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

import org.opalj.br.analyses.Project
import org.opalj.br.Method
import org.opalj.br.analyses.Project
import org.opalj.br.MethodSignature
import org.opalj.ai.domain.ThrowAllPotentialExceptionsConfiguration
import org.opalj.ai.domain.DefaultHandlingOfMethodResults
import org.opalj.ai.domain.TheMethod
import org.opalj.ai.domain.DefaultDomainValueBinding
import org.opalj.ai.domain.TheProject
import org.opalj.ai.domain.IgnoreSynchronization
import org.opalj.ai.domain.l0
/**
 * Domain object which is used to calculate the call graph.
 *
 * @author Michael Eichberg
 */
class DefaultCHACallGraphDomain[Source](
        val project: Project[Source],
        val cache:   CallGraphCache[MethodSignature, Set[Method]],
        val method:  Method
) extends Domain
    with DefaultDomainValueBinding
    with ThrowAllPotentialExceptionsConfiguration
    with TheProject
    with TheMethod
    with DefaultHandlingOfMethodResults
    with IgnoreSynchronization
    with l0.DefaultTypeLevelIntegerValues
    with l0.DefaultTypeLevelLongValues
    with l0.DefaultTypeLevelFloatValues
    with l0.DefaultTypeLevelDoubleValues
    with l0.TypeLevelPrimitiveValuesConversions
    with l0.TypeLevelLongValuesShiftOperators
    with l0.DefaultReferenceValuesBinding
    with l0.TypeLevelInvokeInstructions
    with l0.TypeLevelFieldAccessInstructions
