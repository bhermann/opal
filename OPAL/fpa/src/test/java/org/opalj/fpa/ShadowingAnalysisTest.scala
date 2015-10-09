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
package org.opalj.fpa

import org.opalj.br.analyses.Project
import java.net.URL
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SourceElementsPropertyStoreKey
import org.opalj.fp.PropertyKey
import org.opalj.fpa.test.annotations.ProjectAccessibilityKeys
import org.opalj.AnalysisModes

/**
 * 
 * 
 * @author Michael Reif
 */
abstract class ShadowingAnalysisTest extends AbstractFixpointAnalysisTest {
    
    lazy val analysisName = "ShadowingAnalysis"
    
    override def testFileName = "classfiles/shadowingTest.jar"
    
    override def testFilePath = "fpa"
        
    override def runAnalysis(project: Project[URL]) : Unit = {
        val propertyStore = project.get(SourceElementsPropertyStoreKey)
        val fmat = new Thread(new Runnable { def run = ShadowingAnalysis.analyze(project) });
        fmat.start
        fmat.join
        propertyStore.waitOnPropertyComputationCompletion( /*default: true*/ )
    }
    
    override def propertyKey: PropertyKey = ProjectAccessibility.Key
    
    override def propertyAnnotation: ObjectType =
        ObjectType("org/opalj/fpa/test/annotations/ProjectAccessibilityProperty")
    
    lazy val defaultValue = ProjectAccessibilityKeys.Global.toString
}

class ShadowingAnalysisCPATest extends ShadowingAnalysisTest {
    
    override def analysisMode = AnalysisModes.LibraryWithClosedPackagesAssumption
}

class ShadowingAnalysisOPATest extends ShadowingAnalysisTest {
    
    override def analysisMode = AnalysisModes.LibraryWithClosedPackagesAssumption
}