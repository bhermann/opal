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

import scala.collection.BitSet
import scala.collection.immutable.IntMap
import org.opalj.collection.mutable.UShortSet
import org.opalj.collection.immutable.{Chain ⇒ List}

/**
 * Exception that is thrown by the abstract interpreter when the abstract
 * interpretation of a method's implementation failed.
 *
 * To create an instance use the companion object [[InterpretationFailedException$]].
 *
 * @param   pc The program counter of the instruction for which the interpretation failed.
 *          If `pc == code.length` then the interpretation completed successfully,
 *          but the post-processing failed.
 *
 * @author Michael Eichberg
 */
sealed trait InterpretationFailedException {
    def cause: Throwable
    val domain: Domain
    val ai: AI[_ >: domain.type]
    val pc: PC
    val worklist: List[PC]
    val evaluated: List[PC]
    val cfJoins: BitSet
    val cfForks: BitSet
    val remainingCFForkTargets: IntMap[UShortSet]
    val operandsArray: domain.OperandsArray
    val localsArray: domain.LocalsArray
    val memoryLayoutBeforeSubroutineCall: List[(PC, domain.OperandsArray, domain.LocalsArray)]
}

/**
 * Factory for [[InterpretationFailedException]]s.
 *
 * @author Michael Eichberg
 */
object InterpretationFailedException {

    def apply(
        theCause:  Throwable,
        theDomain: Domain
    )(
        theAI:                               AI[_ >: theDomain.type],
        thePc:                               PC,
        theCFJoins:                          BitSet,
        theCFForks:                          BitSet,
        theWorklist:                         List[PC],
        theEvaluated:                        List[PC],
        theRemainingCFForkTargets:           IntMap[UShortSet],
        theOperandsArray:                    theDomain.OperandsArray,
        theLocalsArray:                      theDomain.LocalsArray,
        theMemoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)]
    ): AIException with InterpretationFailedException = {

        new AIException("the interpretation failed", theCause) with InterpretationFailedException {
            def cause = super.getCause
            val ai: AI[_ >: theDomain.type] = theAI
            val domain: theDomain.type = theDomain
            val pc: PC = thePc
            val cfJoins: BitSet = theCFJoins
            val cfForks: BitSet = theCFForks

            val worklist: List[PC] = theWorklist
            val evaluated: List[PC] = theEvaluated
            val remainingCFForkTargets: IntMap[UShortSet] = theRemainingCFForkTargets

            val operandsArray: theDomain.OperandsArray = theOperandsArray
            val localsArray: theDomain.LocalsArray = theLocalsArray
            val memoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)] = theMemoryLayoutBeforeSubroutineCall
        }

    }
}
