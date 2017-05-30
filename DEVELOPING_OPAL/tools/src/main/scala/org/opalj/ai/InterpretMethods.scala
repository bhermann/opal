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
package debug

import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import scala.util.control.ControlThrowable

import org.opalj.log.LogContext
import org.opalj.br._
import org.opalj.br.analyses._
import org.opalj.ai.util.XHTML
import org.opalj.io.writeAndOpen

/**
 * Performs an abstract interpretation of all methods of the given class file(s) using
 * a configurable domain.
 *
 * This class is meant to support the development and testing of new domains.
 *
 * @author Michael Eichberg
 */
object InterpretMethods extends AnalysisExecutor {

    override def analysisSpecificParametersDescription: String =
        "[-domain=<Class of the domain that should be used for the abstract interpretation>]\n"+
            "[-verbose={true,false} If true, extensive information is shown.]\n"

    override def checkAnalysisSpecificParameters(parameters: Seq[String]): Traversable[String] = {
        def isDomainParameter(parameter: String) =
            parameter.startsWith("-domain=") && parameter.length() > 8
        def isVerbose(parameter: String) =
            parameter == "-verbose=true" || parameter == "-verbose=false"

        parameters match {
            case Nil ⇒ Traversable.empty
            case Seq(parameter) ⇒
                if (isDomainParameter(parameter) || isVerbose(parameter))
                    Traversable.empty
                else
                    Traversable("unknown parameter: "+parameter)
            case Seq(parameter1, parameter2) ⇒
                if (!isDomainParameter(parameter1))
                    Seq("the first parameter does not specify the domain: "+parameter1)
                else if (!isVerbose(parameter2))
                    Seq("the second parameter has to be \"verbose\": "+parameter2)
                else
                    Traversable.empty

        }
    }

    override val analysis = new InterpretMethodsAnalysis[URL]

}

/**
 * An analysis that analyzes all methods of all class files of a project using a
 * custom domain.
 *
 * @author Michael Eichberg
 */
class InterpretMethodsAnalysis[Source] extends Analysis[Source, BasicReport] {

    override def title: String = "interpret methods"

    override def description: String = "performs an abstract interpretation of all methods"

    override def analyze(
        project:                Project[Source],
        parameters:             Seq[String]                = List.empty,
        initProgressManagement: (Int) ⇒ ProgressManagement
    ): BasicReport = {
        implicit val logContext = project.logContext

        val verbose = parameters.size > 0 &&
            (parameters(0) == "-verbose=true" ||
                (parameters.size == 2 && parameters.tail.head == "-verbose=true"))
        val (message, detailedErrorInformationFile) =
            if (parameters.size > 0 && parameters(0).startsWith("-domain")) {
                InterpretMethodsAnalysis.interpret(
                    project,
                    Class.forName(parameters.head.substring(8)).asInstanceOf[Class[_ <: Domain]],
                    verbose,
                    initProgressManagement,
                    6d
                )
            } else {
                InterpretMethodsAnalysis.interpret(
                    project,
                    classOf[domain.l0.BaseDomain[java.net.URL]],
                    verbose,
                    initProgressManagement,
                    6d
                )

            }
        BasicReport(
            message + detailedErrorInformationFile.map(" (See "+_+" for details.)").getOrElse("")
        )
    }
}

object InterpretMethodsAnalysis {

    def println(s: String): Unit = {
        Console.out.println(s)
        Console.flush()
    }

