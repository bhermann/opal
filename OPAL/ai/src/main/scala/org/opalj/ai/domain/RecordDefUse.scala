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
package ai
package domain

import scala.annotation.tailrec
import scala.annotation.switch
import java.io.ByteArrayOutputStream
import java.io.PrintStream

import scala.xml.Node
import scala.collection.JavaConverters._
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap
import org.opalj.graphs.DefaultMutableNode
import org.opalj.collection.mutable.{Locals ⇒ Registers}
import org.opalj.collection.immutable.:&:
import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.collection.immutable.IntTrieSet1
import org.opalj.collection.immutable.EmptyIntTrieSet
import org.opalj.bytecode.BytecodeProcessingFailedException
import org.opalj.br.Code
import org.opalj.br.ComputationalTypeCategory
import org.opalj.br.instructions._
import org.opalj.br.analyses.AnalysisException
import org.opalj.ai.util.XHTML
import org.opalj.collection.immutable.Chain.ChainBuilder

/**
 * Collects the definition/use information based on the abstract interpretation time cfg.
 * I.e., makes the information available which value is accessed where/where a used
 * value is defined.
 * In general, all local variables are identified using `Int`s
 * where the `Int` identifies the expression (by means of it's pc) which evaluated
 * to the respective value. In case of a parameter the `Int` value is equivalent to
 * the value `-parameterIndex`.
 * '''In case of exception values the `Int` value identifies the instruction which ex-/implicitly
 * raised the exception.'''
 *
 * @note A checkcast is considered a use-site, but not a def-site, even if the shape changes/
 *       the assumed type is narrowed.
 *
 * ==General Usage==
 * This trait finalizes the collection of the def/use information '''after the abstract
 * interpretation has successfully completed''' and the control-flow graph is available.
 * The information is automatically made available, when this plug-in is mixed in.
 *
 * ==Special Values==
 *
 * ===Parameters===
 * The ex-/implicit parameters given to a method have negative `int` values (the first
 * parameter has the value -1, the second -2 if the first one is a value of computational
 * type category one and -3 if the first value is of computational type category two and so forth).
 * I.e., in case of a method `def (d : Double, i : Int)`, the second parameter will have the index
 * -3.
 *
 * ==Core Properties==
 *
 * ===Reusability===
 * An instance of this domain can be reused to successively perform abstract
 * interpretations of different methods.
 * The domain's inherited `initProperties` method – which is always called by the AI
 * framework – resets the entire state related to the method.
 *
 * @author Michael Eichberg
 */
trait RecordDefUse extends RecordCFG { defUseDomain: Domain with TheCode ⇒

    // IDEA:
    // EACH LOCAL VARIABLE IS BASICALLY NAMED USING THE PC OF THE INSTRUCTION THAT INITIALIZES IT.
    //
    // EXAMPLE (AFTER COMPUTING THE DEF/USE INFORMATION)
    // PC:                0:        1:          2:           3:        4:         5:
    // INSTRUCTION        a_load0   getfield    invokestatic a_store0  getstatic  return
    // STACK              empty     [ -1 ]      [ 1 ]        [ 2 ]     empty      [ 4 ]
    // REGISTERS          0: -1     0: -1       0: -1        0: -1     0: 2       0: 1
    // USED(BY) "-1":{1}  "0": N/A  "1":{2}     "2":{3}      "3": N/A  "4": {5}   "5": N/A

    @inline final def ValueOrigins(vo: Int): IntTrieSet = IntTrieSet1(vo)
    final def NoValueOrigins: IntTrieSet = EmptyIntTrieSet

    // Stores the information where the value defined by an instruction is
    // used. The used array basically mirrors the instructions array, but has additional
    // space for storing the information about the usage of the parameters. The size
    // of this additional space is `parametersOffset` large and is prepended to
    // the array that mirrors the instructions array.
    private[this] var used: Array[ValueOrigins] = _ // initialized by initProperties
    private[this] var usedExceptions: Array[ValueOrigins] = _ // initialized by initProperties
    protected[this] var parametersOffset: Int = _ // initialized by initProperties

    // This array contains the information where each operand value found at a
    // specific instruction was defined.
    private[this] var defOps: Array[Chain[ValueOrigins]] = _ // initialized by initProperties
    // This array contains the information where each local is defined;
    // negative values indicate that the values are parameters.
    private[this] var defLocals: Array[Registers[ValueOrigins]] = _ // initialized by initProperties

    abstract override def initProperties(code: Code, cfJoins: IntTrieSet, locals: Locals): Unit = {
        val codeSize = code.codeSize
        val defOps = new Array[Chain[ValueOrigins]](codeSize)
        defOps(0) = Naught // the operand stack is empty...
        this.defOps = defOps

        // initialize initial def-use information based on the parameters
        val defLocals = new Array[Registers[ValueOrigins]](codeSize)
        var parameterIndex = 0
        defLocals(0) =
            locals.map { v ⇒
                // we always decrement parameterIndex to get the same offsets as
                // used by the AI for parameters
                parameterIndex -= 1
                if (v ne null) {
                    ValueOrigins(parameterIndex)
                } else {
                    null
                }
            }
        this.defLocals = defLocals
        this.parametersOffset = -parameterIndex

        this.used = new Array(codeSize + parametersOffset)
        this.usedExceptions = new Array(codeSize)

        super.initProperties(code, cfJoins, locals)
    }

    protected[this] def thisProperty(pc: Int): Option[String] = {
        Option(usedBy(pc)).map(_.mkString("UsedBy={", ",", "}"))
    }

    /**
     * Prints out the information by which values the current values are used.
     *
     * @inheritdoc
     */
    abstract override def properties(pc: Int, propertyToString: AnyRef ⇒ String): Option[String] = {
        super.properties(pc, propertyToString) match {
            case superProperty @ Some(description) ⇒
                thisProperty(pc) map (_+"; "+description) orElse superProperty
            case None ⇒
                thisProperty(pc)
        }
    }

