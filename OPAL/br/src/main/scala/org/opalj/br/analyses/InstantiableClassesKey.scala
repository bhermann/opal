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
package br
package analyses

import org.opalj.concurrent.defaultIsInterrupted
import org.opalj.fpcf.analyses.LibraryInstantiableClassesAnalysis

/**
 * The ''key'' object to get information about the classes that can be instantiated
 * (either, directly or indirectly).
 *
 * @example To get the index use the [[Project]]'s `get` method and pass in `this` object.
 *
 * @author Michael Eichberg
 */
object InstantiableClassesKey extends ProjectInformationKey[InstantiableClasses] {

    /**
     * The [[InstantiableClasses]] has no special prerequisites.
     *
     * @return `Nil`.
     */
    override protected def requirements: Seq[ProjectInformationKey[Nothing]] = Nil

    /**
     * Computes the information which classes are (not) instantiable.
     *
     * @see [[InstantiableClasses]] and [[InstantiableClassesAnalysis]]
     */
    override protected def compute(project: SomeProject): InstantiableClasses = {
        val isLibrary = AnalysisModes.isLibraryLike(project.analysisMode)
        if (isLibrary)
            LibraryInstantiableClassesAnalysis.doAnalyze(project, defaultIsInterrupted)
        else
            InstantiableClassesAnalysis.doAnalyze(project, defaultIsInterrupted)
    }
}

