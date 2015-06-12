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
package core

import java.net.URL
import scala.collection.SortedMap
import scala.xml.Node
import org.opalj.io.writeAndOpen
import org.opalj.io.process
import org.opalj.br.analyses.{ Analysis, AnalysisExecutor, BasicReport, Project }
import org.opalj.br.analyses.ProgressManagement
import org.opalj.ai.common.XHTML
import org.opalj.bugpicker.core.analysis.IssueKind
import org.opalj.bugpicker.core.analysis.BugPickerAnalysis
import org.opalj.bugpicker.core.analysis.BugPickerAnalysis.resultsAsXHTML
import org.opalj.log.OPALLogger
import java.lang.Integer.parseInt
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.io.File
import org.opalj.log.OPALLogger
import org.opalj.log.GlobalLogContext

/**
 * A simple wrapper around the BugPicker analysis to make it runnable using the
 * command line.
 *
 * @author Michael Eichberg
 */
object Console extends Analysis[URL, BasicReport] with AnalysisExecutor {
    val analysis = this

    final val HTMLFileOutputNameMatcher = """-html=([\w-_\.\:/\\]+)""".r

    final val DebugFileOutputNameMatcher = """-debug=([\w-_\.\:/\\]+)""".r

    final val MinRelevancePattern = """-minRelevance=(\d\d?)""".r
    final val MinRelevance = 0

    final val IssueKindsPattern = """-kinds=([\w, ]+)""".r

    final override val analysisSpecificParametersDescription: String =
        """[-maxEvalFactor=<DoubleValue {[0.1,100),Infinity}=1.75> determines the maximum effort that
            |               the analysis will spend when analyzing a specific method. The effort is
            |               always relative to the size of the method. For the vast majority of methods
            |               a value between 0.5 and 1.5 is sufficient to completely analyze a single
            |               method using the default settings.
            |               A value greater than 1.5 can already lead to very long evaluation times.
            |               If the threshold is exceeded the analysis of the method is aborted and no
            |               result can be drawn.]
            |[-maxEvalTime=<IntValue [10,1000000]=10000> determines the time (in ms) that the analysis
            |               is allowed to take for one method before the analysis is terminated.]
            |[-maxCardinalityOfIntegerRanges=<LongValue [1,4294967295]=16> basically determines for each integer
            |               value how long the value is "precisely" tracked. Internally the analysis
            |               computes the range of values that an integer value may have at runtime. The
            |               maximum size/cardinality of this range is controlled by this setting. If
            |               the range is exceeded the precise tracking of the respective value is
            |               terminated.
            |               Increasing this value may significantly increase the analysis time and
            |               may require the increase of maxEvalFactor.]
            |[-maxCardinalityOfLongSets=<IntValue [1,1024]=2> basically determines for each long
            |               value how long the value is "precisely" tracked.
            |               The maximum size/cardinality of this set is controlled by this setting. If
            |               the set's size is tool large the precise tracking of the respective value is
            |               terminated.
            |               Increasing this value may significantly increase the analysis time and
            |               may require the increase of maxEvalFactor.]            |
            |[-maxCallChainLength=<IntValue [0..9]=1> determines the maximum length of the call chain
            |               that is analyzed.
            |               If you increase this value by one it is typically also necessary
            |               to also increase the maxEvalFactor by a factor of 2 to 3. Otherwise it
            |               may happen that many analyses are aborted because the evaluation time
            |               is exhausted and – overall – the analysis reports less issues!]
            |[-minRelevance=<IntValue [0..99]=0> the minimum relevance of the shown issues.
            |[-kinds=<Issue Kinds="constant computation,dead path,throws exception,
            |                unguarded use,unused">] a comma seperated list of issue kinds
            |                that should be reported
            |[-eclipse      creates an eclipse console compatible output).]
            |[-html[=<FileName>] generates an HTML report which is written to the optionally
            |               specified location.]
            |[-debug[=<FileName>] turns on the debug mode (more information are logged and
            |               internal, recoverable exceptions are logged) the report is optionally
            |               written to the specified location.]""".stripMargin('|')

    private final val bugPickerAnalysis = new BugPickerAnalysis

    override def title: String = bugPickerAnalysis.title

    override def description: String = bugPickerAnalysis.description

