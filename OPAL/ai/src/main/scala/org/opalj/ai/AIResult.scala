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

import scala.collection.mutable
import scala.collection.BitSet
import org.opalj.collection.immutable.{Chain ⇒ List}
import org.opalj.collection.immutable.{Naught ⇒ Nil}
import org.opalj.collection.UShortSet
import org.opalj.br.Code

/**
 * Encapsulates the ''result'' of the abstract interpretation of a method. If
 * the abstract interpretation was cancelled, the result encapsulates the current
 * state of the evaluation which can be used to continue
 * the abstract interpretation later on if necessary/desired.
 *
 * @author Michael Eichberg
 */
sealed abstract class AIResult {

    /**
     * If `true` then the code was evaluated using `strict` semantics (in Java
     * the `strictfp` modifier was used), otherwise
     * it was evaluated using the standard JVM semantics.
     *
     * Note, that most standard JVMs nowadays always use strict semantics.
     */
    val strictfp: Boolean

    /**
     * The code for which the abstract interpretation was performed.
     */
    val code: Code

    /**
     * The instructions where two or more control flow paths join.
     *
     * (See also [[org.opalj.br.Code.joinInstructions]].)
     *
     * @note This information could be recomputed on-demand but is stored for performance
     * reasons.
     */
    val joinInstructions: BitSet

    /**
     * The domain object that was used to perform the abstract interpretation.
     */
    val domain: Domain

    /**
     * The list of instructions that need to be interpreted next. This list is empty
     * if the abstract interpretation succeed.
     */
    val worklist: List[PC]

    /**
     * The list of evaluated instructions ordered by the evaluation time.
     */
    val evaluated: List[PC]

    /**
     * Returns the information whether an instruction with a specific PC was evaluated
     * at least once.
     */
    lazy val evaluatedInstructions: BitSet = {
        val evaluatedInstructions = new mutable.BitSet(code.instructions.size)
        evaluated.foreach(pc ⇒ if (pc >= 0) evaluatedInstructions += pc)
        evaluatedInstructions
    }

    /**
     * Returns all instructions that belong to a subroutine.
     */
    lazy val subroutineInstructions: UShortSet = {
        var instructions = org.opalj.collection.mutable.UShortSet.empty
        var subroutineLevel = 0
        // It is possible to have a method with just JSRs and no RETs...
        // Hence, we have to iterate from the beginning.
        evaluated.reverse.foreach { pc ⇒
            (pc: @scala.annotation.switch) match {
                case SUBROUTINE_START          ⇒ subroutineLevel += 1
                case SUBROUTINE_END            ⇒ subroutineLevel -= 1
                case pc if subroutineLevel > 0 ⇒ instructions = pc +≈: instructions
                case _                         ⇒ // we don't care
            }
        }
        instructions
    }

    /**
     * The array of the operand lists in effect before the execution of the
     * instruction with the respective program counter.
     *
     * For those instructions that were never executed (potentially dead code if the
     * abstract interpretation succeeded) the operands array will be empty (the
     * value will be `null`).
     */
    val operandsArray: domain.OperandsArray

    /**
     * Returns true if the instruction with the given pc was evaluated at least once.
     */
    @inline final def wasEvaluted(pc: PC): Boolean = operandsArray(pc) ne null

    /**
     * The values stored in the registers.
     *
     * For those instructions that were never executed (potentially dead code if the
     * abstract interpretation succeeded) the locals array will be empty (the
     * value will be `null`).
     */
    val localsArray: domain.LocalsArray

    /**
     * Contains the memory layout before the call to a subroutine. This list is
     * empty if the abstract interpretation completed successfully.
     */
    val memoryLayoutBeforeSubroutineCall: List[(PC, domain.OperandsArray, domain.LocalsArray)]

    /**
     * Contains the memory layout related to the method's subroutines (if any).
     *
     * @note '''This value is `null`''' if the method does not have subroutines (Java 6 and newer
     *      class files never contain subroutines) or if no subroutine was analyzed so far.
     */
    val subroutinesOperandsArray: domain.OperandsArray

    /**
     * Contains the memory layout related to the method's subroutines (if any).
     *
     * @note '''This value is `null`''' if the method does not have subroutines (Java 6 and newer
     *      class files never contain subroutines) or if no subroutine was analyzed so far.
     */
    val subroutinesLocalsArray: domain.LocalsArray

    /**
     * Returns `true` if the abstract interpretation was aborted.
     */
    def wasAborted: Boolean

    /**
     * Textual representation of the state encapsulated by this result.
     */
    def stateToString: String = {
        var result = ""
        result += evaluated.mkString("Evaluated: ", ",", "\n")
        result += (
            if (worklist.nonEmpty) worklist.mkString("Remaining Worklist: ", ",", "\n")
            else "Worklist: empty\n"
        )
        if (memoryLayoutBeforeSubroutineCall.nonEmpty) {
            for ((subroutineId, operandsArray, localsArray) ← memoryLayoutBeforeSubroutineCall) {
                result += s"Memory Layout Before Calling Subroutine $subroutineId:\n"
                result += memoryLayoutToText(domain)(operandsArray, localsArray)
            }
        }
        result += "Current Memory Layout:\n"
        result += memoryLayoutToText(domain)(operandsArray, localsArray)

        result
    }
}

/**
 * Encapsulates the intermediate result of an aborted abstract interpretation of a method.
 *
 * @author Michael Eichberg
 */
sealed abstract class AIAborted extends AIResult {

    override def wasAborted: Boolean = true

    def continueInterpretation(ai: AI[_ >: domain.type]): AIResult

