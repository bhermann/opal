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

import scala.annotation.tailrec

import java.util.Arrays.fill

import scala.collection.BitSet
import scala.collection.immutable.IntMap
import scala.collection.mutable
import scala.collection.generic.FilterMonadic
import scala.collection.generic.CanBuildFrom

import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught
import org.opalj.collection.mutable.UShortSet
import org.opalj.br.instructions._
import org.opalj.br.cfg.CFGFactory

/**
 * Representation of a method's code attribute, that is, representation of a method's
 * implementation.
 *
 * @param   maxStack The maximum size of the stack during the execution of the method.
 *          This value is determined by the compiler and is not necessarily the minimum.
 *          However, in the vast majority of cases it is the minimum.
 * @param   maxLocals The number of registers/local variables needed to execute the method.
 *          As in case of `maxStack` this number is expected to be the minimum, but this is
 *          not guaranteed.
 * @param   instructions The instructions of this `Code` array/`Code` block. Since the code
 *          array is not completely filled (it contains `null` values) the preferred way
 *          to iterate over all instructions is to use for-comprehensions and pattern
 *          matching or to use one of the predefined methods [[foreach]], [[collect]],
 *          [[collectPair]], [[collectWithIndex]], etc..
 *          The `instructions` array must not be mutated!
 *
 * @author Michael Eichberg
 */
