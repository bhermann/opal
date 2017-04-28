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

import org.opalj.br.analyses.BasicReport

import java.net.URL
import org.opalj.br.analyses.SourceElementsPropertyStoreKey
import org.opalj.log.OPALLogger
import org.opalj.log.Warn
import org.opalj.log.ConsoleOPALLogger
import org.opalj.log.GlobalLogContext
import org.opalj.fpcf.properties.Global
import org.opalj.fpcf.properties.PackageLocal
import org.opalj.fpcf.properties.ClassLocal

/**
 * @author Michael Reif
 */
object MethodAccessibilityAnalysisDemo extends MethodAnalysisDemo {

    OPALLogger.updateLogger(GlobalLogContext, new ConsoleOPALLogger(true, Warn))

    override def title: String =
        "entry point computation"

    override def description: String =
        "determines the factory methods of a library"

    override def doAnalyze(
        project:       org.opalj.br.analyses.Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {

        val executer = project.get(FPCFAnalysesManagerKey)

        var analysisTime = org.opalj.util.Seconds.None
        org.opalj.util.PerformanceEvaluation.time {

            executer.runAll(
                CallableFromClassesInOtherPackagesAnalysis,
                MethodAccessibilityAnalysis
            )

        } { t ⇒ analysisTime = t.toSeconds }

        val propertyStore = project.get(SourceElementsPropertyStoreKey)

        val global = entitiesByProperty(Global)(propertyStore)
        val packgeLocal = entitiesByProperty(PackageLocal)(propertyStore)
        val classLocal = entitiesByProperty(ClassLocal)(propertyStore)

        BasicReport(
            s"\nglobal            : ${global.size}"+
                s"\npackageLocal  : ${packgeLocal.size}"+
                s"\nclassLocal    : ${classLocal.size}"+
                "\nAnalysis time: "+analysisTime
        )
    }
}
