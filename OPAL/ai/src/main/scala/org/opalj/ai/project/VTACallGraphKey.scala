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
package ai
package project

import org.opalj.ai.analyses.FieldValuesKey
import org.opalj.br.analyses.ProjectInformationKey

import br.analyses.ProjectInformationKey
import br.analyses.SomeProject

/**
 * The ''key'' object to get a call graph that was calculated using the VTA algorithm.
 *
 * You can assume that – in general – the call graph calculated using the VTA algorithm
 * is more precise than the call graph calculated using the CHA algorithm. Depending
 * on the project, the performance may be better, equal or worse.
 *
 * @example
 *      To get the call graph object use the `Project`'s `get` method and pass in
 *      `this` object.
 *      {{{
 *      val ComputedCallGraph = project.get(VTACallGraphKey)
 *      }}}
 *
 * @author Michael Eichberg
 */
object VTACallGraphKey extends ProjectInformationKey[ComputedCallGraph] {

    override protected def requirements = List(FieldValuesKey)

    /**
     * Computes the `CallGraph` for the given project.
     */
    override protected def compute(project: SomeProject): ComputedCallGraph = {

        // TODO query the project to decide which configuration to choose

        val entryPoints = CallGraphFactory.defaultEntryPointsForLibraries(project)
        CallGraphFactory.create(
            project, entryPoints,
            new DefaultVTACallGraphAlgorithmConfiguration(project))
    }
}