final class Code private (
    val maxStack:          Int,
    val maxLocals:         Int,
    val instructions:      Array[Instruction],
    val exceptionHandlers: ExceptionHandlers,
    val attributes:        Attributes
) extends Attribute
        with CommonAttributes
        with InstructionsContainer
        with FilterMonadic[(PC, Instruction), Nothing] { code ⇒

    class FilteredCode( final val p: ((PC, Instruction)) ⇒ Boolean)
            extends FilterMonadic[(PC, Instruction), Nothing] {

        def map[B, That](
            f: ((PC, Instruction)) ⇒ B
        )(
            implicit
            bf: CanBuildFrom[Nothing, B, That]
        ): That = {
            val that = bf()
            code foreach { (pcInstruction: (PC, Instruction)) ⇒
                if (p(pcInstruction)) that += f(pcInstruction)
            }
            that.result
        }

        def flatMap[B, That](
            f: ((PC, Instruction)) ⇒ scala.collection.GenTraversableOnce[B]
        )(
            implicit
            bf: CanBuildFrom[Nothing, B, That]
        ): That = {
            val that = bf()
            code foreach { (pcInstruction: (PC, Instruction)) ⇒
                if (p(pcInstruction)) that ++= f(pcInstruction).seq
            }
            that.result
        }

        def foreach[U](f: ((PC, Instruction)) ⇒ U): Unit = {
            code.foreach { (pcInstruction: (PC, Instruction)) ⇒
                if (p(pcInstruction)) f((pcInstruction))
            }
        }

        def withFilter(p: ((PC, Instruction)) ⇒ Boolean): FilterMonadic[(PC, Instruction), Nothing] =
            new FilteredCode(
                (pcInstruction: (PC, Instruction)) ⇒ this.p(pcInstruction) && p((pcInstruction))
            )
    }

    def map[B, That](f: ((PC, Instruction)) ⇒ B)(implicit bf: CanBuildFrom[Nothing, B, That]): That = {
        val that = bf()
        code.foreach(that += f(_))
        that.result
    }

    def flatMap[B, That](
        f: ((PC, Instruction)) ⇒ scala.collection.GenTraversableOnce[B]
    )(
        implicit
        bf: CanBuildFrom[Nothing, B, That]
    ): That = {
        val that = bf()
        code.foreach(pcInstruction ⇒ that ++= f(pcInstruction).seq)
        that.result
    }

    def withFilter(p: ((PC, Instruction)) ⇒ Boolean): FilterMonadic[(PC, Instruction), Nothing] = {
        new FilteredCode(p)
    }

    override def instructionsOption: Some[Array[Instruction]] = Some(instructions)

    /**
     * Returns an iterator to iterate over the program counters of the instructions
     * of this `Code` block.
     *
     * @see See the method [[foreach]] for an alternative.
     */
    def programCounters: Iterator[PC] =
        new Iterator[PC] {
            var pc = 0 // there is always at least one instruction

            def next() = {
                val next = pc
                pc = pcOfNextInstruction(pc)
                next
            }

            def hasNext = pc < instructions.size
        }

    /**
     * Counts the number of instructions. This operation has complexity O(n).
     *
     * The number of instructions is always smaller or equal to the size of the code
     * array.
     *
     * @note The result is not cached and recalculated on-demand.
     */
    def instructionsCount: Int = {
        var c = 0
        var pc = 0
        val max = instructions.size
        while (pc < max) {
            c += 1
            pc = pcOfNextInstruction(pc)
        }
        c
    }

    /**
     * Calculates for each instruction to which subroutine the respective instruction
     * belongs to – if any. This information is required to, e.g., identify the subroutine
     * contexts that need to be reset in case of an exception in a subroutine.
     *
     * @note Calling this method only makes sense for Java bytecode that actually contains
     *         [[org.opalj.br.instructions.JSR]] and [[org.opalj.br.instructions.RET]]
     *         instructions.
     *
     * @return Basically a map that maps the `pc` of each instruction to the id of the
     *      subroutine.
     *      For each instruction (with a specific `pc`) the `pc` of the first instruction
     *      of the subroutine it belongs to is returned. The pc 0 identifies the instruction
     *      as belonging to the core method. The pc -1 identifies the instruction as
     *      dead by compilation.
     */
    def belongsToSubroutine(): Array[Int] = {
        val subroutineIds = new Array[Int](instructions.length)
        fill(subroutineIds, -1) // <= initially all instructions belong to "no routine"

        val nextSubroutines = mutable.Queue[PC](0)

        def propagate(subroutineId: Int, subroutinePC: PC): Unit = {

            val nextPCs = mutable.Queue[PC](subroutinePC)
            while (nextPCs.nonEmpty) {
                val pc = nextPCs.dequeue
                if (subroutineIds(pc) == -1) {
                    subroutineIds(pc) = subroutineId
                    val instruction = instructions(pc)

                    (instruction.opcode: @scala.annotation.switch) match {
                        case ATHROW.opcode                                    ⇒
                        /*Nothing do to. (Will be handled when we deal with exceptions)*/

                        case /* xReturn: */ 176 | 175 | 174 | 172 | 173 | 177 ⇒
                        /*Nothing to do. (no successor!)*/

                        case RET.opcode                                       ⇒
                        /*Nothing to do; handled by JSR*/
                        case JSR.opcode | JSR_W.opcode ⇒
                            val jsrInstr = instruction.asInstanceOf[JSRInstruction]
                            nextSubroutines.enqueue(pc + jsrInstr.branchoffset)
                            nextPCs.enqueue(pcOfNextInstruction(pc))

                        case GOTO.opcode | GOTO_W.opcode ⇒
                            val bInstr = instruction.asInstanceOf[UnconditionalBranchInstruction]
                            nextPCs.enqueue(pc + bInstr.branchoffset)

                        case /*IFs:*/ 165 | 166 | 198 | 199 |
                            159 | 160 | 161 | 162 | 163 | 164 |
                            153 | 154 | 155 | 156 | 157 | 158 ⇒
                            val bInstr = instruction.asInstanceOf[SimpleConditionalBranchInstruction]
                            nextPCs.enqueue(pc + bInstr.branchoffset)
                            nextPCs.enqueue(pcOfNextInstruction(pc))

                        case TABLESWITCH.opcode | LOOKUPSWITCH.opcode ⇒
                            val sInstr = instruction.asInstanceOf[CompoundConditionalBranchInstruction]
                            nextPCs.enqueue(pc + sInstr.defaultOffset)
                            sInstr.jumpOffsets foreach { jumpOffset ⇒
                                nextPCs.enqueue(pc + jumpOffset)
                            }

                        case _ ⇒
                            nextPCs.enqueue(pcOfNextInstruction(pc))
                    }
                }
            }
        }

        var remainingExceptionHandlers = exceptionHandlers

        while (nextSubroutines.nonEmpty) {
            val subroutineId = nextSubroutines.dequeue()
            propagate(subroutineId, subroutineId)

            // all handlers that handle exceptions related to one of the instructions
            // belonging to this subroutine belong to this subroutine (unless the handler
            // is already associated with a previous subroutine!)
            @inline def belongsToCurrentSubroutine(
                startPC:   PC,
                endPC:     PC,
                handlerPC: PC
            ): Boolean = {
                var currentPC = startPC
                while (currentPC < endPC) {
                    if (subroutineIds(currentPC) != -1) {
                        propagate(subroutineId, handlerPC)
                        // we are done
                        return true;
                    } else {
                        currentPC = pcOfNextInstruction(currentPC)
                    }
                }
                false
            }

            remainingExceptionHandlers =
                for {
                    eh @ ExceptionHandler(startPC, endPC, handlerPC, _) ← remainingExceptionHandlers
                    if subroutineIds(handlerPC) == -1 // we did not already analyze the handler
                    if !belongsToCurrentSubroutine(startPC, endPC, handlerPC)
                } yield {
                    eh
                }
        }

        subroutineIds
    }

    /**
     * Returns the set of all program counters where two or more control flow
     * paths may join.
     *
     * ==Example==
     * {{{
     *     0: iload_1
     *     1: ifgt    6
     *     2: iconst_1
     *     5: goto 10
     *     6: ...
     *     9: iload_1
     *  10:return // <= PATH JOIN: the predecessors are the instructions 5 and 9.
     * }}}
     *
     * In case of exception handlers the sound overapproximation is made that
     * all exception handlers may be reached on multiple paths.
     */
    def cfJoins: BitSet = {
        val instructions = this.instructions
        val instructionsLength = instructions.length
        val cfJoins = new mutable.BitSet(instructionsLength)
        exceptionHandlers.foreach { eh ⇒
            // [REFINE] For non-finally handlers, test if multiple paths
            // can lead to the respective exception
            cfJoins += eh.handlerPC
        }
        // The algorithm determines for each instruction the successor instruction
        // that is reached and then marks it. If an instruction was already reached in the
        // past, it will then mark the instruction as a "join" instruction.
        val isReached = new mutable.BitSet(instructionsLength)
        isReached += 0 // the first instruction is always reached!
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            val nextPC = pcOfNextInstruction(pc)
            @inline def runtimeSuccessor(pc: PC): Unit = {
                if (isReached.contains(pc))
                    cfJoins += pc
                else
                    isReached += pc
            }
            (instruction.opcode: @scala.annotation.switch) match {
                case ATHROW.opcode ⇒ /*already handled*/

                case RET.opcode    ⇒ /*Nothing to do; handled by JSR*/
                case JSR.opcode | JSR_W.opcode ⇒
                    val jsrInstr = instruction.asInstanceOf[JSRInstruction]
                    runtimeSuccessor(pc + jsrInstr.branchoffset)
                    runtimeSuccessor(nextPC)

                case GOTO.opcode | GOTO_W.opcode ⇒
                    val bInstr = instruction.asInstanceOf[UnconditionalBranchInstruction]
                    runtimeSuccessor(pc + bInstr.branchoffset)

                case /*IFs:*/ 165 | 166 | 198 | 199 |
                    159 | 160 | 161 | 162 | 163 | 164 |
                    153 | 154 | 155 | 156 | 157 | 158 ⇒
                    val bInstr = instruction.asInstanceOf[SimpleConditionalBranchInstruction]
                    val jumpTargetPC = pc + bInstr.branchoffset
                    if (jumpTargetPC != nextPC) {
                        // we have an "if" that always immediately continues with the next
                        // instruction; hence, this "if" is useless
                        runtimeSuccessor(jumpTargetPC)
                    }
                    runtimeSuccessor(nextPC)

                case TABLESWITCH.opcode | LOOKUPSWITCH.opcode ⇒
                    instruction.nextInstructions(pc)(code, null /*not required!*/ ) foreach { pc ⇒
                        runtimeSuccessor(pc)
                    }

                case /*xReturn:*/ 176 | 175 | 174 | 172 | 173 | 177 ⇒
                /*Nothing to do. (no successor!)*/

                case _ ⇒
                    runtimeSuccessor(nextPC)
            }
            pc = nextPC
        }
        cfJoins
    /**
     * @return  An array which contains for each instruction the set of all predecessors as well
     *          as the set of all instructions which have only predecessors/no successors.
     */
    def predecessorPCs(implicit classHierarchy: ClassHierarchy): (Array[PCs], PCs) = {
        val instructions = this.instructions
        val max = instructions.length
        val allPredecessorPCs = new Array[PCs](max)
        var exitPCs = UShortSet.empty
        allPredecessorPCs(0) = UShortSet.empty
        var pc = 0
        while (pc < max) {
            val i = instructions(pc)
            val nextPCs = i.nextInstructions(pc, false)(this, classHierarchy)
            if (nextPCs.isEmpty) {
                exitPCs += pc
            } else {
                nextPCs foreach { nextPC ⇒
                    val predecessorPCs = allPredecessorPCs(nextPC)
                    if (predecessorPCs eq null) {
                        allPredecessorPCs(nextPC) = UShortSet(pc)
                    } else {
                        allPredecessorPCs(nextPC) = predecessorPCs + pc
                    }
                }
            }
            pc = i.indexOfNextInstruction(pc)(this)
        }
        (allPredecessorPCs, exitPCs)
    }
    }

    /**
     * Returns the set of all program counters where two or more control flow
     * paths join of fork.
     *
     * ==Example==
     * {{{
     *     0: iload_1
     *     1: ifgt    6 // <= PATH FORK
     *     2: iconst_1
     *     5: goto 10
     *     6: ...
     *     9: iload_1
     *  10:return // <= PATH JOIN: the predecessors are the instructions 5 and 9.
     * }}}
     *
     * In case of exception handlers the sound overapproximation is made that
     * all exception handlers may be reached on multiple paths.
     *
     * @return A triple which contains (1) the set of pcs of those instruction where multiple
     *         control-flow paths join; (2) the pcs of the instructions which may result in
     *         multiple different control-flow paths and (3) for each of the later instructions
     *         the set of all potential targets.
     */
    def cfPCs(
        implicit
        classHierarchy: ClassHierarchy = Code.preDefinedClassHierarchy
    ): (BitSet /*joins*/ , BitSet /*forks*/ , IntMap[UShortSet] /*forkTargetPCs*/ ) = {
        val instructions = this.instructions
        val instructionsLength = instructions.length

        val cfJoins = new mutable.BitSet(instructionsLength)
        val cfForks = new mutable.BitSet(instructionsLength)
        var cfForkTargets = IntMap.empty[UShortSet]

        val isReached = new mutable.BitSet(instructionsLength)
        isReached += 0 // the first instruction is always reached!

        lazy val cfg = CFGFactory(this, classHierarchy)

        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            val nextPC = pcOfNextInstruction(pc)

            @inline def runtimeSuccessor(pc: PC): Unit = {
                if (isReached.contains(pc))
                    cfJoins += pc
                else
                    isReached += pc
            }

            (instruction.opcode: @scala.annotation.switch) match {
                case RET.opcode ⇒
                    // The ret may return to different sites;
                    // the potential path joins are determined when we process the JSR.
                    cfForks += pc
                    cfForkTargets += ((pc, UShortSet.create(cfg.successors(pc))))

                case JSR.opcode | JSR_W.opcode ⇒
                    val jsrInstr = instruction.asInstanceOf[JSRInstruction]
                    runtimeSuccessor(pc + jsrInstr.branchoffset)
                    runtimeSuccessor(nextPC)

                case _ ⇒
                    val nextInstructions = instruction.nextInstructions(pc)(this, classHierarchy)
                    nextInstructions.foreach(runtimeSuccessor)
                    if (nextInstructions.hasMultipleElements) {
                        cfForks += pc
                        cfForkTargets += ((pc, UShortSet.create(nextInstructions)))
                    }
            }

            pc = nextPC
        }
        (cfJoins, cfForks, cfForkTargets)
    }

    /**
     * Iterates over all instructions and calls the given function `f`
     * for every instruction.
     */
    @inline final def iterate[U](f: (PC, Instruction) ⇒ U): Unit = {
        val instructionsLength = instructions.length
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            f(pc, instruction)
            pc = pcOfNextInstruction(pc)
        }
    }

    @inline final def foreach[U](f: ((PC, Instruction)) ⇒ U): Unit = {
        val instructionsLength = instructions.length
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            f((pc, instruction))
            pc = pcOfNextInstruction(pc)
        }
    }

    @inline final def forall(f: (PC, Instruction) ⇒ Boolean): Boolean = {
        val instructionsLength = instructions.length
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            if (!f(pc, instruction))
                return false;

            pc = pcOfNextInstruction(pc)
        }
        true
    }

    /**
     * Iterates over all instructions and calls the given function `f`
     * for every instruction.
     */
    @inline final def foreachInstruction[U](f: Instruction ⇒ U): Unit = {
        val instructionsLength = instructions.length
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            f(instruction)
            pc = pcOfNextInstruction(pc)
        }
    }

    /**
     * Iterates over all instructions until an instruction is found that matches
     * the given predicate.
     */
    @inline final def exists(p: (PC, Instruction) ⇒ Boolean): Boolean = {
        val instructionsLength = instructions.length
        var pc = 0
        while (pc < instructionsLength) {
            val instruction = instructions(pc)
            if (p(pc, instruction))
                return true;

            pc = pcOfNextInstruction(pc)
        }
        false
    }

    /**
     * Returns a view of all handlers (exception and finally handlers) for the
     * instruction with the given program counter (`pc`) that may catch an exception.
     *
     * In case of multiple exception handlers that are identical (in particular
     * in case of the finally handlers) only the first one is returned as that
     * one is the one that will be used by the JVM at runtime.
     * In case of identical caught exceptions only the
     * first of them will be returned. No further checks (w.r.t. the type hierarchy) are done.
     *
     * @param pc The program counter of an instruction of this `Code` array.
     */
    def handlersFor(pc: PC, justExceptions: Boolean = false): Chain[ExceptionHandler] = {
        var handledExceptions = Set.empty[ObjectType]
        val ehs = Chain.newBuilder[ExceptionHandler]
        exceptionHandlers forall { eh ⇒
            if (eh.startPC <= pc && eh.endPC > pc) {
                val catchTypeOption = eh.catchType
                if (catchTypeOption.isDefined) {
                    val catchType = catchTypeOption.get
                    if (!handledExceptions.contains(catchType)) {
                        handledExceptions += catchType
                        ehs += eh
                    }
                    true
                } else {
                    if (!justExceptions) {
                        ehs += eh
                    }
                    false
                }

            } else {
                // the handler is not relevant
                true
            }
        }
        ehs.result()
    }

    /**
     * Returns a view of all potential exception handlers (if any) for the
     * instruction with the given program counter (`pc`). `Finally` handlers
     * (`catchType == None`) are not returned but will stop the evaluation (as all further
     * exception handlers have no further meaning w.r.t. the runtime)!
     * In case of identical caught exceptions only the
     * first of them will be returned. No further checks (w.r.t. the typehierarchy) are done.
     *
     * @param pc The program counter of an instruction of this `Code` array.
     */
    def exceptionHandlersFor(pc: PC): Chain[ExceptionHandler] = {
        handlersFor(pc, justExceptions = true)
    }

    /**
     * Returns the handlers that may handle the given exception.
     *
     * The (known/given) type hierarchy is taken into account as well as
     * the order between the exception handlers.
     */
    def handlersForException(
        pc:        PC,
        exception: ObjectType
    )(
        implicit
        classHierarchy: ClassHierarchy = Code.preDefinedClassHierarchy
    ): Chain[ExceptionHandler] = {
        import classHierarchy.isSubtypeOf

        var handledExceptions = Set.empty[ObjectType]

        val ehs = Chain.newBuilder[ExceptionHandler]
        exceptionHandlers forall { eh ⇒
            if (eh.startPC <= pc && eh.endPC > pc) {
                val catchTypeOption = eh.catchType
                if (catchTypeOption.isDefined) {
                    val catchType = catchTypeOption.get
                    val isSubtype = isSubtypeOf(exception, catchType)
                    if (isSubtype.isYes) {
                        ehs += eh
                        /* we found a definitiv matching handler*/ false
                    } else if (isSubtype.isUnknown) {
                        if (!handledExceptions.contains(catchType)) {
                            handledExceptions += catchType
                            ehs += eh
                        }
                        /* we may have a better fit */ true
                    } else {
                        /* the exception type is not relevant*/ true
                    }
                } else {
                    ehs += eh
                    /* we are done; we found a finally handler... */ false
                }
            } else {
                /* the handler is not relevant */ true
            }
        }
        ehs.result()
    }

    /**
     * The list of pcs of those instructions that may handle an exception if the evaluation
     * of the instruction with the given `pc` throws an exception.
     *
     * In case of multiple finally handlers only the first one will be returned and no further
     * exception handlers will be returned. In case of identical caught exceptions only the
     * first of them will be returned. No further checks (w.r.t. the type hierarchy) are done.
     *
     * If different exceptions are handled by the same handler, the corresponding pc is returned
     * multiple times.
     */
    def handlerInstructionsFor(pc: PC): Chain[PC] = {
        var handledExceptions = Set.empty[ObjectType]

        val pcs = Chain.newBuilder[PC]
        exceptionHandlers forall { eh ⇒
            if (eh.startPC <= pc && eh.endPC > pc) {
                val catchTypeOption = eh.catchType
                if (catchTypeOption.isDefined) {
                    val catchType = catchTypeOption.get
                    if (!handledExceptions.contains(catchType)) {
                        handledExceptions += catchType
                        pcs += eh.handlerPC
                    }
                    true
                } else {
                    pcs += eh.handlerPC
                    false // we effectively abort after the first finally handler
                }
            } else {
                // the handler is not relevant
                true
            }
        }
        pcs.result()
    }

    /**
     * Returns the program counter of the next instruction after the instruction with
     * the given counter (`currentPC`).
     *
     * @param currentPC The program counter of an instruction. If `currentPC` is the
     *      program counter of the last instruction of the code block then the returned
     *      program counter will be equivalent to the length of the Code/Instructions
     *      array.
     */
    @inline final def pcOfNextInstruction(currentPC: PC): PC = {
        instructions(currentPC).indexOfNextInstruction(currentPC)(this)
        // OLD: ITERATING OVER THE ARRAY AND CHECKING FOR NON-NULL IS NO LONGER SUPPORTED!
        //    @inline final def pcOfNextInstruction(currentPC: PC): PC = {
        //        val max_pc = instructions.size
        //        var nextPC = currentPC + 1
        //        while (nextPC < max_pc && (instructions(nextPC) eq null))
        //            nextPC += 1
        //
        //        nextPC
        //    }
    }

    /**
     * Returns the program counter of the previous instruction in the code array.
     * `currentPC` must be the program counter of an instruction.
     *
     * This function is only defined if currentPC is larger than 0; i.e., if there
     * is a previous instruction! If currentPC is larger than `instructions.size` the
     * behavior is undefined.
     */
    @inline final def pcOfPreviousInstruction(currentPC: PC): PC = {
        var previousPC = currentPC - 1
        val instructions = this.instructions
        while (previousPC > 0 && !instructions(previousPC).isInstanceOf[Instruction]) {
            previousPC -= 1
        }
        previousPC
    }

    /**
     * Returns the line number table - if any.
     *
     * @note    A code attribute is allowed to have multiple line number tables. However, all
     *          tables are merged into one by OPAL at class loading time.
     *
     * @note    Depending on the configuration of the reader for `ClassFile`s this
     *          attribute may not be reified.
     */
    def lineNumberTable: Option[LineNumberTable] = {
        attributes collectFirst { case lnt: LineNumberTable ⇒ lnt }
    }

    /**
     * Returns the line number associated with the instruction with the given pc if
     * it is available.
     *
     * @param pc Index of the instruction for which we want to get the line number.
     * @return `Some` line number or `None` if no line-number information is available.
     */
    def lineNumber(pc: PC): Option[Int] = {
        lineNumberTable.flatMap(_.lookupLineNumber(pc))
    }

    /**
     * Returns `Some(true)` if both pcs have the same line number. If line number information
     * is not available `None` is returned.
     */
    def haveSameLineNumber(firstPC: PC, secondPC: PC): Option[Boolean] = {
        lineNumber(firstPC).flatMap(firstLN ⇒ lineNumber(secondPC).map(_ == firstLN))
    }

    /**
     * Returns the smallest line number (if any).
     *
     * @note The line number associated with the first instruction (pc === 0) is
     *      not necessarily the smallest one.
     *      {{{
     *      public void foo(int i) {
     *          super.foo( // The call has the smallest line number.
     *              i+=1; // THIS IS THE FIRST OPERATION...
     *          )
     *      }
     *      }}}
     */
    def firstLineNumber: Option[Int] = lineNumberTable.flatMap(_.firstLineNumber)

    /**
     * Collects all local variable tables.
     *
     * @note A code attribute is allowed to have multiple local variable tables. However, all
     *      tables are merged into one by OPAL at class loading time.
     *
     * @note Depending on the configuration of the reader for `ClassFile`s this
     *         attribute may not be reified.
     */
    def localVariableTable: Option[LocalVariables] = {
        attributes collectFirst { case LocalVariableTable(lvt) ⇒ lvt }
    }

    /**
     * Returns the set of local variables defined at the given pc.
     *
     * @return A mapping of the index to the name of the local variable. The map is
     *      empty if no debug information is available.
     */
    def localVariablesAt(pc: PC): Map[Int, LocalVariable] = {
        localVariableTable match {
            case Some(lvt) ⇒
                (lvt.collect {
                    case lv @ LocalVariable(
                        startPC,
                        length,
                        name,
                        fieldType,
                        index
                        ) if startPC <= pc && startPC + length > pc ⇒
                        (index, lv)
                }).toMap
            case _ ⇒
                Map.empty
        }
    }

    /**
     * Returns the local variable stored at the given local variable index that is live at
     * the given instruction (pc).
     */
    def localVariable(pc: PC, index: Int): Option[LocalVariable] = {
        localVariableTable.flatMap { lvs ⇒
            lvs.find { lv ⇒
                val result = lv.index == index &&
                    lv.startPC <= pc &&
                    (lv.startPC + lv.length) > pc
                result
            }
        }
    }

    /**
     * Collects all local variable type tables.
     *
     * @note Depending on the configuration of the reader for `ClassFile`s this
     *         attribute may not be reified.
     */
    def localVariableTypeTable: Seq[LocalVariableTypes] = {
        attributes collect { case LocalVariableTypeTable(lvtt) ⇒ lvtt }
    }

    /**
     * Collects all local variable type tables.
     *
     * @note Depending on the configuration of the reader for `ClassFile`s this
     *      attribute may not be reified.
     */
    def runtimeVisibleType: Seq[LocalVariableTypes] = {
        attributes collect { case LocalVariableTypeTable(lvtt) ⇒ lvtt }
    }

    /**
     * The JVM specification mandates that a Code attribute has at most one
     * StackMapTable attribute.
     *
     * @note Depending on the configuration of the reader for `ClassFile`s this
     *         attribute may not be reified.
     */
    def stackMapTable: Option[StackMapFrames] = {
        attributes collectFirst { case StackMapTable(smf) ⇒ smf }
    }

    /**
     * True if the instruction with the given program counter is modified by wide.
     *
     * @param pc A valid index in the code array.
     */
    @inline def isModifiedByWide(pc: PC): Boolean = pc > 0 && instructions(pc - 1) == WIDE

    /**
     * Collects all instructions for which the given function is defined.
     *
     * ==Usage scenario==
     * Use this function if you want to search for and collect specific instructions and
     * when you do not immediately require the program counter/index of the instruction
     * in the instruction array to make the decision whether you want to collect the
     * instruction.
     *
     * ==Examples==
     * Example usage to collect the declaring class of all get field accesses where the
     * field name is "last".
     * {{{
     * collect({
     *  case GETFIELD(declaringClass, "last", _) ⇒ declaringClass
     * })
     * }}}
     *
     * Example usage to collect all instances of a "DUP" instruction.
     * {{{
     * code.collect({ case dup @ DUP ⇒ dup })
     * }}}
     *
     * @return The result of applying the function f to all instructions for which f is
     *      defined combined with the index (program counter) of the instruction in the
     *      code array.
     */
    def collect[B](f: PartialFunction[Instruction, B]): Seq[(PC, B)] = {
        val max_pc = instructions.size
        var pc = 0
        var result: List[(PC, B)] = List.empty
        while (pc < max_pc) {
            val instruction = instructions(pc)
            if (f.isDefinedAt(instruction)) {
                result = (pc, f(instruction)) :: result
            }
            pc = pcOfNextInstruction(pc)
        }
        result.reverse
    }

    /**
     * Collects the results of the evaluation of the partial function until the partial function
     * is not defined.
     *
     * @return The program counter of the instruction for which the given partial function was
     *         not defined along with the list of previous results. '''The results are sorted in
     *         descending order w.r.t. the PC'''.
     */
    def collectUntil[B](f: PartialFunction[(PC, Instruction), B]): (PC, Seq[B]) = {
        val max_pc = instructions.size
        var pc = 0
        var result: List[B] = List.empty
        while (pc < max_pc) {
            val instruction = instructions(pc)
            val value = (pc, instruction)
            if (f.isDefinedAt(value)) {
                result = f(value) :: result
            } else {
                return (pc, result);
            }
            pc = pcOfNextInstruction(pc)
        }
        (pc, result)
    }

    /**
     * Collects all instructions for which the given function is defined. The order in
     * which the instructions are collected is reversed when compared to the order in the
     * instructions array.
     */
    def collectInstructions[B](f: PartialFunction[Instruction, B]): Seq[B] = {
        val max_pc = instructions.size
        var result: List[B] = List.empty
        var pc = 0
        while (pc < max_pc) {
            val instruction = instructions(pc)
            if (f.isDefinedAt(instruction)) {
                result = f(instruction) :: result
            }
            pc = pcOfNextInstruction(pc)
        }
        result
    }

    /**
     * Applies the given function `f` to all instruction objects for which the function is
     * defined. The function is passed a tuple consisting of the current program
     * counter/index in the code array and the corresponding instruction.
     *
     * ==Example==
     * Example usage to collect the program counters (indexes) of all instructions that
     * are the target of a conditional branch instruction:
     * {{{
     * code.collectWithIndex({
     *  case (pc, cbi: ConditionalBranchInstruction) ⇒
     *      Seq(cbi.indexOfNextInstruction(pc, code), pc + cbi.branchoffset)
     *  }) // .flatten should equal (Seq(...))
     * }}}
     */
    def collectWithIndex[B](f: PartialFunction[(PC, Instruction), B]): Seq[B] = {
        val max_pc = instructions.size
        var pc = 0
        var result: List[B] = List.empty
        while (pc < max_pc) {
            val params = (pc, instructions(pc))
            if (f.isDefinedAt(params)) {
                result = f(params) :: result
            }
            pc = pcOfNextInstruction(pc)
        }
        result.reverse
    }

    /**
     * Applies the given function to the first instruction for which the given function
     * is defined.
     */
    def collectFirstWithIndex[B](f: PartialFunction[(PC, Instruction), B]): Option[B] = {
        val max_pc = instructions.size
        var pc = 0
        while (pc < max_pc) {
            val params = (pc, instructions(pc))
            if (f.isDefinedAt(params))
                return Some(f(params));

            pc = pcOfNextInstruction(pc)
        }

        None
    }

    /**
     * Tests if an instruction matches the given filter. If so, the index of the first
     * matching instruction is returned.
     */
    def find(f: Instruction ⇒ Boolean): Option[PC] = {
        val max_pc = instructions.size
        var pc = 0
        while (pc < max_pc) {
            if (f(instructions(pc)))
                return Some(pc);

            pc = pcOfNextInstruction(pc)
        }

        None
    }

    /**
     * Returns a new sequence that pairs the program counter of an instruction with the
     * instruction.
     */
    def associateWithIndex(): Seq[(PC, Instruction)] = collect { case i ⇒ i }

    /**
     * Slides over the code array and tries to apply the given function to each sequence
     * of instructions consisting of `windowSize` elements.
     *
     * ==Scenario==
     * If you want to search for specific patterns of bytecode instructions. Some "bug
     * patterns" are directly related to specific bytecode sequences and these patterns
     * can easily be identified using this method.
     *
     * ==Example==
     * Search for sequences of the bytecode instructions `PUTFIELD` and `ALOAD_O` in the
     * method's body and return the list of program counters of the start of the
     * identified sequences.
     * {{{
     * code.slidingCollect(2)({
     *  case (pc, Seq(PUTFIELD(_, _, _), ALOAD_0)) ⇒ (pc)
     * }) should be(Seq(...))
     * }}}
     *
     * @note If possible, use one of the more specialized methods, such as, [[collectPair]].
     *      The pure iteration overhead caused by this method is roughly 10-20 times higher
     *      than this one.
     *
     * @param windowSize The size of the sequence of instructions that is passed to the
     *      partial function.
     *      It must be larger than 0. **Do not use this method with windowSize "1"**;
     *      it is more efficient to use the `collect` or `collectWithIndex` methods
     *      instead.
     *
     * @return The list of results of applying the function f for each matching sequence.
     */
    def slidingCollect[B](
        windowSize: Int
    )(
        f: PartialFunction[(PC, Seq[Instruction]), B]
    ): Seq[B] = {
        require(windowSize > 0)

        import scala.collection.immutable.Queue

        val max_pc = instructions.size
        var instrs: Queue[Instruction] = Queue.empty
        var firstPC, lastPC = 0
        var elementsInQueue = 0

        //
        // INITIALIZATION
        //
        while (elementsInQueue < windowSize - 1 && lastPC < max_pc) {
            instrs = instrs.enqueue(instructions(lastPC))
            lastPC = pcOfNextInstruction(lastPC)
            elementsInQueue += 1
        }

        //
        // SLIDING OVER THE CODE
        //
        var result: List[B] = List.empty
        while (lastPC < max_pc) {
            instrs = instrs.enqueue(instructions(lastPC))

            if (f.isDefinedAt((firstPC, instrs))) {
                result = f((firstPC, instrs)) :: result
            }

            firstPC = pcOfNextInstruction(firstPC)
            lastPC = pcOfNextInstruction(lastPC)
            instrs = instrs.tail
        }

        result.reverse
    }

    /**
     * Finds a sequence of instructions that are matched by the given partial function.
     *
     * @note If possible, use one of the more specialized methods, such as, [[collectPair]].
     *      The pure iteration overhead caused by this method is roughly 10-20 times higher
     *      than this one.
     *
     * @return List of pairs where the first element is the pc of the first instruction
     *      of a matched sequence and the second value is the result of the evaluation
     *      of the partial function.
     */
    def findSequence[B](
        windowSize: Int
    )(
        f: PartialFunction[Seq[Instruction], B]
    ): List[(PC, B)] = {
        require(windowSize > 0)

        import scala.collection.immutable.Queue

        val max_pc = instructions.size
        var instrs: Queue[Instruction] = Queue.empty
        var firstPC, lastPC = 0
        var elementsInQueue = 0

        //
        // INITIALIZATION
        //
        while (elementsInQueue < windowSize - 1 && lastPC < max_pc) {
            instrs = instrs.enqueue(instructions(lastPC))
            lastPC = pcOfNextInstruction(lastPC)
            elementsInQueue += 1
        }

        //
        // SLIDING OVER THE CODE
        //
        var result: List[(PC, B)] = List.empty
        while (lastPC < max_pc) {
            instrs = instrs.enqueue(instructions(lastPC))

            if (f.isDefinedAt(instrs)) {
                result = (firstPC, f(instrs)) :: result
            }

            firstPC = pcOfNextInstruction(firstPC)
            lastPC = pcOfNextInstruction(lastPC)
            instrs = instrs.tail
        }

        result.reverse
    }

    /**
     * Finds a pair of consecutive instructions that are matched by the given partial
     * function.
     *
     * ==Example Usage==
     * {{{
     * (pc, _) ← body.findPair {
     *      case (
     *          INVOKESPECIAL(receiver1, _, SingleArgumentMethodDescriptor((paramType: BaseType, _))),
     *          INVOKEVIRTUAL(receiver2, name, NoArgumentMethodDescriptor(returnType: BaseType))
     *      ) if (...) ⇒ (...)
     *      } yield ...
     * }}}
     */
    def collectPair[B](f: PartialFunction[(Instruction, Instruction), B]): List[(PC, B)] = {
        val max_pc = instructions.size

        var first_pc = 0
        var firstInstruction = instructions(first_pc)
        var second_pc = pcOfNextInstruction(0)
        var secondInstruction: Instruction = null

        var result: List[(PC, B)] = List.empty
        while (second_pc < max_pc) {
            secondInstruction = instructions(second_pc)
            val instrs = (firstInstruction, secondInstruction)
            if (f.isDefinedAt(instrs)) {
                result = (first_pc, f(instrs)) :: result
            }

            firstInstruction = secondInstruction
            first_pc = second_pc
            second_pc = pcOfNextInstruction(second_pc)
        }
        result
    }

    /**
     * Matches pairs of two consecutive instructions. For each matched pair,
     * the program counter of the first instruction is returned.
     *
     * ==Example Usage==
     * {{{
     * for {
     *  classFile ← project.view.map(_._1).par
     *  method @ MethodWithBody(body) ← classFile.methods
     *  pc ← body.matchPair({
     *      case (
     *          INVOKESPECIAL(receiver1, _, TheArgument(parameterType: BaseType)),
     *          INVOKEVIRTUAL(receiver2, name, NoArgumentMethodDescriptor(returnType: BaseType))
     *      ) ⇒ { (receiver1 eq receiver2) && (returnType ne parameterType) }
     *      case _ ⇒ false
     *      })
     *  } yield (classFile, method, pc)
     * }}}
     */
    def matchPair(f: (Instruction, Instruction) ⇒ Boolean): List[PC] = {
        val max_pc = instructions.size
        var pc1 = 0
        var pc2 = pcOfNextInstruction(pc1)

        var result: List[PC] = List.empty
        while (pc2 < max_pc) {
            if (f(instructions(pc1), instructions(pc2))) {
                result = pc1 :: result
            }

            pc1 = pc2
            pc2 = pcOfNextInstruction(pc2)
        }
        result
    }

    /**
     * Finds all sequences of three consecutive instructions that are matched by `f`.
     */
    def matchTriple(f: (Instruction, Instruction, Instruction) ⇒ Boolean): List[PC] = {
        matchTriple(Int.MaxValue, f)
    }

    /**
     * Finds a sequence of 3 consecutive instructions for which the given function returns
     * `true`, and returns the `PC` of the first instruction in each found sequence.
     *
     * @param matchMaxTriples Is the maximum number of triples that is passed to `f`.
     *      E.g., if `matchMaxTriples` is "1" only the first three instructions are
     *      passed to `f`.
     */
    def matchTriple(
        matchMaxTriples: Int                                               = Int.MaxValue,
        f:               (Instruction, Instruction, Instruction) ⇒ Boolean
    ): List[PC] = {
        val max_pc = instructions.size
        var matchedTriplesCount = 0
        var pc1 = 0
        var pc2 = pcOfNextInstruction(pc1)
        if (pc2 >= max_pc)
            return List.empty;

        var pc3 = pcOfNextInstruction(pc2)

        var result: List[PC] = List.empty
        while (pc3 < max_pc && matchedTriplesCount < matchMaxTriples) {
            if (f(instructions(pc1), instructions(pc2), instructions(pc3))) {
                result = pc1 :: result
            }

            matchedTriplesCount += 1

            // Move forward by 1 instruction at a time. Even though (..., 1, 2, 3, _, ...)
            // didn't match, it's possible that (..., _, 1, 2, 3, ...) matches.
            pc1 = pc2
            pc2 = pc3
            pc3 = pcOfNextInstruction(pc3)
        }
        result
    }

    /**
     * Returns the next instruction that will be executed at runtime that is not a
     * [[org.opalj.br.instructions.GotoInstruction]].
     * If the given instruction is not a [[org.opalj.br.instructions.GotoInstruction]],
     * the given instruction is returned.
     */
    @tailrec def nextNonGotoInstruction(pc: PC): PC = {
        instructions(pc) match {
            case GotoInstruction(branchoffset) ⇒ nextNonGotoInstruction(pc + branchoffset)
            case _                             ⇒ pc
        }
    }

    /**
     * Tests if the straight-line sequence of instructions that starts with the given `pc`
     * always ends with an `ATHROW` instruction or a method call that always throws an
     * exception. The call sequence furthermore has to contain no complex logic.
     * Here, complex means that evaluating the instruction may result in multiple control flows.
     * If the sequence contains complex logic, `false` will be returned.
     *
     * One use case of this method is, e.g., to check if the code
     * of the default case of a switch instruction always throws some error
     * (e.g., an `UnknownError` or `AssertionError`).
     * {{{
     * switch(...) {
     *  case X : ....
     *  default :
     *      throw new AssertionError();
     * }
     * }}}
     * This is a typical idiom used in Java programs and which may be relevant for
     * certain analyses to detect.
     *
     * @note   If complex control flows should also be considered it is possible to compute
     *         a methods [[org.opalj.br.cfg.CFG]] and use that one.
     *
     * @param  pc The program counter of an instruction that strictly dominates all
     *         succeeding instructions up until the next instruction (as determined
     *         by [[#cfJoins]] where two or more paths join. If the pc belongs to an instruction
     *         where multiple paths join, `false` will be returned.
     *
     * @param  anInvocation When the analysis finds a method call, it calls this method
     *         to let the caller decide whether the called method is an (indirect) way
     *         of always throwing an exception.
     *         If `true` is returned the analysis terminates and returns `true`; otherwise
     *         the analysis continues.
     *
     * @param  aThrow If all (non-exception) paths will always end in one specific
     *         `ATHROW` instruction then this function is called (callback) to let the
     *         caller decide if the "expected" exception is thrown. This analysis will
     *         return with the result of this call.
     *
     * @return `true` if the bytecode sequence starting with the instruction with the
     *         given `pc` always ends with an [[org.opalj.br.instructions.ATHROW]] instruction.
     *         `false` in all other cases (i.e., the sequence does not end with an `athrow`
     *         instruction or the control flow is more complex.)
     */
    @inline def alwaysResultsInException(
        pc:           PC,
        cfJoins:      BitSet,
        anInvocation: (PC) ⇒ Boolean,
        aThrow:       (PC) ⇒ Boolean
    ): Boolean = {

        var currentPC = pc
        while (!cfJoins.contains(currentPC)) {
            val instruction = instructions(currentPC)

            (instruction.opcode: @scala.annotation.switch) match {
                case ATHROW.opcode ⇒
                    val result = aThrow(currentPC)
                    return result;

                case RET.opcode | JSR.opcode | JSR_W.opcode ⇒
                    return false;

                case GOTO.opcode | GOTO_W.opcode ⇒
                    currentPC += instruction.asInstanceOf[GotoInstruction].branchoffset

                case /*IFs:*/ 165 | 166 | 198 | 199 |
                    159 | 160 | 161 | 162 | 163 | 164 |
                    153 | 154 | 155 | 156 | 157 | 158 ⇒
                    return false;

                case TABLESWITCH.opcode | LOOKUPSWITCH.opcode ⇒
                    return false;

                case /*xReturn:*/ 176 | 175 | 174 | 172 | 173 | 177 ⇒
                    return false;

                case INVOKEINTERFACE.opcode
                    | INVOKESPECIAL.opcode
                    | INVOKESTATIC.opcode
                    | INVOKEVIRTUAL.opcode ⇒
                    if (anInvocation(currentPC))
                        return true;

                    currentPC = pcOfNextInstruction(currentPC)

                case _ ⇒
                    currentPC = pcOfNextInstruction(currentPC)
            }
        }

        false
    }

    /**
     * This attribute's kind id.
     */
    override def kindId: Int = Code.KindId

    /**
     * A complete representation of this code attribute (including instructions,
     * attributes, etc.).
     */
    override def toString = {
        s"Code_attribute(maxStack=$maxStack, maxLocals=$maxLocals, "+
            (instructions.zipWithIndex.filter(_._1 ne null).map(_.swap).deep.toString) +
            (exceptionHandlers.toString)+","+
            (attributes.toString)+
            ")"
    }

}