    /**
     * Returns the instruction(s) which defined the value used by the instruction with the
     * given `pc` and which is stored at the stack position with the given stackIndex.
     * The first/top value on the stack has index 0 and the second value - if it exists -
     * has index two; independent of the computational category of the values.
     */
    def operandOrigin(pc: /*PC*/ Int, stackIndex: Int): ValueOrigins = defOps(pc)(stackIndex)

    /**
     * Returns the instruction(s) which define(s) the value found in the register variable with
     * index `registerIndex` and the program counter `pc`.
     */
    def localOrigin(pc: /*PC*/ Int, registerIndex: Int): ValueOrigins = defLocals(pc)(registerIndex)

    /**
     * Returns the instructions which use the value or implicit exception identified by the given
     * value origin. In case of the implicit exception thrown by instruction X, the value
     * origin is VMLevelValuesOriginOffset - pc.
     */
    def usedBy(valueOrigin: ValueOrigin): ValueOrigins = {
        if (valueOrigin > VMLevelValuesOriginOffset)
            used(valueOrigin + parametersOffset)
        else
            usedExceptions(pcOfVMLevelValue(valueOrigin))
    }

    def safeUsedBy(valueOrigin: ValueOrigin): ValueOrigins = {
        val usedBy = this.usedBy(valueOrigin)
        if (usedBy eq null)
            NoValueOrigins
        else
            usedBy
    }

    /**
     * Returns the union of the set of unused parameters and the set of all instructions which
     * compute a value that is not used in the following.
     */
    def unused(): ValueOrigins = {
        var unused = NoValueOrigins

        // 1. check if the parameters are used...
        val parametersOffset = this.parametersOffset
        val defLocals0 = defLocals(0)
        var parameterIndex = 0
        while (parameterIndex < parametersOffset) {
            if (defLocals0(parameterIndex) ne null) /*we may have parameters with comp. type 2*/ {
                val unusedParameter = -parameterIndex - 1
                val usedBy = this.usedBy(unusedParameter)
                if (usedBy eq null) { unused += unusedParameter }
            }
            parameterIndex += 1
        }

        // 2. check instructions
        code iterate { (pc, instruction) ⇒
            if (instruction.opcode != CHECKCAST.opcode) {
                // a checkcast instruction is already a use
                instruction.expressionResult match {
                    case NoExpression        ⇒ // nothing to do
                    case Stack | Register(_) ⇒ if (usedBy(pc) eq null) { unused += pc }
                }
            }
        }

        unused
    }

    private[this] def updateUsageInformation(usedValues: ValueOrigins, useSite: /*PC*/ Int): Unit = {
        usedValues.foreach { usedValue ⇒
            if (usedValue > VMLevelValuesOriginOffset) {
                val usedIndex = usedValue + parametersOffset
                val oldUsedInfo: ValueOrigins = used(usedIndex)
                if (oldUsedInfo eq null) {
                    used(usedIndex) = ValueOrigins(useSite)
                } else {
                    val newUsedInfo = oldUsedInfo + useSite
                    if (newUsedInfo ne oldUsedInfo)
                        used(usedIndex) = newUsedInfo
                }
            } else {
                // we have a usage of an implicit exception
                val usedIndex = pcOfVMLevelValue(usedValue)
                val oldUsedExceptionsInfo: ValueOrigins = usedExceptions(usedIndex)
                if (oldUsedExceptionsInfo eq null) {
                    usedExceptions(usedIndex) = ValueOrigins(useSite)
                } else {
                    val newUsedExceptionsInfo = oldUsedExceptionsInfo + useSite
                    if (newUsedExceptionsInfo ne oldUsedExceptionsInfo)
                        usedExceptions(usedIndex) = newUsedExceptionsInfo
                }
            }
        }
    }

