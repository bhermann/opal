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
package br
package analyses

import java.net.URL
import java.io.File

import scala.util.control.ControlThrowable

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

import org.opalj.br.reader.Java9LibraryFramework
import org.opalj.log.OPALLogger
import org.opalj.log.GlobalLogContext
import org.opalj.log.LogContext

/**
 * Provides the necessary infrastructure to easily execute a given analysis that
 * generates some analysis result that can be printed on the command line.
 *
 * To facilitate the usage of this trait several implicit conversions are defined that
 * wrap standard analyses ([[org.opalj.br.analyses]]) such that they report
 * results that are reportable.
 *
 * This class distinguishes between class files belonging to the code base under
 * analysis and those that belong to the libraries. Those belonging to the libraries
 * are loaded using the `ClassFileReader` for library classes (basically, all method
 * bodies are skipped [[org.opalj.br.reader.Java8LibraryFramework]]).
 * The parameter to specify library classes is `-libcp=`, the parameter to specify
 * the "normal" classpath is `-cp=`.
 *
 * ==Control Flow==
 *  1. The standard parameters are checked.
 *  1. The analysis is called to let it verify the analysis specific parameters.
 *  1. The [[Project]] is created.
 *  1. The `analyze` method of the [[Analysis]] is called with the project and the parameters.
 *  1. The results are printed.
 *
 * @author Michael Eichberg
 * @author Arne Lottmann
 */
trait AnalysisExecutor {

    /**
     * The analysis that will be executed.
     *
     * The `analyze` method implemented by the analysis will be called after loading
     * all class files and creating a `Project`. Additionally,
     * all specified (additional) parameters are passed to the analyze method.
     */
    val analysis: Analysis[URL, ReportableAnalysisResult]

    /**
     * Describes the analysis specific parameters. An analysis specific parameter
     * has to start with a dash ("-") and has to contain an equals sign ("=").
     *
     * @note The parameter `-cp=` is already predefined (see general documentation).
     * @note The parameter `-library=` is already predefined (see general documentation).
     */
    def analysisSpecificParametersDescription: String = ""

    /**
     * Checks if the (additional) parameters are understood by
     * the analysis. If an error is found a list of issues is returned and the analysis
     * will not be executed.
     *
     * This method '''must be''' overridden if the analysis defines additional
     * parameters. A method that overrides this method should `return` the list of
     * issues if it can't validate all arguments.
     * The default behavior is to check that there are no additional parameters.
     */
    def checkAnalysisSpecificParameters(parameters: Seq[String]): Traversable[String] = {
        if (parameters.isEmpty) Nil else parameters.map("unknown parameter: "+_)
    }

    /**
     * Prints out general information how to use this analysis. Printed whenever
     * the set of specified parameters is not valid.
     */
    protected def printUsage(implicit logContext: LogContext): Unit = {
        val analysisModes = AnalysisModes.values.map(_.toString.replace(" ", "_")).mkString(",")
        OPALLogger.info(
            "usage",
            "java "+
                this.getClass().getName()+"\n"+
                "[-cp=<Directories or JAR/class files> (If no class path is specified the current folder is used.)]\n"+
                "[-libcp=<Directories or JAR/class files>]\n"+
                "[-analysisMode=<the kind of project ("+analysisModes+")>]\n"+
                "[-completelyLoadLibraries=<true|false> (The default is false.)]\n"+
                analysisSpecificParametersDescription
        )
        OPALLogger.info("general", "description: "+analysis.description)
        OPALLogger.info("general", "copyright: "+analysis.copyright)
    }

