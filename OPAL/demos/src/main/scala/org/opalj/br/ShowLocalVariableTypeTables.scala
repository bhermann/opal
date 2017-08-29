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

import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters

import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.DefaultOneStepAnalysis

/**
 * Shows the local variable type tables of given class files.
 *
 * @author Daniel Klauer
 */
object ShowLocalVariableTypeTables extends DefaultOneStepAnalysis {

    override def description: String = "Prints out the local variable type tables."

    def doAnalyze(
        project:       Project[URL],
        params:        Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {

        val messages = new ConcurrentLinkedQueue[String]()
        project.parForeachMethodWithBody(isInterrupted) { mi ⇒
            val m = mi.method
            val lvtt = m.body.get.localVariableTypeTable
            if (lvtt.nonEmpty)
                messages.add(
                    Console.BOLD + Console.BLUE + m.toJava + Console.RESET+" "+
                        lvtt.mkString("LocalVariableTypeTable: ", ",", "")
                )
        }

        import JavaConverters._
        BasicReport(messages.asScala.mkString("\n", "\n\n", "\n"))
    }
}