    protected[this] def propagate(
        currentPC: /*PC*/ Int,
        successorPC: /*PC*/ Int,
        newDefOps:    Chain[ValueOrigins],
        newDefLocals: Registers[ValueOrigins]
    )(
        implicit
        cfJoins:                 IntTrieSet,
        isSubroutineInstruction: ( /*PC*/ Int) ⇒ Boolean
    ): Boolean = {
        if (cfJoins.contains(successorPC) && (defLocals(successorPC) ne null /*non-dead*/ )) {
            var forceScheduling = false
            // we now also have to perform a join...
            @tailrec def joinDefOps(
                oldDefOps:     Chain[ValueOrigins],
                lDefOps:       Chain[ValueOrigins],
                rDefOps:       Chain[ValueOrigins],
                oldIsSuperset: Boolean                    = true,
                joinedDefOps:  ChainBuilder[ValueOrigins] = Chain.newBuilder[ValueOrigins]
            ): Chain[ValueOrigins] = {
                if (lDefOps.isEmpty) {
                    // assert(rDefOps.isEmpty)
                    return if (oldIsSuperset) oldDefOps else joinedDefOps.result();
                }
                // assert(
                //     rDefOps.nonEmpty,
                //     s"unexpected (pc:$currentPC -> pc:$successorPC): $lDefOps vs. $rDefOps;"+
                //      s" original: $oldDefOps"
                // )

                val newHead = lDefOps.head
                val oldHead = rDefOps.head
                if (newHead.subsetOf(oldHead))
                    joinDefOps(
                        oldDefOps,
                        lDefOps.tail, rDefOps.tail,
                        oldIsSuperset, joinedDefOps += oldHead
                    )
                else {
                    val joinedHead = newHead ++ oldHead
                    // assert(newHead.subsetOf(joinedHead))
                    // assert(
                    //      oldHead.subsetOf(joinedHead),
                    //      s"$newHead ++ $oldHead is $joinedHead"
                    // )
                    // assert(
                    //      joinedHead.size > oldHead.size,
                    //      s"$newHead ++  $oldHead is $joinedHead"
                    // )
                    joinDefOps(
                        oldDefOps,
                        lDefOps.tail, rDefOps.tail,
                        false, joinedDefOps += joinedHead
                    )
                }
            }

            val oldDefOps = defOps(successorPC)
            if (newDefOps ne oldDefOps) {
                val joinedDefOps = joinDefOps(oldDefOps, newDefOps, oldDefOps)
                if (joinedDefOps ne oldDefOps) {
                    // assert(
                    //     joinedDefOps != oldDefOps,
                    //     s"$joinedDefOps is unexpectedly equal to $newDefOps join $oldDefOps"
                    // )
                    forceScheduling = true
                    // joinedDefOps.foreach{vo ⇒
                    //    require(vo != null, s"$newDefOps join $oldDefOps == null")
                    //}
                    defOps(successorPC) = joinedDefOps
                }
            }

            val oldDefLocals = defLocals(successorPC)
            if (newDefLocals ne oldDefLocals) {
                // newUsage is `true` if a new value(variable) may be used somewhere
                // (I)
                // For example:
                // 0: ALOAD_0
                // 1: INVOKEVIRTUAL com.sun.media.sound.EventDispatcher dispatchEvents (): void
                // 4: GOTO 0↑
                // 7: ASTORE_1 // exception handler for the instruction with pc 1
                // 8: GOTO 0↑
                // The last goto leads to some new information regarding the values
                // on the stack (e.g., Register 1 now contains an exception), but
                // propagating this information is useless - the value is never used...
                // (II)
                // Furthermore, whenever we have a jump back to the first instruction
                // (PC == 0) and the joined values are unrelated to the parameters
                // - i.e., we do not assign a new value to a register used by a
                // parameter - then we do not have to force a scheduling of the reevaluation of
                // the next instruction since there has to be some assignment related to the
                // respective variables (there is no load without a previous store).
                var newUsage = false
                val joinedDefLocals =
                    oldDefLocals.fuse(
                        newDefLocals,
                        { (o, n) ⇒
                            // In general, if n or o equals null, then
                            // the register variable did not contain any
                            // useful information when the current instruction was
                            // reached for the first time, hence there will
                            // always be an initialization before the next
                            // use of the register value and we can drop all
                            // information.... unless we have a JSR/RET and we are in
                            // a subroutine!
                            if (o eq null) {
                                if ((n ne null) && isSubroutineInstruction(successorPC)) {
                                    newUsage = true
                                    n
                                } else {
                                    null
                                }
                            } else if (n eq null) {
                                if ((o ne null) && isSubroutineInstruction(successorPC)) {
                                    newUsage = true
                                    o
                                } else {
                                    null
                                }
                            } else if (n subsetOf o) {
                                o
                            } else {
                                newUsage = true
                                val joinedDefLocals = n ++ o
                                // assert(
                                //      joinedDefLocals.size > o.size,
                                //      s"$n ++  $o is $joinedDefLocals"
                                // )
                                joinedDefLocals
                            }
                        }
                    )
                if (joinedDefLocals ne oldDefLocals) {
                    // assert(
                    //      joinedDefLocals != oldDefLocals,
                    //      s"$joinedDefLocals should not be equal to "+
                    //          s"$newDefLocals join $oldDefLocals"
                    // )
                    // There is nothing to do if all joins are related to unused vars...
                    if (newUsage) forceScheduling = true
                    defLocals(successorPC) = joinedDefLocals
                }
            }

            forceScheduling
        } else {
            newDefOps.foreach(vo ⇒ require(vo != null, s"$newDefOps contains null"))
            defOps(successorPC) = newDefOps
            defLocals(successorPC) = newDefLocals
            true // <=> always schedule the execution of the next instruction
        }
    }

    /**
     * Returns the origins of a domain value. This method is intended to be overridden by
     * domains that provide more precise def/use information than the default def/use analysis.
     *
     * E.g., the l1.ReferenceValues domain tracks alias relations and can (when we inline calls)
     * correctly identify those returned values that were passed to it.
     *
     * @param domainValue The domain value for which the origin information is required.
     *                    If no information is available, `defaultOrigins` should be returned.
     * @param defaultOrigins The default origin information.
     * @return The origin information for the given `domainValue`.
     */
    protected[this] def originsOf(
        domainValue:    DomainValue,
        defaultOrigins: ValueOrigins
    ): ValueOrigins = {
        defaultOrigins
    }

    protected[this] def newDefOpsForExceptionalControlFlow(
        currentPC: /*PC*/   Int,
        currentInstruction: Instruction,
        successorPC: /*PC*/ Int
    )(
        implicit
        operandsArray: OperandsArray
    ): Chain[ValueOrigins] = {
        // The stack only contains the exception (which was created before
        // and was explicitly thrown by an athrow instruction or which resulted from
        // a called method or which was created by the JVM).
        // (Whether we had a join or not is irrelevant.)

        val newDefOps = if (currentInstruction.isAthrow) {
            // The thrown value may be null... in that case the thrown exception is
            // the VM generated NullPointerException.
            val thrownValue = operandsArray(currentPC).head
            val exceptionIsNull = refIsNull(currentPC, thrownValue)
            var newDefOps = NoValueOrigins
            if (exceptionIsNull.isYesOrUnknown) newDefOps += ValueOriginForVMLevelValue(currentPC)
            if (exceptionIsNull.isNoOrUnknown) newDefOps ++= defOps(currentPC).head
            newDefOps
        } else {
            // The instruction implicitly threw the exception... (it was not athrow...)
            ValueOrigins(ValueOriginForVMLevelValue(currentPC))
        }

        new :&:(originsOf(operandsArray(successorPC).head, newDefOps))
    }

    /*
     * Specifies that the given number of stack values is used/popped from
     * the stack and that – optionally – a new value is pushed onto the stack (and
     * associated with a new variable).
     *
     * The usage is independent of the question whether the usage resulted in an
     * exceptional control flow.
     */
    protected[this] def stackOperation(
        currentPC: /*PC*/         Int,
        currentInstruction:       Instruction,
        successorPC: /*PC*/       Int,
        isExceptionalControlFlow: Boolean,
        usedValues:               Int,
        pushesValue:              Boolean
    )(
        implicit
        cfJoins:                 IntTrieSet,
        isSubroutineInstruction: ( /*PC*/ Int) ⇒ Boolean,
        operandsArray:           OperandsArray
    ): Boolean = {
        val currentDefOps = defOps(currentPC)
        currentDefOps.forFirstN(usedValues) { op ⇒ updateUsageInformation(op, currentPC) }

        val newDefOps: Chain[ValueOrigins] =
            if (isExceptionalControlFlow) {
                newDefOpsForExceptionalControlFlow(currentPC, currentInstruction, successorPC)
            } else {
                if (pushesValue)
                    originsOf(operandsArray(successorPC).head, ValueOrigins(currentPC)) :&:
                        currentDefOps.drop(usedValues)
                else
                    currentDefOps.drop(usedValues)
            }

        propagate(currentPC, successorPC, newDefOps, defLocals(currentPC))
    }

