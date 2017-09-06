/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package br
package reader

import scala.annotation.switch
import scala.annotation.tailrec

import net.ceedubs.ficus.Ficus._

import org.opalj.log.OPALLogger.info
import org.opalj.br.instructions.Instruction
import org.opalj.br.instructions.GotoInstruction
import org.opalj.br.instructions.GOTO
import org.opalj.br.instructions.GOTO_W
import org.opalj.br.instructions.WIDE
import org.opalj.br.instructions.NOP
import org.opalj.br.instructions.POP
import org.opalj.br.instructions.POP2
import org.opalj.br.instructions.TABLESWITCH
import org.opalj.br.instructions.LOOKUPSWITCH
import org.opalj.br.instructions.IF_ACMPNE
import org.opalj.br.instructions.IF_ACMPEQ
import org.opalj.br.instructions.IFNE
import org.opalj.br.instructions.IFEQ
import org.opalj.br.instructions.IFLT
import org.opalj.br.instructions.IFGT
import org.opalj.br.instructions.IFGE
import org.opalj.br.instructions.IFLE
import org.opalj.br.instructions.IF_ICMPNE
import org.opalj.br.instructions.IF_ICMPEQ
import org.opalj.br.instructions.IF_ICMPLT
import org.opalj.br.instructions.IF_ICMPGT
import org.opalj.br.instructions.IF_ICMPGE
import org.opalj.br.instructions.IF_ICMPLE
import org.opalj.br.instructions.IFNONNULL
import org.opalj.br.instructions.IFNULL
import org.opalj.br.instructions.JSRInstruction
import org.opalj.br.instructions.JSR
import org.opalj.br.instructions.JSR_W

/**
 * Performs some very basic, in-place control-flow simplifications to make the code more regular.
 * In particular to make it more likely that an if which is actually a loop's if actually
 * jumps back.
 *
 * The following transformations are performed:
 *  - trivial gotos which jump to the immediately succeeding instruction are replaced by nops
 *    (the CFG may contain less basic blocks afterwards)
 *  - goto chains are shortened (i.e., a goto jumping to another goto)
 *    (this primarily reduces the number of instructions that need to be evaluated at
 *    abstract interpretation time; it may - however - also reduce the number of basic blocks)
 *  - useless ifs where the jump target is the next instruction are replaced by nops
 *    (the CFG may contain less basic blocks afterwards)
 *  - if -> goto instruction sequences are resolved
 *    (this primarily reduces the number of instructions that need to be evaluated at
 *    abstract interpretation time; it may - however - also reduce the number of basic blocks)
 *  - useless switches are replaced
 *    (the CFG may contain less basic blocks afterwards)
 *
 * The target array has the same size as the source array to make sure that branch offsets/ line-
 * numbers etc. point to the correct instruction. Furthermore, we try to avoid the introduction
 * of dead code.
 *
 * @author Michael Eichberg
 */
trait BytecodeOptimizer extends MethodsBinding {
    this: ClassFileBinding with ConstantPoolBinding with AttributeBinding ⇒

    final val PerformControlFlowSimplifications: Boolean = {
        val key = BytecodeOptimizer.BytecodeOptimizerConfigKeyPrefix+"simplifyControlFlow"
        val simplifyControlFlow: Boolean = config.as[Option[Boolean]](key).getOrElse(true)
        if (simplifyControlFlow) {
            info("class file reader", "the control-flow is simplified")
        } else {
            info("class file reader", "the control-flow is not simplified")
        }
        simplifyControlFlow
    }

    final val LogControlFlowSimplifications: Boolean = {
        val key = BytecodeOptimizer.BytecodeOptimizerConfigKeyPrefix+"logControlFlowSimplification"
        val logControlFlowSimplification: Boolean = config.as[Option[Boolean]](key).getOrElse(false)
        if (logControlFlowSimplification) {
            info("class file reader", "control flow simplifications are logged")
        } else {
            info("class file reader", "control flow simplifications are not logged")
        }
        logControlFlowSimplification
    }

