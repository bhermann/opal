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
package fpcf
package analysis

import java.net.URL

import org.opalj.util.PerformanceEvaluation.time
import org.opalj.util.Seconds
import org.opalj.br.analyses.DefaultOneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.SourceElementsPropertyStoreKey
import org.opalj.br.ClassFile
import org.opalj.fpcf.properties.SelfReferenceLeakage
import org.opalj.fpcf.properties.DoesNotLeakSelfReference

/**
 * Runs the default escape analysis.
 *
 * @author Michael Eichberg
 */
object EscapeAnalysisDemo extends DefaultOneStepAnalysis {

    override def title: String = "shallow escape analysis"

    override def description: String = {
        "determins escape information related to objects belonging to a specific class"
    }

    def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {

        val projectStore = project.get(SourceElementsPropertyStoreKey)

        var analysisTime = Seconds.None
        time {
            EscapeAnalysis.analyze(project)
            projectStore.waitOnPropertyComputationCompletion( /*default: true*/ )
        } { t ⇒ analysisTime = t.toSeconds }

        val notLeakingEntities: Traversable[EP[Entity, SelfReferenceLeakage]] =
            projectStore.entities(SelfReferenceLeakage.Key) filter { ep ⇒
                ep.p == DoesNotLeakSelfReference
            }
        val notLeakingClasses = notLeakingEntities.map { ep ⇒
            val classFile = ep.e.asInstanceOf[ClassFile]
            val classType = classFile.thisType
            val className = classFile.thisType.toJava
            if (project.classHierarchy.isInterface(classType).isYes)
                "interface "+className
            else
                "class "+className
        }

        val leakageInfo =
            notLeakingClasses.toList.sorted.mkString(
                "\nClasses not leaking self reference:\n",
                "\n",
                s"\nTotal: ${notLeakingEntities.size}\n"
            )
        BasicReport(leakageInfo + projectStore+"\nAnalysis time: "+analysisTime)
    }
}