    protected[this] def registerReadWrite(
        currentPC: /*PC*/ Int,
        successorPC: /*PC*/ Int,
        index:       Int
    )(
        implicit
        cfJoins:                 IntTrieSet,
        isSubroutineInstruction: ( /*PC*/ Int) ⇒ Boolean,
        localsArray:             LocalsArray
    ): Boolean = {
        val currentDefLocals = defLocals(currentPC)
        updateUsageInformation(currentDefLocals(index), currentPC)
        val newOrigin = originsOf(localsArray(successorPC)(index), ValueOrigins(currentPC))
        val newDefLocals = currentDefLocals.updated(index, newOrigin)
        propagate(currentPC, successorPC, defOps(currentPC), newDefLocals)
    }

    /**
     * Updates/computes the def/use information when the instruction with
     * the pc `successorPC` is executed immediately after the instruction with `currentPC`.
     */
    private[this] def handleFlow(
        currentPC:                Int /*PC*/ ,
        successorPC:              Int /*PC*/ ,
        isExceptionalControlFlow: Boolean
    )(
        implicit
        cfJoins:                 IntTrieSet,
        isSubroutineInstruction: ( /*PC*/ Int) ⇒ Boolean,
        operandsArray:           OperandsArray,
        localsArray:             LocalsArray
    ): Boolean = {

        val currentInstruction = code.instructions(currentPC)

        //
        // HELPER METHODS
        //

        def propagate(
            newDefOps:    Chain[ValueOrigins],
            newDefLocals: Registers[ValueOrigins]
        ): Boolean = {
            defUseDomain.propagate(currentPC, successorPC, newDefOps, newDefLocals)
        }

        def stackOperation(usedValues: Int, pushesValue: Boolean): Boolean = {
            defUseDomain.stackOperation(
                currentPC,
                currentInstruction,
                successorPC,
                isExceptionalControlFlow,
                usedValues, pushesValue
            )
        }

        def load(index: Int): Boolean = {
            // there will never be an exceptional control flow ...
            val currentLocals = defLocals(currentPC)
            val newDefOps = currentLocals(index) :&: defOps(currentPC)
            propagate(newDefOps, currentLocals)
        }

        def store(index: Int): Boolean = {
            // there will never be an exceptional control flow ...
            val currentOps = defOps(currentPC)
            val newDefLocals = defLocals(currentPC).updated(index, currentOps.head)
            propagate(currentOps.tail, newDefLocals)
        }

        //
        // THE IMPLEMENTATION...
        //
        val scheduleNextPC: Boolean = (currentInstruction.opcode: @switch) match {
            case GOTO.opcode | GOTO_W.opcode |
                NOP.opcode |
                WIDE.opcode ⇒
                propagate(defOps(currentPC), defLocals(currentPC))

            case JSR.opcode | JSR_W.opcode ⇒
                stackOperation(0, pushesValue = true)

            case RET.opcode ⇒
                val RET(lvIndex) = currentInstruction
                val currentDefLocals = defLocals(currentPC)
                val returnAddressValue = currentDefLocals(lvIndex)
                updateUsageInformation(returnAddressValue, currentPC)
                propagate(defOps(currentPC), currentDefLocals)

            case IF_ACMPEQ.opcode | IF_ACMPNE.opcode
                | IF_ICMPEQ.opcode | IF_ICMPNE.opcode
                | IF_ICMPGT.opcode | IF_ICMPGE.opcode | IF_ICMPLT.opcode | IF_ICMPLE.opcode ⇒
                stackOperation(2, pushesValue = false)

            case IFNULL.opcode | IFNONNULL.opcode
                | IFEQ.opcode | IFNE.opcode
                | IFGT.opcode | IFGE.opcode | IFLT.opcode | IFLE.opcode
                | LOOKUPSWITCH.opcode | TABLESWITCH.opcode ⇒
                stackOperation(1, pushesValue = false)

            case ATHROW.opcode ⇒
                val pushesValues = true /* <= irrelevant; athrow has special handling downstream */
                stackOperation(1, pushesValues)

            //
            // ARRAYS
            //
            case NEWARRAY.opcode | ANEWARRAY.opcode ⇒
                stackOperation(1, pushesValue = true)

            case ARRAYLENGTH.opcode ⇒
                stackOperation(1, pushesValue = true)

            case MULTIANEWARRAY.opcode ⇒
                val dims = currentInstruction.asInstanceOf[MULTIANEWARRAY].dimensions
                stackOperation(dims, pushesValue = true)

            case 50 /*aaload*/ |
                49 /*daload*/ | 48 /*faload*/ |
                51 /*baload*/ |
                52 /*caload*/ | 46 /*iaload*/ | 47 /*laload*/ | 53 /*saload*/ ⇒
                stackOperation(2, pushesValue = true)

            case 83 /*aastore*/ |
                84 /*bastore*/ |
                85 /*castore*/ | 79 /*iastore*/ | 80 /*lastore*/ | 86 /*sastore*/ |
                82 /*dastore*/ | 81 /*fastore*/ ⇒
                stackOperation(3, pushesValue = false)

            //
            // FIELD ACCESS
            //
            case 180 /*getfield*/ ⇒
                stackOperation(1, pushesValue = true)
            case 178 /*getstatic*/ ⇒
                stackOperation(0, pushesValue = true)
            case 181 /*putfield*/ ⇒
                stackOperation(2, pushesValue = false)
            case 179 /*putstatic*/ ⇒
                stackOperation(1, pushesValue = false)

            //
            // MONITOR
            //

            case 194 /*monitorenter*/ ⇒
                stackOperation(1, pushesValue = false)
            case 195 /*monitorexit*/ ⇒
                stackOperation(1, pushesValue = false)

            //
            // METHOD INVOCATIONS
            //
            case 184 /*invokestatic*/ | 186 /*invokedynamic*/ |
                185 /*invokeinterface*/ | 183 /*invokespecial*/ | 182 /*invokevirtual*/ ⇒
                val invoke = currentInstruction.asInvocationInstruction
                val descriptor = invoke.methodDescriptor
                stackOperation(
                    invoke.numberOfPoppedOperands(ComputationalTypeCategoryNotAvailable),
                    !descriptor.returnType.isVoidType
                )

            //
            // LOAD AND STORE INSTRUCTIONS
            //
            case 25 /*aload*/ | 24 /*dload*/ | 23 /*fload*/ | 21 /*iload*/ | 22 /*lload*/ ⇒
                load(currentInstruction.asLoadLocalVariableInstruction.lvIndex)
            case 42 /*aload_0*/ |
                38 /*dload_0*/ | 34 /*fload_0*/ | 26 /*iload_0*/ | 30 /*lload_0*/ ⇒
                load(0)
            case 43 /*aload_1*/ |
                39 /*dload_1*/ | 35 /*fload_1*/ | 27 /*iload_1*/ | 31 /*lload_1*/ ⇒
                load(1)
            case 44 /*aload_2*/ |
                40 /*dload_2*/ | 36 /*fload_2*/ | 28 /*iload_2*/ | 32 /*lload_2*/ ⇒
                load(2)
            case 45 /*aload_3*/ |
                41 /*dload_3*/ | 37 /*fload_3*/ | 29 /*iload_3*/ | 33 /*lload_3*/ ⇒
                load(3)

            case 58 /*astore*/ |
                57 /*dstore*/ | 56 /*fstore*/ | 54 /*istore*/ | 55 /*lstore*/ ⇒
                store(currentInstruction.asStoreLocalVariableInstruction.lvIndex)
            case 75 /*astore_0*/ |
                71 /*dstore_0*/ | 67 /*fstore_0*/ | 63 /*lstore_0*/ | 59 /*istore_0*/ ⇒
                store(0)
            case 76 /*astore_1*/ |
                72 /*dstore_1*/ | 68 /*fstore_1*/ | 64 /*lstore_1*/ | 60 /*istore_1*/ ⇒
                store(1)
            case 77 /*astore_2*/ |
                73 /*dstore_2*/ | 69 /*fstore_2*/ | 65 /*lstore_2*/ | 61 /*istore_2*/ ⇒
                store(2)
            case 78 /*astore_3*/ |
                74 /*dstore_3*/ | 70 /*fstore_3*/ | 66 /*lstore_3*/ | 62 /*istore_3*/ ⇒
                store(3)

            //
            // PUSH CONSTANT VALUE
            //
            case 1 /*aconst_null*/ |
                2 /*iconst_m1*/ |
                3 /*iconst_0*/ | 4 /*iconst_1*/ |
                5 /*iconst_2*/ | 6 /*iconst_3*/ | 7 /*iconst_4*/ | 8 /*iconst_5*/ |
                9 /*lconst_0*/ | 10 /*lconst_1*/ |
                11 /*fconst_0*/ | 12 /*fconst_1*/ | 13 /*fconst_2*/ |
                14 /*dconst_0*/ | 15 /*dconst_1*/ |
                16 /*bipush*/ | 17 /*sipush*/ |
                18 /*ldc*/ | 19 /*ldc_w*/ | 20 /*ldc2_w*/ ⇒
                stackOperation(0, pushesValue = true)

            //
            // RELATIONAL OPERATORS
            //
            case 148 /*lcmp*/ |
                150 /*fcmpg*/ | 149 /*fcmpl*/ |
                152 /*dcmpg*/ | 151 /*dcmpl*/ ⇒
                stackOperation(2, pushesValue = true)

            //
            // UNARY EXPRESSIONS
            //
            case 116 /*ineg*/ | 117 /*lneg*/ | 119 /*dneg*/ | 118 /*fneg*/ ⇒
                stackOperation(1, pushesValue = true)

            case NEW.opcode ⇒
                stackOperation(0, pushesValue = true)

            //
            // BINARY EXPRESSIONS
            //
            case IINC.opcode ⇒
                val IINC(index, _) = currentInstruction
                registerReadWrite(currentPC, successorPC, index)

            case 99 /*dadd*/ | 111 /*ddiv*/ | 107 /*dmul*/ | 115 /*drem*/ | 103 /*dsub*/ |
                98 /*fadd*/ | 110 /*fdiv*/ | 106 /*fmul*/ | 114 /*frem*/ | 102 /*fsub*/ |
                109 /*ldiv*/ | 105 /*lmul*/ | 113 /*lrem*/ | 101 /*lsub*/ | 97 /*ladd*/ |
                96 /*iadd*/ | 108 /*idiv*/ | 104 /*imul*/ | 112 /*irem*/ | 100 /*isub*/ |
                126 /*iand*/ | 128 /*ior*/ | 130 /*ixor*/ |
                127 /*land*/ | 129 /*lor*/ | 131 /*lxor*/ |
                120 /*ishl*/ | 122 /*ishr*/ | 124 /*iushr*/ |
                121 /*lshl*/ | 123 /*lshr*/ | 125 /*lushr*/ ⇒
                stackOperation(2, pushesValue = true)

            //
            // GENERIC STACK MANIPULATION
            //
            case 89 /*dup*/ ⇒
                val oldDefOps = defOps(currentPC)
                propagate(oldDefOps.head :&: oldDefOps, defLocals(currentPC))
            case 90 /*dup_x1*/ ⇒
                val v1 :&: v2 :&: rest = defOps(currentPC)
                propagate(v1 :&: v2 :&: v1 :&: rest, defLocals(currentPC))
            case 91 /*dup_x2*/ ⇒
                operandsArray(currentPC) match {
                    case (_ /*v1 @ CTC1()*/ ) :&: (_@ CTC1()) :&: _ ⇒
                        val (v1 :&: v2 :&: v3 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v3 :&: v1 :&: rest, defLocals(currentPC))
                    case _ ⇒
                        val (v1 :&: v2 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v1 :&: rest, defLocals(currentPC))
                }
            case 92 /*dup2*/ ⇒
                operandsArray(currentPC) match {
                    case (_@ CTC1()) :&: _ ⇒
                        val currentDefOps = defOps(currentPC)
                        val (v1 :&: v2 :&: _) = currentDefOps
                        propagate(v1 :&: v2 :&: currentDefOps, defLocals(currentPC))
                    case _ ⇒
                        val oldDefOps = defOps(currentPC)
                        propagate(oldDefOps.head :&: defOps(currentPC), defLocals(currentPC))
                }
            case 93 /*dup2_x1*/ ⇒
                operandsArray(currentPC) match {
                    case (_@ CTC1()) :&: _ ⇒
                        val (v1 :&: v2 :&: v3 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v3 :&: v1 :&: v2 :&: rest, defLocals(currentPC))
                    case _ ⇒
                        val (v1 :&: v2 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v1 :&: rest, defLocals(currentPC))
                }
            case 94 /*dup2_x2*/ ⇒
                operandsArray(currentPC) match {
                    case (_@ CTC1()) :&: (_@ CTC1()) :&: (_@ CTC1()) :&: _ ⇒
                        val (v1 :&: v2 :&: v3 :&: v4 :&: rest) = defOps(currentPC)
                        val currentLocals = defLocals(currentPC)
                        propagate(v1 :&: v2 :&: v3 :&: v4 :&: v1 :&: v2 :&: rest, currentLocals)
                    case (_@ CTC1()) :&: (_@ CTC1()) :&: _ ⇒
                        val (v1 :&: v2 :&: v3 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v3 :&: v1 :&: v2 :&: rest, defLocals(currentPC))
                    case (_ /*v1 @ CTC2()*/ ) :&: (_@ CTC1()) :&: _ ⇒
                        val (v1 :&: v2 :&: v3 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v3 :&: v1 :&: rest, defLocals(currentPC))
                    case _ ⇒
                        val (v1 :&: v2 :&: rest) = defOps(currentPC)
                        propagate(v1 :&: v2 :&: v1 :&: rest, defLocals(currentPC))
                }

            case 87 /*pop*/ ⇒
                propagate(defOps(currentPC).tail, defLocals(currentPC))
            case 88 /*pop2*/ ⇒
                if (operandsArray(currentPC).head.computationalType.operandSize == 1)
                    propagate(defOps(currentPC).drop(2), defLocals(currentPC))
                else
                    propagate(defOps(currentPC).tail, defLocals(currentPC))

            case 95 /*swap*/ ⇒
                val v1 :&: v2 :&: rest = defOps(currentPC)
                propagate(v2 :&: v1 :&: rest, defLocals(currentPC))

            //
            // VALUE CONVERSIONS
            //
            case 144 /*d2f*/ | 142 /*d2i*/ | 143 /*d2l*/ |
                141 /*f2d*/ | 139 /*f2i*/ | 140 /*f2l*/ |
                145 /*i2b*/ | 146 /*i2c*/ | 135 /*i2d*/ | 134 /*i2f*/ | 133 /*i2l*/ | 147 /*i2s*/ |
                138 /*l2d*/ | 137 /*l2f*/ | 136 /*l2i*/ |
                193 /*instanceof*/ ⇒
                stackOperation(1, pushesValue = true)

            case CHECKCAST.opcode ⇒
                // Recall that – even if the cast is successful NOW (i.e., we don't have an
                // exceptional control flow) - that does not mean that the cast was useless.
                // At this point in time we simply don't have the necessary information to
                // decide whether the cast is truly useless.
                // E.g,.
                //      AbstractList abstractL = ...;
                //      List l = (java.util.List) abstractL; // USELESS
                //      ArrayList al = (java.util.ArrayList) l; // MAY OR MAY NO SUCCEED
                val currentDefOps = defOps(currentPC)
                val op = currentDefOps.head
                updateUsageInformation(op, currentPC)
                val newDefOps =
                    if (isExceptionalControlFlow) {
                        newDefOpsForExceptionalControlFlow(
                            currentPC, currentInstruction, successorPC
                        )
                    } else {
                        currentDefOps
                    }
                propagate(newDefOps, defLocals(currentPC))

            //
            // "ERROR" HANDLING
            //
            case RETURN.opcode ⇒
                if (isExceptionalControlFlow) {
                    val pushesValue = true /* value doesn't matter - special handling downstream */
                    stackOperation(0, pushesValue)
                } else {
                    val message = s"a return instruction does not have regular successors"
                    throw BytecodeProcessingFailedException(message)
                }

            case 176 /*a...*/ | 175 /*d...*/ | 174 /*f...*/ | 172 /*i...*/ | 173 /*l...return*/ ⇒
                if (isExceptionalControlFlow) {
                    val pushesValue = true /* value doesn't matter - special handling downstream */
                    stackOperation(1, pushesValue)
                } else {
                    val message = s"a(n) $currentInstruction does not have regular successors"
                    throw BytecodeProcessingFailedException(message)
                }

            case opcode ⇒ throw BytecodeProcessingFailedException(s"unknown opcode: $opcode")
        }

        scheduleNextPC
    }

