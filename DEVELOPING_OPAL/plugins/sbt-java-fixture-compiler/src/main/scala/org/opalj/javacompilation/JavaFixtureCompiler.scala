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
package org.opalj.javacompilation

import sbt._
import sbt.Keys._

import java.io.File
import java.io.Writer
import java.io.PrintWriter

import org.opalj.javacompilation.FixtureDiscovery._
import org.opalj.javacompilation.FixtureCompileSpec._

/**
 * Plug-in to compile java fixtures against the Eclipse JDT Java compiler.
 *
 * =Core tasks/keys=
 *  - `javaFixtureCompile` (compile a )
 *  - `javaFixturePackage` (compile with JAR packaging)
 *  - `javaFixtureDiscovery` (discover fixture compilation tasks)
 *
 * ==Input for javaFixtureCompile==
 *  - `javaFixtureTaskDefs` - a sequence of manually-specified tasks
 * - see: class JavaFixtureCompilationTask in FixtureCompileSpec.scala
 *
 * ==Input for javaFixtureDiscovery==
 *  - javaFixtureProjectsDir - the folder for discovery where each subfolder is a potential java fixture project
 *  - javaFixtureSupportDir - folder containing .jar support libraries
 *  - javaFixtureTargetDir - target folder for compilation and packaging
 */
object JavaFixtureCompiler extends AutoPlugin {

    //override def trigger = allRequirements

    object autoImport {
        // tasks of the plugin
        val javaFixtureCompile = taskKey[Seq[JavaFixtureCompilationResult]]("Compilation of Java fixture projects against Eclipse 4.6.1 JDT compiler.")
        val javaFixturePackage = taskKey[Seq[JavaFixturePackagingResult]]("Packaging of compiled Java fixture projects.")
        val javaFixtureDiscovery = taskKey[Seq[JavaFixtureCompilationTask]]("Discovery of Java fixture projects.")

        // will be scoped to the compilation task.
        val javaFixtureTaskDefs = settingKey[Seq[JavaFixtureCompilationTask]]("Java fixture compilation task definitions for the plugin")

        // will be scoped to the discovery task
        val javaFixtureProjectsDir = settingKey[File]("Folder containing Java fixture projects")
        val javaFixtureSupportDir = settingKey[File]("Folder containing shared support libraries used by the Java fixture projects.")
        val javaFixtureTargetDir = settingKey[File]("Folder in which the compiled and packaged Java fixture projects will be stored.")
    }

    import autoImport._

    lazy val baseJavafixtureSettings: Seq[Def.Setting[_]] = Seq(
        javaFixtureProjectsDir in javaFixtureDiscovery := sourceDirectory.value / "fixtures-java" / "projects",
        javaFixtureSupportDir in javaFixtureDiscovery := sourceDirectory.value / "fixtures-java" / "support",
        javaFixtureTargetDir in javaFixtureDiscovery := resourceManaged.value,

        javaFixtureTaskDefs in javaFixtureCompile := Seq(), // default: no manually defined tasks

        javaFixtureCompile := {
            Javacompilation.compileRunner(
                javaFixtureDiscovery.value ++ (javaFixtureTaskDefs in javaFixtureCompile).value,
                streams.value
            )
        },

        javaFixturePackage := {
            Javacompilation.packageRunner(javaFixtureCompile.value, streams.value)
        },

        javaFixtureDiscovery := {
            Javacompilation.discoveryRunner(
                (javaFixtureProjectsDir in javaFixtureDiscovery).value,
                (javaFixtureSupportDir in javaFixtureDiscovery).value,
                (javaFixtureTargetDir in javaFixtureDiscovery).value,
                streams.value
            )
        }

    )

    override lazy val projectSettings = inConfig(Compile)(baseJavafixtureSettings)

    /**
     * Object that contains the task implementations for the fixture compilation
     * plugin and helper methods (e. g. for packaging).
     */
    object Javacompilation {

