/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package bugpicker
package ui

import java.net.URL
import scala.io.Source
import scala.xml.{ Node ⇒ xmlNode }
import org.opalj.io.process
import org.opalj.ai.common.XHTML
import org.opalj.br.analyses.ProgressManagement
import org.opalj.br.analyses.Project
import org.opalj.bugpicker.core.analysis.AnalysisParameters
import org.opalj.bugpicker.core.analysis.Issue
import org.opalj.bugpicker.core.analysis.BugPickerAnalysis
import javafx.concurrent.{ Service ⇒ jService }
import javafx.concurrent.{ Task ⇒ jTask }
import scalafx.beans.property.ObjectProperty
import scalafx.concurrent.Service
import org.opalj.log.OPALLogger
import org.opalj.util.NanoSeconds
import org.opalj.bugpicker.core.analysis.BugPickerAnalysis.resultsAsXHTML

/**
 * @author Arne Lottmann
 * @author Michael Eichberg
 * @author David Becker
 */
class AnalysisWorker(
    doc: ObjectProperty[xmlNode],
    project: Project[URL],
    parameters: AnalysisParameters,
    initProgressManagement: Int ⇒ ProgressManagement) extends Service[Unit](new jService[Unit]() {

    protected def createTask(): jTask[Unit] = new jTask[Unit] {
        protected def call(): Unit = {
            val parametersAsString = parameters.toStringParameters
            val (analysisTime, issues, _) =
                AnalysisRunner.analyze(project, parametersAsString, initProgressManagement)
            doc() = createHTMLReport(analysisTime, parametersAsString, issues)
        }

        def createHTMLReport(
            analysisTime: NanoSeconds,
            parametersAsString: Seq[String],
            issues: Iterable[Issue]): scala.xml.Node = {
            val report = resultsAsXHTML(parametersAsString, issues, analysisTime)

            val additionalStyles = process(getClass.getResourceAsStream("report.ext.css")) {
                Source.fromInputStream(_).mkString
            }
            val stylesNode = <style type="text/css">{ scala.xml.Unparsed(additionalStyles) }</style>

            val newHead = <head>{ (report \ "head" \ "_") }{ stylesNode }</head>

            val assembledReport =
                new scala.xml.Elem(
                    report.prefix,
                    report.label,
                    report.attributes,
                    report.scope,
                    false,
                    (newHead ++ (report \ "body"): _*))

            OPALLogger.info(
                "analysis progress", "assembled the final report")(project.logContext)

            assembledReport
        }
    }
})