    /**
     * Completes the computation of the definition/use information by using the recorded cfg.
     */
    abstract override def abstractInterpretationEnded(
        aiResult: AIResult { val domain: defUseDomain.type }
    ): Unit = {
        super.abstractInterpretationEnded(aiResult)

        if (aiResult.wasAborted)
            return /* nothing to do */ ;

        val instructions = code.instructions
        val operandsArray = aiResult.operandsArray
        val localsArray = aiResult.localsArray
        val subroutineInstructions = aiResult.subroutineInstructions
        implicit val isSubroutineInstruction: Int /*PC*/ ⇒ Boolean = subroutineInstructions.contains
        implicit val cfJoins = aiResult.cfJoins

        // General idea related to JSR/RET:
        // Follow JSRs eagerly; RET only to ACTIVE callers. (Recall the underlying CFG already knows
        // ALL ret targets, but if we have a JSR that was not yet executed, it may be the
        // case that some instructions (on the way to the respective JSR) are relevant for the
        // state afterwards!)
        //
        // Given that some JSRs have no RET (only athrow/returns instead!); the values are the
        // pcs of the JSRs unless a ret was found that actually ends the subroutine -
        // then the value is -1.
        val retTargetPCs = new Int2IntLinkedOpenHashMap(4)
        val nextPCs: IntLinkedOpenHashSet = new IntLinkedOpenHashSet()
        nextPCs.add(0)

        def checkAndScheduleNextSubroutine(): Boolean = {
            // When we reach this point "nextPCs" is empty!

            /* We have to ensure that all paths of a specific subroutine are
             * actually evaluated before we return. Therefore, in case of
             * deeply nested jsr-rets, we have to make sure that we "ret" as
             * soon as all jumps to the subroutine have been evaluated,
             * otherwise, we may circumvent a relevant control-flow.
             *
             * A concrete example (belonging to the qualitas corpus) is:
             *  com.aelitis.azureus.plugins.dht.impl.DHTPluginStorageManager{
             *      public com.aelitis.azureus.core.dht.DHTStorageBlock keyBlockRequest(
             *          com.aelitis.azureus.core.dht.transport.DHTTransportContact,byte[],byte[]
             *      )
             *  }
             * In general, we have to ensure that a subroutine that is called
             * directly by the main code, but which may also be called after
             * some other subroutines were evaluated, is only evaluated after the
             * other subroutines have been completely evaluated.
             */
            while (!retTargetPCs.isEmpty) {
                val retTargetPC = retTargetPCs.lastIntKey()
                val jsrPC = retTargetPCs.removeLastInt()
                if (jsrPC == -1) {
                    nextPCs.add(retTargetPC)
                    return true;
                } else {
                    val retCandidatePC = predecessorsOf(retTargetPC).filter(instructions(_).isRET)
                    if (retCandidatePC.isSingletonSet) {
                        val retPC = retCandidatePC.head
                        updateUsageInformation(IntTrieSet1(jsrPC), retPC)
                        val oldRetTargetPCDefLocals = defLocals(retTargetPC)
                        val oldRetTargetPCDefOps = defOps(retTargetPC)
                        val retDefLocals = defLocals(retPC)
                        val retDefOps = defOps(retPC)
                        if (propagate(retPC, retTargetPC, retDefOps, retDefLocals) && (
                            oldRetTargetPCDefLocals != defLocals(retTargetPC) ||
                            oldRetTargetPCDefOps != defOps(retTargetPC)
                        )) {
                            nextPCs.add(retTargetPC)
                            return true;
                        }
                    }
                    /*
                    else the subroutine never returns via RET and therefore retTargetPC is
                    actually not an instruction reached via a RET...
                    */
                }
            }
            false
        }

        while (!nextPCs.isEmpty || checkAndScheduleNextSubroutine()) {
            val currPC = nextPCs.removeLastInt() // in case of JSRs we need to decent eagerly...

            def handleSuccessor(isExceptionalControlFlow: Boolean)(succPC: PC): Unit = {
                val scheduleNextPC = try {
                    handleFlow(
                        currPC, succPC, isExceptionalControlFlow
                    )(
                        cfJoins, subroutineInstructions.contains,
                        operandsArray, localsArray
                    )
                } catch {
                    case e: Throwable ⇒
                        val method = analyzedEntity(aiResult.domain)
                        var message = s"def-use computation failed for: $method\n"
                        try {
                            message += s"\tCurrent PC: $currPC; Successor PC: $succPC\n"
                            message +=
                                retTargetPCs.int2IntEntrySet().iterator.asScala.
                                mkString("\tRET Target PCs: ", ",", "\n")
                            message += s"\tStack: ${defOps(currPC)}\n"
                            val localsDump =
                                defLocals(currPC).zipWithIndex.map { e ⇒
                                    val (local, index) = e; s"$index: $local"
                                }
                            message += localsDump.mkString("\tLocals:\n\t\t", "\n\t\t", "\n")
                            val bout = new ByteArrayOutputStream()
                            val pout = new PrintStream(bout)
                            e.printStackTrace(pout)
                            pout.flush()
                            val stacktrace = bout.toString("UTF-8")
                            message += "\tStacktrace: \n\t"+stacktrace+"\n"
                        } catch {
                            case t: Throwable ⇒
                                message += s"<fatal error while collecting : ${t.getMessage}>"
                        }
                        // val htmlMessage =
                        // message.
                        //     replace("\n", "<br>").
                        //     replace("\t", "&nbsp;&nbsp;") +
                        //     dumpDefUseInfo().toString
                        // org.opalj.io.writeAndOpen(htmlMessage, "defuse", ".html")
                        throw AnalysisException(message, e);
                }

                assert(defLocals(succPC) ne null)
                assert(defOps(succPC) ne null)

                val currentInstruction = instructions(currPC)
                currentInstruction.opcode match {
                    case JSR.opcode | JSR_W.opcode ⇒
                        val retTargetPC = code.pcOfNextInstruction(currPC)
                        if (scheduleNextPC) {
                            retTargetPCs.put(retTargetPC, currPC)
                            nextPCs.add(succPC) // will be executed next(!)
                        } else {
                            // Let's search for "a" potential last evaluation of the subroutine ...
                            // it must contain at least the current def-use information.
                            // (Every instruction is returned-to by at most one RET.)
                            val retCandidatePC =
                                predecessorsOf(retTargetPC).filter { predPC ⇒
                                    instructions(predPC).opcode == RET.opcode
                                }
                            if (retCandidatePC.isSingletonSet) {
                                val retPC = retCandidatePC.head
                                val retDefLocals = defLocals(retPC)
                                val retDefOps = defOps(retPC)
                                updateUsageInformation(IntTrieSet1(currPC), retPC)
                                if (propagate(retPC, retTargetPC, retDefOps, retDefLocals))
                                    nextPCs.add(retTargetPC)
                            }
                            /*
                            else the subroutine never returns via RET and therefore retTargetPC is
                            actually not an instruction reached via a RET...
                            */
                        }

                    case RET.opcode ⇒ /* nothing to do... */

                    case _ ⇒
                        if (scheduleNextPC) {
                            nextPCs.add(succPC)
                        }
                }

            }

            val currentSuccessors = regularSuccessorsOf(currPC)
            if (instructions(currPC).isRET) {
                // we can't schedule the target of a RET instruction related to a not-yet executed
                // JSR(!)
                currentSuccessors foreach { retTargetPC ⇒
                    if (retTargetPCs.containsKey(retTargetPC)) {
                        retTargetPCs.replace(retTargetPC, -1)
                        handleSuccessor(false)(retTargetPC)
                    }
                }
            } else {
                currentSuccessors foreach { handleSuccessor(false) }
            }
            val currentExceptionHandlerSuccessors = exceptionHandlerSuccessorsOf(currPC)
            currentExceptionHandlerSuccessors foreach { handleSuccessor(true) }
            if (currentSuccessors.isEmpty && currentExceptionHandlerSuccessors.isEmpty) {
                // e.g., athrow, return or any instruction which potentially leads to an abnormal
                // return (this excludes, notably, iinc; hence, it has to be an instruction which
                // just operates on the stack and which is not a stack management instruction (dup,
                // ...))
                val usedValues = instructions(currPC).numberOfPoppedOperands(NotRequired)
                defOps(currPC).forFirstN(usedValues) { op ⇒ updateUsageInformation(op, currPC) }
            }
        }
    }