/**
 * Defines constants useful when analyzing a method's code.
 *
 * @author Michael Eichberg
 */
object Code {

    def apply(
        maxStack:          Int,
        maxLocals:         Int,
        instructions:      Array[Instruction],
        exceptionHandlers: ExceptionHandlers  = IndexedSeq.empty,
        attributes:        Attributes         = IndexedSeq.empty
    ): Code = {

        var localVariableTablesCount = 0
        var lineNumberTablesCount = 0
        attributes foreach { a ⇒
            if (a.isInstanceOf[LocalVariableTable]) {
                localVariableTablesCount += 1
            } else if (a.isInstanceOf[UnpackedLineNumberTable]) {
                lineNumberTablesCount += 1
            }
        }

        if (localVariableTablesCount <= 1 && lineNumberTablesCount <= 1) {
            new Code(maxStack, maxLocals, instructions, exceptionHandlers, attributes)
        } else {
            val (localVariableTables, otherAttributes1) =
                attributes partition { _.isInstanceOf[LocalVariableTable] }
            val newAttributes1 =
                if (localVariableTables.nonEmpty && localVariableTables.tail.nonEmpty) {
                    val allLVs =
                        localVariableTables.
                            map(_.asInstanceOf[LocalVariableTable].localVariables).toIndexedSeq
                    val theLVT = allLVs.flatten
                    new LocalVariableTable(theLVT) +: otherAttributes1
                } else {
                    attributes
                }

            val (lineNumberTables, otherAttributes2) =
                newAttributes1 partition { _.isInstanceOf[UnpackedLineNumberTable] }
            val newAttributes2 =
                if (lineNumberTables.nonEmpty && lineNumberTables.tail.nonEmpty) {
                    val mergedTables =
                        lineNumberTables.flatMap(_.asInstanceOf[UnpackedLineNumberTable].lineNumbers)
                    val sortedTable =
                        mergedTables.sortWith((ltA, ltB) ⇒ ltA.startPC < ltB.startPC)
                    new UnpackedLineNumberTable(sortedTable) +: otherAttributes2
                } else {
                    newAttributes1
                }

            new Code(maxStack, maxLocals, instructions, exceptionHandlers, newAttributes2)
        }
    }

