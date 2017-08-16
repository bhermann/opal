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
package analyses
package cg

import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import org.opalj.ai.domain.TheProject
import org.opalj.ai.domain.TheMethod

/**
 * Configuration of a call graph algorithm that uses "variable type analysis".
 *
 * ==Thread Safety==
 * This class is thread-safe
 * import org.opalj.ai.analyses.cg.BasicVTACallGraphDomain
 * import org.opalj.ai.analyses.cg.ExtVTACallGraphDomain
 * import org.opalj.ai.analyses.cg.BasicVTAWithPreAnalysisCallGraphDomain
 * import org.opalj.ai.analyses.cg.DefaultVTACallGraphDomain(it contains no mutable state.)
 *
 * ==Usage==
 * Instances of this class are passed to a `CallGraphFactory`'s `create` method.
 *
 * @author Michael Eichberg
 */
abstract class VTACallGraphAlgorithmConfiguration(
        project: SomeProject
) extends DefaultCallGraphAlgorithmConfiguration(project) {

    type CallGraphDomain = Domain with ReferenceValuesDomain with TheProject with TheMethod

    def Domain(method: Method): CallGraphDomain

    val Extractor = new VTACallGraphExtractor(cache, Domain)
}

class BasicVTACallGraphAlgorithmConfiguration(
        project: SomeProject
) extends VTACallGraphAlgorithmConfiguration(project) {

    def Domain(method: Method): BasicVTACallGraphDomain[_] = {
        new BasicVTACallGraphDomain(project, cache, method)
    }
}

abstract class VTAWithPreAnalysisCallGraphAlgorithmConfiguration(
        project: SomeProject
) extends VTACallGraphAlgorithmConfiguration(project) {

    val fieldValueInformation = project.get(FieldValuesKey)

    val methodReturnValueInformation = project.get(MethodReturnValuesKey)

}

class BasicVTAWithPreAnalysisCallGraphAlgorithmConfiguration(
        project: SomeProject
) extends VTAWithPreAnalysisCallGraphAlgorithmConfiguration(project) {

    def Domain(method: Method): BasicVTAWithPreAnalysisCallGraphDomain[_] = {
        new BasicVTAWithPreAnalysisCallGraphDomain(
            project, fieldValueInformation, methodReturnValueInformation,
            cache,
            method
        )
    }
}

class DefaultVTACallGraphAlgorithmConfiguration(
        project: SomeProject
) extends VTAWithPreAnalysisCallGraphAlgorithmConfiguration(project) {

    def Domain(method: Method): DefaultVTACallGraphDomain[_] = {
        new DefaultVTACallGraphDomain(
            project, fieldValueInformation, methodReturnValueInformation,
            cache,
            method
        )
    }
}

class ExtVTACallGraphAlgorithmConfiguration(
        project: SomeProject
) extends VTAWithPreAnalysisCallGraphAlgorithmConfiguration(project) {

    def Domain(method: Method): ExtVTACallGraphDomain[_] = {
        new ExtVTACallGraphDomain(
            project, fieldValueInformation, methodReturnValueInformation,
            cache,
            method
        )
    }
}