    override def analyze(
        theProject: Project[URL],
        parameters: Seq[String],
        initProgressManagement: (Int) ⇒ ProgressManagement) = {

        import theProject.logContext

        OPALLogger.info("analysis progress", "starting analysis")

        val (analysisTime, issues0, exceptions) =
            bugPickerAnalysis.analyze(theProject, parameters, initProgressManagement)

        //
        // PREPARE THE GENERATION OF THE REPORT OF THE FOUND ISSUES
        // (HTML/Eclipse)
        //

        // Filter the report
        val minRelevance: Int =
            parameters.
                collectFirst { case MinRelevancePattern(i) ⇒ parseInt(i) }.
                getOrElse(MinRelevance)
        val issues1 = issues0.filter { i ⇒ i.relevance.value >= minRelevance }

        val issues = parameters.collectFirst { case IssueKindsPattern(ks) ⇒ ks } match {
            case Some(ks) ⇒
                val relevantKinds = ks.split(',').toSet
                issues1.filter(issue ⇒ (issue.kind intersect (relevantKinds)).nonEmpty)
            case None ⇒
                issues1
        }

        // Generate the report well suited for the eclipse console
        //
        if (parameters.contains("-eclipse")) {
            val formattedIssues = issues.map { issue ⇒ issue.asEclipseConsoleString }
            println(formattedIssues.toSeq.sorted.mkString("\n"))
        }

        // Generate the HTML report
        //
        lazy val htmlReport = resultsAsXHTML(parameters, issues, showSearch = false, analysisTime).toString
        parameters.collectFirst { case HTMLFileOutputNameMatcher(name) ⇒ name } match {
            case Some(fileName) ⇒
                val file = new File(fileName).toPath
                process { Files.newBufferedWriter(file, StandardCharsets.UTF_8) } { fos ⇒
                    fos.write(htmlReport, 0, htmlReport.length)
                }
            case _ ⇒ // Nothing to do
        }
        if (parameters.contains("-html")) {
            writeAndOpen(htmlReport, "BugPickerAnalysisResults", ".html")
        }

        //
        // PREPARE THE GENERATION OF THE REPORT OF THE OCCURED EXCEPTIONS
        //
        if (exceptions.nonEmpty) {
            OPALLogger.error(
                "internal error",
                s"the analysis threw ${exceptions.size} exceptions")
            exceptions.foreach { e ⇒
                OPALLogger.error(
                    "internal error", "the analysis failed", e)
            }

            var exceptionsReport: Node = null
            def getExceptionsReport = {
                if (exceptionsReport eq null) {
                    val exceptionNodes =
                        exceptions.take(10).map { e ⇒
                            <p>{ XHTML.throwableToXHTML(e) }</p>
                        }
                    exceptionsReport =
                        XHTML.createXHTML(
                            Some(s"${exceptions.size}/${exceptionNodes.size} Thrown Exceptions"),
                            <div>{ exceptionNodes }</div>
                        )
                }
                exceptionsReport
            }
            parameters.collectFirst { case DebugFileOutputNameMatcher(name) ⇒ name } match {
                case Some(fileName) ⇒
                    process { new java.io.FileOutputStream(fileName) } { fos ⇒
                        fos.write(getExceptionsReport.toString.getBytes("UTF-8"))
                    }
                case _ ⇒ // Nothing to do
            }
            if (parameters.contains("-debug")) {
                org.opalj.io.writeAndOpen(getExceptionsReport, "Exceptions", ".html")
            }
        }

        //
        // Print some statistics and "return"
        //
        val groupedIssues =
            issues.groupBy(_.relevance).toList.
                sortWith((e1, e2) ⇒ e1._1.value < e2._1.value)
        val groupedAndCountedIssues = groupedIssues.map(e ⇒ e._1+": "+e._2.size)

        BasicReport(
            groupedAndCountedIssues.mkString(
                s"Issues (∑${issues.size}):\n\t",
                "\n\t",
                s"\nIdentified in: ${analysisTime.toSeconds}.\n")
        )
    }

    override def checkAnalysisSpecificParameters(parameters: Seq[String]): Seq[String] = {
        var outputFormatGiven = false

        import org.opalj.bugpicker.core.analysis.BugPickerAnalysis._

        val issues =
            parameters.filterNot(parameter ⇒ parameter match {
                case MaxEvalFactorPattern(d) ⇒
                    try {
                        val factor = java.lang.Double.parseDouble(d).toDouble
                        (factor >= 0.1d && factor < 100.0d) ||
                            factor == Double.PositiveInfinity
                    } catch {
                        case nfe: NumberFormatException ⇒ false
                    }
                case MaxEvalTimePattern(l) ⇒
                    try {
                        val maxTime = java.lang.Long.parseLong(l).toLong
                        maxTime >= 10 && maxTime <= 1000000
                    } catch {
                        case nfe: NumberFormatException ⇒ false
                    }
                case MaxCardinalityOfIntegerRangesPattern(i) ⇒
                    try {
                        val cardinality = java.lang.Long.parseLong(i).toLong
                        cardinality >= 1 && cardinality <= 4294967295l
                    } catch {
                        case nfe: NumberFormatException ⇒ false
                    }
                case MaxCardinalityOfLongSetsPattern(i) ⇒
                    try {
                        val cardinality = java.lang.Integer.parseInt(i).toInt
                        cardinality >= 1 && cardinality <= 1024
                    } catch {
                        case nfe: NumberFormatException ⇒ false
                    }
                case MaxCallChainLengthPattern(_) ⇒
                    // the pattern ensures that the value is legal...
                    true

                case IssueKindsPattern(ks) ⇒
                    val kinds = ks.split(',')
                    kinds.nonEmpty && kinds.forall { IssueKind.AllKinds.contains(_) }

                case MinRelevancePattern(_) ⇒
                    // the pattern ensures that the value is legal...
                    true

                case HTMLFileOutputNameMatcher(_) ⇒
                    outputFormatGiven = true; true
                case "-html" ⇒
                    outputFormatGiven = true; true
                case "-eclipse" ⇒
                    outputFormatGiven = true; true
                case "-debug"                      ⇒ true
                case DebugFileOutputNameMatcher(_) ⇒ true
                case _                             ⇒ false
            })

        if (!outputFormatGiven)
            OPALLogger.warn("analysis configuration", "no output format specified")(GlobalLogContext)

        issues.map("unknown or illegal parameter: "+_)
    }
}