    // #############################################################################################
    // #
    // #
    // # DEBUG
    // #
    // #
    // #############################################################################################

    /**
     * Creates an XHTML document that contains information about the def-/use
     * information.
     */
    def dumpDefUseInfo(): Node = {
        XHTML.createXHTML(Some("Definition/Use Information"), dumpDefUseTable())
    }

    /**
     * Creates an XHTML table node which contains the def/use information.
     */
    def dumpDefUseTable(): Node = {
        val instructions = code.instructions
        val perInstruction =
            defOps.zip(defLocals).zipWithIndex.
                filter(e ⇒ e._1._1 != null || e._1._2 != null).
                map { e ⇒
                    val ((os, ls), i) = e
                    val operands =
                        if (os eq null)
                            <i>{ "N/A" }</i>
                        else
                            os.map { o ⇒
                                <li>{ if (o eq null) "N/A" else o.mkString("{", ",", "}") }</li>
                            }.toList

                    val locals =
                        if (ls eq null)
                            <i>{ "N/A" }</i>
                        else
                            ls.toSeq.reverse.map { e ⇒
                                <li>{ if (e eq null) "N/A" else e.mkString("{", ",", "}") }</li>
                            }

                    val used = this.usedBy(i)
                    val usedBy = if (used eq null) "N/A" else used.mkString("{", ", ", "}")
                    <tr>
                        <td>{ i }<br/>{ instructions(i).toString(i) }</td>
                        <td>{ usedBy }</td>
                        <td><ul class="Stack">{ operands }</ul></td>
                        <td><ol start="0" class="registers">{ locals }</ol></td>
                    </tr>
                }

        <div>
            <h1>Unused</h1>
            { unused().mkString("", ", ", "") }
            <h1>Overview</h1>
            <table>
                <tr>
                    <th class="pc">PC</th>
                    <th class="pc">Used By</th>
                    <th class="stack">Stack</th>
                    <th class="registers">Locals</th>
                </tr>
                { perInstruction }
            </table>
        </div>
    }

