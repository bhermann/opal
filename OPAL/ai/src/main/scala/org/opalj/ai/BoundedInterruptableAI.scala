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

import org.opalj.br.Code
import org.opalj.log.LogContext
import org.opalj.util.Nanoseconds
import org.opalj.util.Milliseconds

/**
 * An abstract interpreter that interrupts itself after the evaluation of
 * the given number of instructions or if the callback function `doInterrupt` returns
 * `false` or if the maximum allowed time is exceeded.
 *
 * @param maxEvaluationCount See [[InstructionCountBoundedAI.maxEvaluationCount]].
 *
 * @param maxEvaluationTime The maximum number of nanoseconds the abstract interpreter
 *      is allowed to run. It starts with the evaluation of the first instruction.
 *
 * @param doInterrupt This function is called by the abstract interpreter to check if
 *      the abstract interpretation should be aborted. Given that this function is called
 *      very often (before the evaluation of each instruction), it is important that it
 *      is efficient.
 *
 * @author Michael Eichberg
 */
class BoundedInterruptableAI[D <: Domain](
        maxEvaluationCount:    Int,
        val maxEvaluationTime: Nanoseconds,
        val doInterrupt:       () ⇒ Boolean,
        IdentifyDeadVariables: Boolean
) extends InstructionCountBoundedAI[D](maxEvaluationCount, IdentifyDeadVariables) {

    private[this] var startTime: Long = -1L;

    def this(
        code:                  Code,
        maxEvaluationFactor:   Double,
        maxEvaluationTime:     Milliseconds,
        doInterrupt:           () ⇒ Boolean,
        identifyDeadVariables: Boolean      = true
    )(
        implicit
        logContext: LogContext
    ) = {
        this(
            InstructionCountBoundedAI.calculateMaxEvaluationCount(code, maxEvaluationFactor),
            maxEvaluationTime.toNanoseconds,
            doInterrupt,
            identifyDeadVariables
        )
    }

    override def isInterrupted: Boolean = {
        if (super.isInterrupted || doInterrupt())
            return true;

        val startTime = this.startTime
        if (startTime == -1L) {
            this.startTime = System.nanoTime()
            false
        } else if (super.currentEvaluationCount % 1000 == 0) {
            val elapsedTime = System.nanoTime() - startTime
            elapsedTime > maxEvaluationTime.timeSpan
        } else
            false
    }

}