    def main(args: Array[String]): Unit = {

        implicit val logContext = GlobalLogContext
        if (args.contains("-help")) {
            printUsage
            sys.exit(0)
        }

        def showError(message: String): Unit = OPALLogger.error("project configuration", message)

        //
        // 1. check arguments
        //
        // Input files must be either directories, or class/jar files.
        //
        def verifyFile(filename: String): Option[File] = {
            val file = new File(filename)

            def workingDirectory: String = {
                s"(working directory: ${System.getProperty("user.dir")})"
            }

            if (!file.exists) {
                showError(s"File does not exist: $file $workingDirectory.")
                None
            } else if (!file.canRead) {
                showError(s"Cannot read: $file $workingDirectory.")
                None
            } else if (!file.isDirectory() &&
                !filename.endsWith(".jar") &&
                !filename.endsWith(".ear") &&
                !filename.endsWith(".war") &&
                !filename.endsWith(".zip") &&
                !filename.endsWith(".class")) {
                showError(s"Input file is neither a directory nor a class or JAR file: $file.")
                None
            } else
                Some(file)
        }

        def verifyFiles(filenames: Array[String]): Seq[File] = {
            filenames.toSeq.map(verifyFile).flatten
        }

        val (cp, args1) = try {
            {
                def splitCPath(path: String) = path.substring(4).split(File.pathSeparator)

                args.partition(_.startsWith("-cp=")) match {
                    case (Array(), notCPArgs) ⇒
                        (Array(System.getProperty("user.dir")), notCPArgs)
                    case (Array(cpParam), notCPArgs) ⇒
                        (splitCPath(cpParam), notCPArgs)
                    case (cpParams: Array[String], notCPArgs) ⇒
                        (cpParams.map(splitCPath).flatten, notCPArgs)
                }
            }
        } catch {
            case ct: ControlThrowable ⇒ throw ct;
            case t: Throwable ⇒
                OPALLogger.error("fatal", "failed parsing the classpath", t)
                sys.exit(2)
        }

        OPALLogger.info("project configuration", s"the classpath is ${cp.mkString}")
        val cpFiles = verifyFiles(cp)
        if (cpFiles.isEmpty) {
            showError("Nothing to analyze.")
            printUsage
            sys.exit(1)
        }

        val (libcp, args2) = {
            def splitLibCPath(path: String) = path.substring(7).split(File.pathSeparator)

            args1.partition(_.startsWith("-libcp=")) match {
                case noLibs @ (Array(), args2) ⇒
                    noLibs
                case (Array(libParam), args2) ⇒
                    (splitLibCPath(libParam), args2)
                case (libParams: Array[String], args2) ⇒
                    (libParams.map(splitLibCPath).flatten, args2)
            }
        }
        val libcpFiles = verifyFiles(libcp)

        val (analysisMode, args3) = try {
            args2.partition(_.startsWith("-analysisMode=")) match {
                case (Array(), args3) ⇒
                    (AnalysisModes.DesktopApplication, args3)
                case (Array(analysisModeParameter), args3) ⇒
                    val analysisMode = analysisModeParameter.substring(14).replace("_", " ")
                    (AnalysisModes.withName(analysisMode), args3)
            }

        } catch {
            case ct: ControlThrowable ⇒ throw ct;
            case t: Throwable ⇒
                OPALLogger.error("project configuration", "failed parsing the analysis mode", t)
                printUsage
                sys.exit(2)
        }
        OPALLogger.info("project configuration", s"the analysis mode is $analysisMode")

        val (completelyLoadLibraries, args4) = try {
            args3.partition(_.startsWith("-completelyLoadLibraries=")) match {
                case (Array(), args4) ⇒
                    (false, args4)
                case (Array(completelyLoadLibrariesParameter), args4) ⇒
                    val completelyLoadLibraries: Boolean = completelyLoadLibrariesParameter.substring(25).toBoolean
                    (completelyLoadLibraries, args4)
            }

        } catch {
            case ct: ControlThrowable ⇒ throw ct;
            case t: Throwable ⇒
                OPALLogger.error("project configuration", "failed parsing completelyLoadLibraries", t)
                printUsage
                sys.exit(2)
        }

        if (args4.nonEmpty)
            OPALLogger.info("project configuration", "analysis specific paramters: "+args4.mkString(","))
        val issues = checkAnalysisSpecificParameters(args4)
        if (issues.nonEmpty) {
            issues.foreach { i ⇒ OPALLogger.error("project configuration", i) }
            printUsage
            sys.exit(2)
        }

        //
        // 2. setup project context
        //
        val project: Project[URL] = try {
            setupProject(
                cpFiles, libcpFiles, completelyLoadLibraries,
                analysisMode,
                ConfigFactory.load()
            )
        } catch {
            case ct: ControlThrowable ⇒ throw ct;
            case t: Throwable ⇒
                OPALLogger.error("fatal", "setting up the project failed", t)
                printUsage
                sys.exit(2)
        }

        //
        // 3. execute analysis
        //
        OPALLogger.info("info", "executing analysis: "+analysis.title+".")
        // TODO Add progressmanagement.
        val result = analysis.analyze(project, args2, ProgressManagement.None)
        OPALLogger.progress("Result:\n"+result.toConsoleString)
    }

    protected def handleParsingExceptions(
        project:    SomeProject,
        exceptions: Traversable[Throwable]
    ): Unit = {
        if (exceptions.isEmpty)
            return ;

        implicit val logContext = project.logContext
        for (exception ← exceptions) {
            OPALLogger.error("creating project", "ignoring invalid class file", exception)
        }
    }

    def setupProject(
        cpFiles:                 Iterable[File],
        libcpFiles:              Iterable[File],
        completelyLoadLibraries: Boolean,
        analysisMode:            AnalysisMode,
        fallbackConfiguration:   Config
    )(
        implicit
        initialLogContext: LogContext
    ): Project[URL] = {

        val analysisModeSpecification = s"${AnalysisMode.ConfigKey} = $analysisMode"
        val analysisModeConfig = ConfigFactory.parseString(analysisModeSpecification)
        val configuredConfig = analysisModeConfig.withFallback(fallbackConfiguration)

        OPALLogger.info("creating project", "reading project class files")
        val JavaClassFileReader = Project.JavaClassFileReader(theConfig = configuredConfig)

        val (classFiles, exceptions1) =
            reader.readClassFiles(
                cpFiles,
                JavaClassFileReader.ClassFiles,
                (file) ⇒ OPALLogger.info("creating project", "\tfile: "+file)
            )

        val (libraryClassFiles, exceptions2) = {
            if (libcpFiles.nonEmpty) {
                OPALLogger.info("creating project", "reading library class files")
                reader.readClassFiles(
                    libcpFiles,
                    if (completelyLoadLibraries) {
                        JavaClassFileReader.ClassFiles
                    } else {
                        Java9LibraryFramework.ClassFiles
                    },
                    (file) ⇒ OPALLogger.info("creating project", "\tfile: "+file)
                )
            } else {
                (Iterable.empty[(ClassFile, URL)], List.empty[Throwable])
            }
        }
        val project =
            Project(
                classFiles,
                libraryClassFiles,
                libraryClassFilesAreInterfacesOnly = !completelyLoadLibraries,
                Traversable.empty
            )(
                    config = configuredConfig
                )
        handleParsingExceptions(project, exceptions1 ++ exceptions2)

        OPALLogger.info(
            "project",
            project.statistics.map(kv ⇒ "- "+kv._1+": "+kv._2).toList.sorted.reverse.
                mkString("project statistics:\n\t", "\n\t", "\n")
        )(project.logContext)
        project
    }
}