    def unapply(
        code: Code
    ): Option[(Int, Int, Array[Instruction], ExceptionHandlers, Attributes)] = {
        import code._
        Some((maxStack, maxLocals, instructions, exceptionHandlers, attributes))
    }

    /**
     * The unique id associated with attributes of kind: [[Code]].
     *
     * `KindId`s can be used for efficient branching on attributes.
     */
    final val KindId = 6

    /**
     * Used to determine the potential handlers in case that an exception is
     * thrown by an instruction.
     */
    val preDefinedClassHierarchy = ClassHierarchy.preInitializedClassHierarchy

    /**
     * The maximum number of registers required to execute the code - independent
     * of the number of parameters.
     *
     * @note    The method's descriptor may actually require
     */
    def computeMaxLocalsRequiredByCode(instructions: Array[Instruction]): Int = {

        val maxPC = instructions.length
        var pc = 0
        var maxRegisters = 0
        var modifiedByWide = false
        do {
            val i: Instruction = instructions(pc)
            if (i == WIDE) {
                modifiedByWide = true
                pc += 1
            } else {
                if (i.writesLocal && i.indexOfWrittenLocal > maxRegisters)
                    maxRegisters = i.indexOfWrittenLocal
                pc = i.indexOfNextInstruction(pc, modifiedByWide)
                modifiedByWide = false
            }

        } while (pc < maxPC)
        maxRegisters
    }