        /**
         * Discovers java fixture projects using the specified directories to find
         * their implementation, configuration and support libraries.
         */
        def discoveryRunner(
            projectsDir: File,
            supportDir:  File,
            targetDir:   File,
            streams:     TaskStreams
        ): Seq[JavaFixtureCompilationTask] = {
            val compiler: TestFixtureCompiler = resolveCompiler("default")
            if (!projectsDir.exists) {
                streams.log.warn(s"The Java fixture projects folder does not exist: $projectsDir.")
                return Seq.empty
            }
            val discovery = new OPALTestFixtureDiscovery(projectsDir, supportDir, targetDir, compiler)
            discovery.discoverFixtureTasks()
        }

        /**
         * Compiles a given sequence of compilation tasks.
         */
        def compileRunner(
            tasks:   Seq[JavaFixtureCompilationTask],
            streams: TaskStreams
        ): Seq[JavaFixtureCompilationResult] = {
            val log = streams.log
            val std = new PrintWriter(new LogWriter((s: String) ⇒ log.info(s)))
            val err = new PrintWriter(new LogWriter((s: String) ⇒ log.error(s)))

            val results = (
                for (fixtureTask ← tasks.par) yield {
                fixtureTask.compiler.compile(fixtureTask, std, err, log)
            }).seq

            val (skipped, notSkipped) = results.toSeq.partition(_.wasSkipped)
            if (results.isEmpty) {
                streams.log.debug("No Java fixtures found.")
            } else if (notSkipped.isEmpty) {
                streams.log.debug("All Java fixture projects were already compiled.")
            } else {
                streams.log.info(s"(Re)Compiled ${notSkipped.size}/${results.size} Java fixtures projects.");
            }

            results
        }

        /**
         * Packages a sequence of compilation results as individual JAR files in the
         * target folder of the compilation (where the .class files are).
         */
        def packageRunner(
            compilationResults: Seq[JavaFixtureCompilationResult],
            streams:            TaskStreams
        ): Seq[JavaFixturePackagingResult] = {
            val results = (for (
                compilationResult ← compilationResults.par
            ) yield {
                packageRoutine(compilationResult, new File(compilationResult.task.targetFolder+".jar"), streams)
            }).seq

            val (skipped, notSkipped) = results.toSeq.partition(_.wasSkipped)
            if (results.isEmpty) {
                streams.log.debug("No class files found.")
            } else if (notSkipped.isEmpty) {
                streams.log.debug("All class files were already packaged.")
            } else {
                streams.log.info(s"Packaged classed of ${notSkipped.size}/${results.size} Java fixture projects.");
            }

            results
        }

        /**
         * Packages the result of the compilation of a java fixture. The resulting .jar
         * file will reside in the target folder of the compilation.
         */
        def packageRoutine(
            compilationResult: JavaFixtureCompilationResult,
            targetJar:         File,
            streams:           TaskStreams
        ): JavaFixturePackagingResult = {
            val inputFiles = (compilationResult.task.fixture.sourceFolder ** ("*.java" || "compiler.config")).get

            val newestOutputDate = if (targetJar.exists) targetJar.lastModified else 0L
            val newestInputDate = inputFiles.map(_.lastModified).foldLeft(0L)(Math.max)
            val packagingNecessary = newestOutputDate < newestInputDate;

            if (packagingNecessary) {
                val targetFolderLength = compilationResult.task.targetFolder.toString.length + 1
                val classFiles: Traversable[(File, String)] =
                    (compilationResult.task.targetFolder ** "*.class").get map { classFile ⇒
                        ((classFile, classFile.toString.substring(targetFolderLength)))
                    }

                streams.log.info(s"Creating jar in: ${targetJar.getPath}.")
                IO.zip(classFiles, targetJar)
            }

            JavaFixturePackagingResult(compilationResult, targetJar, !packagingNecessary)
        }

    }

}