    abstract override def Method_Info(
        cp:               Constant_Pool,
        accessFlags:      Int,
        name_index:       Int,
        descriptor_index: Int,
        attributes:       Attributes
    ): Method_Info = {
        attributes collectFirst { case c: Code ⇒ c } foreach { code ⇒
            val isSimplified = optimizeInstructions(code.exceptionHandlers, code.instructions)
            if (isSimplified && LogControlFlowSimplifications) {
                info(
                    "class file reader",
                    s"simplified control flow of ${cp(name_index).asString}${cp(descriptor_index).asString}"
                )
            }
        }
        super.Method_Info(cp, accessFlags, name_index, descriptor_index, attributes)
    }

    def optimizeInstructions(
        exceptionsHandlers: ExceptionHandlers,
        instructions:       Array[Instruction]
    ): Boolean = {
        var simplified: Boolean = false

        if (!PerformControlFlowSimplifications)
            return simplified;

        // This is the set of instructions which are effectively jumped to by if, switch, goto, and
        // jsr/ret instructions and exception handlers.
        // It contains those instrutions which are definitive jump targets AFTER simplification.
        var jumpTargetInstructions =
            // IMPROVE Use a "real IntTrieSet"...
            exceptionsHandlers.foldLeft(Set.empty[PC]) { (c, eh) ⇒ c + eh.handlerPC }

        var confusedIfs = Set.empty[PC]
        var totallyConfusedIfs = Set.empty[PC]

        /*
         * Given the pc of an instruction the final jump target is determined; that is, if the
         * instruction at currentPC is a goto, that target (by means of the adjusted branchoffset)
         * is returned.
         */
        @tailrec def finalJumpTarget(
            startPC:               PC, // required to detect "while(true){}" loops...
            currentPC:             PC,
            effectiveBranchoffset: Int
        ): Int = {
            instructions(currentPC) match {
                case GotoInstruction(branchoffset) ⇒
                    val nextPC = currentPC + branchoffset
                    if (nextPC != startPC)
                        finalJumpTarget(startPC, nextPC, effectiveBranchoffset + branchoffset)
                    else
                        effectiveBranchoffset

                case _ ⇒
                    effectiveBranchoffset
            }
        }

        val max = instructions.length
        var pc = 0
        while (pc < max) {
            var modifiedByWide = false
            var instruction = instructions(pc)
            if (instruction.opcode == WIDE.opcode) {
                modifiedByWide = true
                pc += 1 // <= WIDE.length
                instruction = instructions(pc)
            }
            val nextPC = instruction.indexOfNextInstruction(pc, modifiedByWide)

            (instruction.opcode: @switch) match {

                case IF_ACMPNE.opcode | IF_ACMPEQ.opcode |
                    IFNE.opcode | IFEQ.opcode |
                    IFLT.opcode | IFGT.opcode | IFGE.opcode | IFLE.opcode |
                    IF_ICMPNE.opcode | IF_ICMPEQ.opcode |
                    IF_ICMPLT.opcode | IF_ICMPGT.opcode | IF_ICMPGE.opcode | IF_ICMPLE.opcode |
                    IFNONNULL.opcode | IFNULL.opcode ⇒
                    val ifInstruction = instruction.asSimpleConditionalBranchInstruction
                    val branchoffset = ifInstruction.branchoffset
                    val jumpTargetPC = pc + branchoffset
                    if (jumpTargetPC == nextPC) {
                        instructions(pc) =
                            if (ifInstruction.numberOfPoppedOperands(NotRequired) == 1)
                                POP
                            else
                                POP2
                        instructions(pc + 1) = NOP
                        instructions(pc + 2) = NOP
                        simplified = true
                        // IMPROVE log the removal of the totally USELESS IF instruction
                    } else {
                        val jumpTargetInstruction = instructions(jumpTargetPC)
                        val nextInstruction = instructions(nextPC)
                        if (nextInstruction.isGotoInstruction && branchoffset == 6) {
                            val nextGotoInstructionBranchoffset =
                                nextInstruction.asGotoInstruction.branchoffset
                            if (nextGotoInstructionBranchoffset == 3) {
                                // we have an if that - even when it falls through – indirectly
                                // just jumps to the overnext instruction; hence, we can
                                // "nop" both instructions right away...
                                instructions(pc + 0) =
                                    if (ifInstruction.numberOfPoppedOperands(NotRequired) == 1)
                                        POP
                                    else
                                        POP2
                                instructions(pc + 1) = NOP
                                instructions(pc + 2) = NOP
                                instructions(pc + 3) = NOP
                                instructions(pc + 4) = NOP
                                instructions(pc + 5) = NOP
                                simplified = true
                                // IMPROVE log the removal of the totally USELESS IF instruction
                            } else if (jumpTargetInstruction.isGotoInstruction) {
                                // TOTALLY CONFUSED IF
                                // A totally confused if, is an if which is directly succeeded by
                                // two goto instructions and which jumps to the second goto and
                                // both goto statements are only reached via the if...
                                // In this case, we want to ensure that we jump back
                                // in case of a jump (if possible...).
                                // E.g., ..,IFLT(6),GOTO(6),GOTO(-201),...
                                totallyConfusedIfs += pc
                            } else {
                                // CONFUSED IF
                                // A "confused if" is an if statement where the next instruction
                                // is a goto instruction that is only reached by the if instruction:
                                //
                                //  i1: if a op b => goto i3
                                //  i2: goto in
                                //  i3: <REST>
                                //
                                // can be replaced by (if the branchoffset is small enough!)
                                //  i1: if !(a op b) => goto in
                                //  i2: NOP
                                //  i3: <REST>
                                confusedIfs += pc
                            }
                        } else if (jumpTargetInstruction.isGotoInstruction) {
                            val nextGoto = jumpTargetInstruction.asGotoInstruction
                            val newBranchoffset = nextGoto.branchoffset + branchoffset
                            if (newBranchoffset >= Short.MinValue && newBranchoffset <= Short.MaxValue) {
                                // Let's use the goto target as the if's target
                                jumpTargetInstructions += pc + newBranchoffset
                                instructions(pc) = ifInstruction.copy(newBranchoffset)
                                simplified = true
                            }
                        }
                    }

                case GOTO.opcode ⇒
                    val GOTO(branchoffset) = instruction
                    val jumpTargetPC = pc + branchoffset
                    if (jumpTargetPC == nextPC) {
                        // let's replace the original jump
                        instructions(pc) = NOP
                        instructions(pc + 1) = NOP
                        instructions(pc + 2) = NOP
                        simplified = true
                    } else {
                        val newBranchoffset = finalJumpTarget(pc, jumpTargetPC, branchoffset)
                        if (newBranchoffset != branchoffset &&
                            newBranchoffset >= Short.MinValue && newBranchoffset <= Short.MaxValue) {
                            // let's replace the original jump
                            jumpTargetInstructions += pc + newBranchoffset
                            instructions(pc) = GOTO(newBranchoffset)
                            simplified = true
                        } else {
                            jumpTargetInstructions += jumpTargetPC
                        }
                    }

                case GOTO_W.opcode ⇒
                    val GOTO_W(branchoffset) = instruction
                    val jumpTargetPC = pc + branchoffset
                    if (jumpTargetPC == nextPC) {
                        // let's replace the original jump
                        instructions(pc) = NOP
                        instructions(pc + 1) = NOP
                        instructions(pc + 2) = NOP
                        instructions(pc + 3) = NOP
                        instructions(pc + 4) = NOP
                        simplified = true
                    } else {
                        val newBranchoffset = finalJumpTarget(pc, jumpTargetPC, branchoffset)
                        if (newBranchoffset != branchoffset) {
                            jumpTargetInstructions += pc + newBranchoffset
                            if (newBranchoffset >= Short.MinValue &&
                                newBranchoffset <= Short.MaxValue) {
                                // Replace it by a short goto
                                instructions(pc + 0) = NOP
                                instructions(pc + 1) = NOP
                                instructions(pc + 2) = GOTO(newBranchoffset)
                                simplified = true
                            } else {
                                // let's replace the original jump
                                instructions(pc) = GOTO_W(newBranchoffset)
                                simplified = true
                            }
                        }
                    }

                case TABLESWITCH.opcode | LOOKUPSWITCH.opcode ⇒
                    val switchInstruction = instruction.asCompoundConditionalBranchInstruction
                    val defaultOffset = switchInstruction.defaultOffset
                    if (switchInstruction.jumpOffsets.forall(_ == defaultOffset)) {
                        var i = pc + 1
                        instructions(pc) = POP
                        var newNextPC = -1
                        val jumpTargetPC = pc + defaultOffset
                        val jumpTargetInstruction = instructions(jumpTargetPC)
                        if (jumpTargetPC == nextPC) {
                            // totally useless..
                            newNextPC = nextPC
                        } else {
                            // This switch is bascially just a goto... we will add
                            // the goto at the end of this section of the bytecode
                            // array; however, the original target remains valid...
                            jumpTargetInstructions += pc + defaultOffset
                            instructions(pc) = POP
                            i = pc + 1
                            if (jumpTargetInstruction.isGotoInstruction) {
                                // actually... a kind of chain of gotos...
                                val nextGoto = jumpTargetInstruction.asGotoInstruction
                                val newBranchoffset =
                                    nextGoto.branchoffset +
                                        // defaultOffset corrected by the relocation of the goto
                                        defaultOffset - ((nextPC - 3) - pc)
                                if (newBranchoffset >= Short.MinValue &&
                                    newBranchoffset <= Short.MaxValue) {
                                    newNextPC = nextPC - 3
                                    instructions(newNextPC) = GOTO(newBranchoffset)
                                    instructions(nextPC - 2) = null
                                    instructions(nextPC - 1) = null
                                } else {
                                    newNextPC = nextPC - 5
                                    instructions(newNextPC) = GOTO_W(newBranchoffset + 2)
                                    instructions(nextPC - 4) = null
                                    instructions(nextPC - 3) = null
                                    instructions(nextPC - 2) = null
                                    instructions(nextPC - 1) = null
                                }
                            } else {
                                // the switch is just a goto, but not a chain of gotos...
                                newNextPC = nextPC - 3
                                instructions(nextPC - 3) = GOTO(defaultOffset - (newNextPC - pc))
                                instructions(nextPC - 2) = null
                                instructions(nextPC - 1) = null
                            }
                        }

                        while (i < newNextPC) { instructions(i) = NOP; i += 1 }
                        simplified = true
                        // IMPROVE log the removal of the USELESS SWITCH instruction
                    } else {
                        // IMPROVE optimize goto chains
                        jumpTargetInstructions += pc + defaultOffset
                        switchInstruction.jumpOffsets foreach { branchoffset ⇒
                            jumpTargetInstructions += pc + branchoffset
                        }
                    }

                case JSR.opcode | JSR_W.opcode ⇒
                    // We do not need to handle the ret instructions... the target is clear
                    // right away... it is the instruction succeeding the jsr instruction.
                    val JSRInstruction(branchoffset) = instruction

                    jumpTargetInstructions += pc + branchoffset
                    jumpTargetInstructions += nextPC

                case _ ⇒ // OK
            }
            pc = nextPC
            modifiedByWide = false
        }

        confusedIfs.filter { cIfPC ⇒ !jumpTargetInstructions.contains(cIfPC + 3) }.foreach { cIfPC ⇒
            // i1: if a op b => goto i3, i2: goto in, i3: <REST>
            // =>
            // i1: if !(a op b) => goto in, i2: NOP, i2+1: NOP, i2+2:NOP, i3: <REST>
            //
            // Recall that the next goto is already rewritten and will point to the final
            // jump target.
            val newBranchoffset = instructions(cIfPC + 3).asGotoInstruction.branchoffset + 3
            if (newBranchoffset >= Short.MinValue && newBranchoffset <= Short.MaxValue) {
                val oldIf = instructions(cIfPC).asSimpleConditionalBranchInstruction
                val newIf = oldIf.negate(newBranchoffset)
                instructions(cIfPC) = newIf
                instructions(cIfPC + 3) = NOP
                instructions(cIfPC + 4) = NOP
                instructions(cIfPC + 5) = NOP
                simplified = true
            }
        }

        totallyConfusedIfs.filter { cIfPC ⇒
            !jumpTargetInstructions.contains(cIfPC + 3) &&
                !jumpTargetInstructions.contains(cIfPC + 6)
        }.foreach { cIfPC ⇒
            // EXAMPLE:
            //
            // The sequence
            // (SPECIAL, BUT FREQUENT, CASE.. the second GOTO just jumps over the next goto..):
            //      pc=182: IF_ACMPNE(6), pc+3: GOTO(6), pc+6: GOTO(-170)
            // is rewritten to:
            //      pc=182: IF_ACMPNE(6 + (-170) ), pc+[3..9]: NOP
            //
            // AND (GENERAL CASE I - pc of the second target is smaller than the first target's pc)
            //      pc=347: IF_ICMPNE(6),GOTO(79),GOTO(-179)
            // =>   pc=347: IF_ICMPEQ(6-179), NOP*3, GOTO(79-3)
            //
            // AND (GENERAL CASE II - pc of the first target is smaller than the second target's pc)
            //      pc=347: IF_ICMPNE(6),GOTO(-79),GOTO(179)
            // =>   pc=347: IF_ICMPEQ(3-79), NOP*3, GOTO(179)

            val ifInstruction = instructions(cIfPC).asSimpleConditionalBranchInstruction
            val firstGotoInstruction = instructions(cIfPC + 3).asGotoInstruction
            val secondGotoInstruction = instructions(cIfPC + 6).asGotoInstruction

            if (firstGotoInstruction.branchoffset == 6) {
                val newBranchoffset = secondGotoInstruction.branchoffset + 6
                if (newBranchoffset >= Short.MinValue && newBranchoffset <= Short.MaxValue) {
                    instructions(cIfPC) = ifInstruction.copy(newBranchoffset)
                    instructions(cIfPC + 3) = NOP
                    instructions(cIfPC + 4) = NOP
                    instructions(cIfPC + 5) = NOP
                    instructions(cIfPC + 6) = NOP
                    instructions(cIfPC + 7) = NOP
                    instructions(cIfPC + 8) = NOP
                    simplified = true
                }
            } else {
                // 1. determine which jump target has a lower pc
                val firstAdjustedBranchoffset = firstGotoInstruction.branchoffset + 3
                val secondAdjustedBranchoffset = secondGotoInstruction.branchoffset + 6
                if (firstAdjustedBranchoffset < secondAdjustedBranchoffset) {
                    // ... CASE II
                    val newBranchoffset = firstAdjustedBranchoffset
                    if (newBranchoffset >= Short.MinValue && newBranchoffset <= Short.MaxValue) {
                        instructions(cIfPC) = ifInstruction.negate(firstAdjustedBranchoffset)
                        instructions(cIfPC + 3) = NOP
                        instructions(cIfPC + 4) = NOP
                        instructions(cIfPC + 5) = NOP
                        simplified = true
                    }

                } else {
                    // ... CASE I
                    val newIfBranchoffset = secondAdjustedBranchoffset
                    val newGotoBranchoffset = firstAdjustedBranchoffset - 6
                    if (newIfBranchoffset >= Short.MinValue &&
                        newIfBranchoffset <= Short.MaxValue &&
                        newGotoBranchoffset >= Short.MinValue &&
                        newGotoBranchoffset <= Short.MaxValue) {
                        instructions(cIfPC) = ifInstruction.negate(firstAdjustedBranchoffset)
                        instructions(cIfPC + 3) = NOP
                        instructions(cIfPC + 4) = NOP
                        instructions(cIfPC + 5) = NOP
                        instructions(cIfPC + 6) = GOTO(newGotoBranchoffset)
                        simplified = true
                    }
                }
            }
        }

        simplified
    }

}

object BytecodeOptimizer {

    final val BytecodeOptimizerConfigKeyPrefix = {
        ClassFileReaderConfiguration.ConfigKeyPrefix+"BytecodeOptimizer."
    }

}