    def computeMaxLocals(
        isInstanceMethod: Boolean,
        descriptor:       MethodDescriptor,
        instructions:     Array[Instruction]
    ): Int = {
        Math.max(
            computeMaxLocalsRequiredByCode(instructions),
            descriptor.parameterTypes.foldLeft(if (isInstanceMethod) 1 else 0) { (c, n) ⇒
                c + n.computationalType.operandSize
            }
        )
    }

    /**
     * Calculates the maximum stack size required during execution of this code
     * block.
     *
     * @throws java.lang.ClassFormatError If the stack size differs between execution paths.
     */
    @throws[ClassFormatError]("if it is impossible to compute the maximum height of the stack")
    def computeMaxStack(
        instructions:      Array[Instruction],
        classHierarchy:    ClassHierarchy     = ClassHierarchy.preInitializedClassHierarchy,
        exceptionHandlers: ExceptionHandlers  = IndexedSeq.empty
    ): Int = {
        val tempCode = Code(Int.MaxValue, Int.MaxValue, instructions, exceptionHandlers)
        val cfg = CFGFactory(tempCode, classHierarchy)

        // Basic ides: follow all paths
        var maxStackDepth: Int = 0;

        var paths: Chain[(PC, Int /*stackdepth before executing the instruction with pc*/ )] = Naught
        val visitedPCs = new mutable.BitSet(instructions.length)

        // We start with the first instruction and an empty stack.
        paths :&:= ((0, 0))
        visitedPCs += 0

        // We have to make sure, that all exception handlers are evaluated for
        // max_stack, if an exception is catched, the stack size is always 1 -
        // containing the exception itself.
        for (exceptionHandler ← exceptionHandlers) {
            val handlerPC = exceptionHandler.handlerPC
            if (visitedPCs.add(handlerPC)) paths :&:= ((handlerPC, 1))
        }

        while (paths.nonEmpty) {
            val (pc, initialStackDepth) = paths.head
            paths = paths.tail
            val stackDepth = initialStackDepth + instructions(pc).stackSlotsChange
            maxStackDepth = Math.max(maxStackDepth, stackDepth)
            cfg.foreachSuccessor(pc) { succPC ⇒
                if (visitedPCs.add(succPC)) { paths :&:= ((succPC, stackDepth)) }
            }
        }

        maxStackDepth
    }

}
