/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
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
package cg
package cha

import java.net.URL
import org.opalj.br.analyses.{BasicReport, DefaultOneStepAnalysis, Project, SourceElementsPropertyStoreKey}
import org.opalj.br.analyses.AnalysisModeConfigFactory
import org.opalj.log.OPALLogger
import org.opalj.log.ConsoleOPALLogger
import org.opalj.log.Warn

object OpaCHADemo extends DefaultOneStepAnalysis {

    override def title: String = "Test stuff."

    override def description: String =
        ""

    override def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {
        val opaProject = AnalysisModeConfigFactory.resetAnalysisMode(project, AnalysisModes.OPA)
        val opaStore = opaProject.get(SourceElementsPropertyStoreKey)

        OPALLogger.updateLogger(opaProject.logContext, new ConsoleOPALLogger(true, Warn))

        val methodsCount: Double = project.methodsCount.toDouble
        def getPercentage(value: Int): String = "%1.2f" format (value.toDouble / methodsCount * 100d)

        // CALL GRAPH STUFF
        val ccg = opaProject.get(CHACallGraphKey)
        val exceptions = ccg.constructionExceptions.map(_.toFullString).mkString("Construction Exception\n\n", "\n", "\n")
        println(exceptions)
        val newOpaCG = ccg.callGraph
        // CALL GRAPH STUFF

        val opaEP = opaStore.entities { (p: Property) ⇒
            p == IsEntryPoint
        }

        BasicReport(
            s"#methods:  ${methodsCount}\n"+
                s"#entry points: ${opaEP.size}\n"+
                s"percentage   : ${getPercentage(opaEP.size)}\n"+
                s"#call edges  : ${newOpaCG.callEdgesCount}"
        )
    }
}