    /**
     * Creates a multi-graph that represents the method's def-use information. I.e.,
     * in which way a certain value is used by other instructions and where the derived
     * values are then used by further instructions.
     * (Basically, we compute the data-dependence graph.)
     */
    def createDefUseGraph(code: Code): Set[DefaultMutableNode[ValueOrigin]] = {

        // 1. create set of all def sites
        var defSites: Set[ValueOrigin] = Set.empty
        defOps.iterator.filter(_ ne null).foreach { _.foreach { _.foreach { defSites += _ } } }
        for {
            defLocalsPerPC ← this.defLocals
            if defLocalsPerPC ne null
            defLocalsPerPCPerRegister ← defLocalsPerPC.toSeq
            if defLocalsPerPCPerRegister ne null
            valueOrigin ← defLocalsPerPCPerRegister
        } {
            defSites += valueOrigin
        }

        def instructionToString(vo: ValueOrigin): String = {
            if (vo <= VMLevelValuesOriginOffset)
                s"<exception thrown by\\linstruction: ${pcOfVMLevelValue(vo)}>"
            else if (vo < 0)
                s"<parameter: ${-vo - 1}>"
            else
                s"$vo: "+code.instructions(vo).toString(vo)
        }

        val unusedNode =
            new DefaultMutableNode(
                Int.MinValue: ValueOrigin, (_: ValueOrigin) ⇒ "<NONE>", Some("pink")
            )

        // 1. create nodes for all local vars (i.e., the corresponding instructions)
        var nodes: Map[ValueOrigin, DefaultMutableNode[ValueOrigin]] =
            defSites.map { defSite ⇒
                val color =
                    if (defSite <= VMLevelValuesOriginOffset)
                        Some("orange")
                    else if (defSite < 0)
                        Some("green")
                    else if (code.exceptionHandlers.exists { _.handlerPC == defSite })
                        Some("yellow")
                    else
                        None
                (
                    defSite,
                    new DefaultMutableNode[ValueOrigin](defSite, instructionToString _, color)
                )
            }.toMap

        // 2. create edges
        defSites foreach { lvar ⇒
            val thisNode = nodes(lvar)
            val usages = usedBy(lvar)
            if ((usages eq null) || usages.isEmpty)
                unusedNode.addChild(thisNode)
            else
                usages.foreach { usage ⇒
                    val usageNode = nodes.get(usage)
                    if (usageNode.isDefined)
                        usageNode.get.addChild(thisNode)
                    else {
                        val useNode = new DefaultMutableNode[ValueOrigin](usage, instructionToString _)
                        useNode.addChild(thisNode)
                        nodes += ((usage, useNode))
                    }
                }
        }

        nodes.values.toSet + unusedNode
    }

}

private object ComputationalTypeCategoryNotAvailable extends (Int ⇒ ComputationalTypeCategory) {

    def apply(i: Int): Nothing = throw new UnsupportedOperationException

}
