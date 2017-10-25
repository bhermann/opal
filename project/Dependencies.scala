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

import sbt._

/**
  * Manages the library dependencies of the subprojects of OPAL.
  *
  * @author Simon Leischnig
  */
object Dependencies {


    lazy val version = new {
        val junit = "4.12"
        val scalatest = "3.0.4"
        val scalacheck = "1.13.5"

        val scalaxml = "1.0.6"
        val playjson = "2.6.6"
        val ficus = "1.4.2"

        val apachetext = "1.1"
        val parsercombinators = "1.0.6"

        val scalafx = "8.0.144-R12"
        val controlsfx = "8.40.14"
        val txtmark = "0.16"
        val jacksonDF = "2.9.2"
        val chocosolver = "4.0.5"
    }

    lazy val library = new  {

        // --- test libraries

        val junit = "junit" % "junit" % version.junit % "test,it"
        val scalatest = "org.scalatest" %% "scalatest" % version.scalatest % "test,it"
        val scalacheck = "org.scalacheck" %% "scalacheck" % version.scalacheck % "test,it"

        // --- general dependencies

        def reflect(scalaVersion: String) = "org.scala-lang" % "scala-reflect" % scalaVersion
        val scalaxml = "org.scala-lang.modules" %% "scala-xml" % version.scalaxml
        val playjson = "com.typesafe.play" %% "play-json" % version.playjson
        val ficus = "com.iheart" %% "ficus" % version.ficus

        val commonstext = "org.apache.commons" % "commons-text" % version.apachetext
        val scalaparsercombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % version.parsercombinators

        // --- developer tools dependencies

        val scalafx = "org.scalafx" %% "scalafx" % version.scalafx withSources() withJavadoc()
        val controlsfx = "org.controlsfx" % "controlsfx" % version.controlsfx withJavadoc()
        val txtmark = "es.nitaur.markdown" % "txtmark" % version.txtmark withJavadoc()
        val jacksonDF = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-csv" % version.jacksonDF withJavadoc()
        val chocosolver = "org.choco-solver" % "choco-solver" % version.chocosolver withSources() withJavadoc()


    }

    import library._

    val testlibs: Seq[ModuleID] = Seq(junit, scalatest, scalacheck)

    def common(scalaVersion: String) = Seq(reflect(scalaVersion), scalaxml, playjson, ficus)
    val bi = Seq(commonstext)
    val br = Seq(scalaparsercombinators)
    val developertools = Seq(scalafx, controlsfx, txtmark, jacksonDF, chocosolver)

}