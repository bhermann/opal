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
package demo

import java.net.URL
import org.opalj.util.PerformanceEvaluation.time
import org.opalj.util.Seconds
import org.opalj.br.analyses.SourceElementsPropertyStoreKey
import org.opalj.br.analyses.DefaultOneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.BasicReport
import org.opalj.br.ClassFile
import org.opalj.fpcf.analysis.immutability.ObjectImmutabilityAnalysis
import org.opalj.fpcf.analysis.immutability.ObjectImmutability
import org.opalj.fpcf.analysis.immutability.TypeImmutabilityAnalysis

/**
 * Demonstrates how to run the immutability analysis.
 *
 * @author Michael Eichberg
 */
object ImmutabilityAnalysisDemo extends DefaultOneStepAnalysis {

    override def title: String =
        "determines those classes that are mmutable"

    override def description: String =
        "identifies classes/type which are immutable"

    override def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {

        val projectStore = project.get(SourceElementsPropertyStoreKey)
        projectStore.debug = true

        // We immediately also schedule the purity analysis to improve the
        // parallelization!

        val manager = project.get(FPCFAnalysesManagerKey)

        var t = Seconds.None
        time {
            manager.runAll(ObjectImmutabilityAnalysis, TypeImmutabilityAnalysis)
        } { r ⇒ t = r.toSeconds }

        projectStore.validate()

        val immutableClasses =
            projectStore.entities(ObjectImmutability.key).groupBy { _.p }

        val immutableClassesInfo =
            immutableClasses.values.flatten.
                map(ep ⇒ ep._1.asInstanceOf[ClassFile].thisType.toJava+"=> "+ep.p).
                toList.sorted.mkString("\n")

        BasicReport(immutableClassesInfo+"\n"+projectStore.toString(false)+"\nAnalysis time: "+t)
    }
}