    override def stateToString: String =
        "The abstract interpretation was aborted:\n"+super.stateToString
}

object AICompleted {

    def unapply(
        result: AICompleted
    ): Some[(result.domain.type, result.operandsArray.type, result.localsArray.type)] =
        Some((result.domain, result.operandsArray, result.localsArray))

}

/**
 * Encapsulates the final result of the successful abstract interpretation of a method.
 */
sealed abstract class AICompleted extends AIResult {

    override val worklist: List[PC] = List.empty

    override def wasAborted: Boolean = false

    def restartInterpretation(ai: AI[_ >: domain.type]): AIResult

    override def stateToString: String =
        "The abstract interpretation succeeded:\n"+super.stateToString
}

/**
 * Factory to create `AIResult` objects. Primarily used to return the
 * result of an abstract interpretation of a method.
 *
 * @author Michael Eichberg
 */
/* Design - We need to use a builder to construct a Result object in two steps.
 * This is necessary to correctly type the data structures that store the memory
 * layout and which depend on the given domain. */
object AIResultBuilder {

    /**
     * Creates a domain dependent [[AIAborted]] object which stores the results of the
     * computation.
     */
    def aborted(
        theStrictfp:         Boolean,
        theCode:             Code,
        theJoinInstructions: BitSet,
        theDomain:           Domain
    )(
        theWorklist:                         List[PC],
        theEvaluated:                        List[PC],
        theOperandsArray:                    theDomain.OperandsArray,
        theLocalsArray:                      theDomain.LocalsArray,
        theMemoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)],
        theSubroutinesOperandsArray:         theDomain.OperandsArray,
        theSubroutinesLocalsArray:           theDomain.LocalsArray
    ): AIAborted { val domain: theDomain.type } = {

        new AIAborted {
            val strictfp: Boolean = theStrictfp
            val code: Code = theCode
            val joinInstructions: BitSet = theJoinInstructions
            val domain: theDomain.type = theDomain
            val worklist: List[PC] = theWorklist
            val evaluated: List[PC] = theEvaluated
            val operandsArray: theDomain.OperandsArray = theOperandsArray
            val localsArray: theDomain.LocalsArray = theLocalsArray
            val memoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)] = theMemoryLayoutBeforeSubroutineCall
            val subroutinesOperandsArray: theDomain.OperandsArray = theSubroutinesOperandsArray
            val subroutinesLocalsArray: theDomain.LocalsArray = theSubroutinesLocalsArray

            def continueInterpretation(ai: AI[_ >: domain.type]): AIResult =
                ai.continueInterpretation(
                    strictfp, code, joinInstructions, domain
                )(
                    worklist, evaluated,
                    operandsArray, localsArray,
                    memoryLayoutBeforeSubroutineCall, subroutinesOperandsArray, subroutinesLocalsArray
                )

        }
    }

    /**
     * Creates a domain dependent [[AICompleted]] object which stores the results of the
     * completed abstract interpretation of the given code. The precise meaning of
     * ''completed'' is depending on the used domain.
     */
    def completed(
        theStrictfp:         Boolean,
        theCode:             Code,
        theJoinInstructions: BitSet,
        theDomain:           Domain
    )(
        theEvaluated:     List[PC],
        theOperandsArray: theDomain.OperandsArray,
        theLocalsArray:   theDomain.LocalsArray
    ): AICompleted { val domain: theDomain.type } = {

        new AICompleted {
            val strictfp: Boolean = theStrictfp
            val code: Code = theCode
            val joinInstructions = theJoinInstructions
            val domain: theDomain.type = theDomain
            val evaluated: List[PC] = theEvaluated
            val operandsArray: theDomain.OperandsArray = theOperandsArray
            val localsArray: theDomain.LocalsArray = theLocalsArray
            val memoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)] = Nil
            val subroutinesOperandsArray: theDomain.OperandsArray = null
            val subroutinesLocalsArray: theDomain.LocalsArray = null

            def restartInterpretation(ai: AI[_ >: theDomain.type]): AIResult = {

                // We have to extract the information about the subroutines... if we have any...
                var subroutinePCs = UShortSet.empty
                var subroutineCount = 0
                var evaluated = theEvaluated
                while (evaluated.nonEmpty) {
                    evaluated.head match {
                        case SUBROUTINE_START ⇒ subroutineCount += 1
                        case SUBROUTINE_END   ⇒ subroutineCount -= 1
                        case pc ⇒
                            if (subroutineCount > 0) subroutinePCs = pc +≈: subroutinePCs
                    }
                    evaluated = evaluated.tail
                }

                // make sure that we don't change "this result"
                val operandsArray = this.operandsArray.clone()
                val localsArray = this.localsArray.clone()
                var subroutinesOperandsArray: domain.OperandsArray = null
                var subroutinesLocalsArray: domain.LocalsArray = null

                if (subroutinePCs.nonEmpty) {
                    val codeSize = code.instructions.size
                    subroutinesOperandsArray = new Array(codeSize)
                    subroutinesLocalsArray = new Array(codeSize)
                    subroutinePCs.foreach { pc ⇒
                        subroutinesOperandsArray(pc) = operandsArray(pc)
                        operandsArray(pc) = null
                        subroutinesLocalsArray(pc) = localsArray(pc)
                        localsArray(pc) = null
                    }
                }

                ai.continueInterpretation(
                    strictfp, code, joinInstructions, domain
                )(
                    AI.initialWorkList, evaluated,
                    operandsArray, localsArray,
                    Nil, subroutinesOperandsArray, subroutinesLocalsArray
                )

            }
        }
    }
}
