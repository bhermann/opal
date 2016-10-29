/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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

import java.net.URL
import org.opalj.br.analyses.{DefaultOneStepAnalysis, BasicReport, Project}

/**
 * Computes some statistics related to the number of parameters and locals
 * (Local Variables/Registers) defined/specified by each method.
 *
 * @author Michael Eichberg
 */
object MaxLocalsEvaluation extends DefaultOneStepAnalysis {

    override def description: String = {
        "Collects information about the maxium number of registers required per method."
    }

    def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ) = {

        import scala.collection.immutable.TreeMap // <= Sorted...
        var methodParametersDistribution: Map[Int, Int] = TreeMap.empty
        var maxLocalsDistrbution: Map[Int, Int] = TreeMap.empty

        for {
            classFile ← project.allProjectClassFiles
            method @ MethodWithBody(body) ← classFile.methods
            descriptor = method.descriptor
        } {
            val parametersCount = descriptor.parametersCount + (if (method.isStatic) 0 else 1)

            val methodParametersFrequency = methodParametersDistribution.getOrElse(parametersCount, 0) + 1
            methodParametersDistribution =
                methodParametersDistribution.updated(parametersCount, methodParametersFrequency)

            val newMaxLocalsCount = maxLocalsDistrbution.getOrElse(body.maxLocals, 0) + 1
            maxLocalsDistrbution = maxLocalsDistrbution.updated(body.maxLocals, newMaxLocalsCount)
        }

        BasicReport("Results\n\n"+
            "Method Parameters Distribution:\n"+
            "#Parameters\tFrequency:\n"+
            methodParametersDistribution.map(kv ⇒ { val (k, v) = kv; k+"\t\t"+v }).mkString("\n")+"\n\n"+
            "MaxLocals Distribution:\n"+
            "#Locals\t\tFrequency:\n"+
            maxLocalsDistrbution.map(kv ⇒ { val (k, v) = kv; k+"\t\t"+v }).mkString("\n"))
    }
}