    def interpret[Source](
        project:                Project[Source],
        domainClass:            Class[_ <: Domain],
        beVerbose:              Boolean,
        initProgressManagement: (Int) ⇒ ProgressManagement,
        maxEvaluationFactor:    Double                     = 3d
    )(
        implicit
        logContext: LogContext
    ): (String, Option[File]) = {

        // TODO Add support for reporting the progress and to interrupt the analysis.

        import Console.{RED, YELLOW, GREEN, RESET}

        val performanceEvaluationContext = new org.opalj.util.PerformanceEvaluation
        import performanceEvaluationContext.{time, getTime}
        val methodsCount = new AtomicInteger(0)
        val instructionEvaluationsCount = new AtomicLong(0)

        val domainConstructor =
            domainClass.getConstructor(classOf[Project[URL]], classOf[ClassFile], classOf[Method])

        def analyzeMethod(
            source:    String,
            classFile: ClassFile,
            method:    Method
        ): Option[(String, ClassFile, Method, Throwable)] = {

            val body = method.body.get
            try {
                if (beVerbose) println(method.toJava(classFile, YELLOW+"[started]"+RESET))

                val evaluatedCount = time('AI) {
                    val ai = new InstructionCountBoundedAI[Domain](body, maxEvaluationFactor, true)
                    val domain = domainConstructor.newInstance(project, classFile, method)
                    val result = ai(classFile, method, domain)
                    if (result.wasAborted) {
                        if (beVerbose)
                            println(
                                classFile.thisType.toJava+"{ "+method.toJava +
                                    RED+"[aborted after evaluating "+
                                    ai.currentEvaluationCount+
                                    " instructions (size of instructions array="+
                                    body.instructions.size+
                                    "; max="+ai.maxEvaluationCount+")]"+
                                    RESET+" }"
                            )

                        val message = s"evaluation bound (max=${ai.maxEvaluationCount}) exceeded"
                        throw new InterruptedException(message)
                    }
                    val evaluatedCount = ai.currentEvaluationCount.toLong
                    instructionEvaluationsCount.addAndGet(evaluatedCount)
                    evaluatedCount
                }
                val naiveEvaluatedCount = time('NAIVE_AI) {
                    val ai = new InstructionCountBoundedAI[Domain](body, maxEvaluationFactor, false)
                    val domain = domainConstructor.newInstance(project, classFile, method)
                    ai(classFile, method, domain)
                    ai.currentEvaluationCount
                }

                if (naiveEvaluatedCount > evaluatedCount) {
                    val codeLength = body.instructions.length
                    val message = method.toJava(
                        classFile,
                        s"Evaluation steps (code length:$codeLength): "+
                            s"${naiveEvaluatedCount} (w/o dead variables analysis) vs. $evaluatedCount"
                    )
                    println(message)
                }

                if (beVerbose) println(method.toJava(classFile, GREEN+"[finished]"+RESET))
                methodsCount.incrementAndGet()
                None
            } catch {
                case ct: ControlThrowable ⇒ throw ct
                case t: Throwable ⇒
                    // basically, we want to catch everything!
                    val source = project.source(classFile.thisType).get.toString
                    Some((source, classFile, method, t))
            }
        }

        val collectedExceptions = time('OVERALL) {
            val results = new ConcurrentLinkedQueue[(String, ClassFile, Method, Throwable)]()
            project.parForeachMethodWithBody() { m ⇒
                val MethodInfo(source, classFile, method) = m
                analyzeMethod(source.toString, classFile, method).map(results.add(_))
            }
            import scala.collection.JavaConverters._
            results.asScala
        }

        if (collectedExceptions.nonEmpty) {
            val header = <p>Generated { new java.util.Date() }</p>

            val body = Seq(header) ++
                (for ((exResource, exInstances) ← collectedExceptions.groupBy(e ⇒ e._1)) yield {
                    val exDetails =
                        exInstances.map { ex ⇒
                            val (_, classFile, method, throwable) = ex
                            <div>
                                <b>{ classFile.thisType.fqn }</b>
                                <i>"{ method.toJava }"</i><br/>
                                { "Length: "+method.body.get.instructions.length }
                                <div>{ XHTML.throwableToXHTML(throwable) }</div>
                            </div>
                        }

                    <section>
                        <h1>{ exResource }</h1>
                        <p>Number of thrown exceptions: { exInstances.size }</p>
                        { exDetails }
                    </section>
                })

            val node =
                XHTML.createXHTML(
                    Some("Exceptions Thrown During Interpretation"), scala.xml.NodeSeq.fromSeq(body)
                )
            val file = writeAndOpen(node, "ExceptionsOfCrashedAbstractInterpretations", ".html")

            (
                "During the interpretation of "+
                methodsCount.get+" methods (of "+project.methodsCount+") in "+
                project.classFilesCount+" classes (real time: "+getTime('OVERALL).toSeconds+
                ", ai (∑CPU Times): "+getTime('AI).toSeconds+
                ")"+collectedExceptions.size+" exceptions occured.",
                Some(file)
            )
        } else {
            (
                "No exceptions occured during the interpretation of "+
                methodsCount.get+" methods (of "+project.methodsCount+") in "+
                project.classFilesCount+" classes\nreal time: "+getTime('OVERALL).toSeconds+"\n"+
                "ai (∑CPU Times): "+getTime('AI).toSeconds +
                s"; evaluated ${instructionEvaluationsCount.get} instructions\n"+
                "naive ai (∑CPU Times): "+getTime('NAIVE_AI).toSeconds+"\n",
                None
            )
        }
    }
}
