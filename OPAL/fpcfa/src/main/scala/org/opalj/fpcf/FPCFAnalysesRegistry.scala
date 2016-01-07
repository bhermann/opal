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

import org.opalj.fpcf.analysis._
import org.opalj.fpcf.analysis.fields.FieldUpdatesAnalysis
import org.opalj.fpcf.analysis.methods.PurityAnalysis

/**
 * Registry for all factories for analyses that are implemented using the fixpoint computations
 * framework ('''fpcf''').
 *
 * The registry primarily serves as a central container that can be queried
 * by subsequent tools.
 *
 * The analyses that are part of OPAL are already registered.
 *
 * @note The registry does not handle dependencies between analyses yet.
 *
 * ==Thread Safety==
 * The registry is thread safe.
 *
 * @author Michael Reif
 * @author Michael Eichberg
 */
object FPCFAnalysesRegistry {

    @volatile
    private[this] var descriptionToFactory: Map[String, FPCFAnalysisRunner] = Map.empty

    /**
     * Registers the factory of a fixpoint analysis that can be
     * used to compute a specific (set of) property(ies).
     *
     * @param analysisDescription A short description of the analysis and the properties that the
     * 		analysis computes; in particular w.r.t. a specific set of entities.
     *
     * @param analysisFactory The factory.
     */
    def register(analysisDescription: String, analysisFactory: FPCFAnalysisRunner): Unit = {
        descriptionToFactory += ((analysisDescription, analysisFactory))
    }

    /**
     * Returns the descriptions of the registered analyses. These descriptions are
     * expected to be useful to the end-users.
     */
    def analysisDescriptions(): Iterable[String] = descriptionToFactory.keys

    /**
     * Returns the current view of the registry.
     */
    def factories: Iterable[FPCFAnalysisRunner] = descriptionToFactory.values

    /**
     * Returns the factory for analysis with a matching description.
     */
    def factory(analysisDescription: String): FPCFAnalysisRunner = {
        descriptionToFactory(analysisDescription)
    }

    // initialize the registry with the known default analyses
    register(
        "[MethodAccessibilityAnalysis] Computes the project accessibility property of methods w.r.t. clients.",
        MethodAccessibilityAnalysis
    )

    register(
        "[FactoryMethodAnalysis] Determines if a static method is an accessible factory method w.r.t. clients.",
        FactoryMethodAnalysis
    )

    register(
        "[InstantiabilityAnalysis] Computes if a class can (possibly) be instantiated.",
        SimpleInstantiabilityAnalysis
    )

    register(
        "[CallableFromClassesInOtherPackagesAnalysis] Computes whether a non-static method can be called via an super or subclass.",
        CallableFromClassesInOtherPackagesAnalysis
    )

    register(
        "[LibraryEntryPointAnalysis] Computes the entry points of a library.",
        LibraryEntryPointsAnalysis
    )

    register(
        "[FieldUpdatesAnalysis] Determines if fields are (effectively) final.",
        FieldUpdatesAnalysis
    )

    register(
        "[PurityAnalysis] Determines if a method is pure (~ has no side effects).",
        PurityAnalysis
    )
}
