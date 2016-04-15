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
package domain

import org.opalj.log.LogContext
import org.opalj.br.analyses.SomeProject

/**
 * Provides information about the underlying project.
 *
 * ==Usage==
 * If a (partial-) domain needs information about the project declare a corresponding
 * self-type dependency.
 * {{{
 * trait MyIntegerValuesDomain extends IntegerValues { this : TheProject =>
 * }}}
 *
 * ==Providing Information about a Project==
 * A domain that provides information about the currently analyzed project should inherit
 * from this trait and implement the respective method.
 *
 * ==Core Properties==
 *  - Defines the public interface.
 *  - Makes the analyzed [[org.opalj.br.analyses.Project]] available.
 *  - Thread safe.
 *
 * @note '''It is recommended that the domain that provides the project information
 *      does not use the `override` access flag.'''
 *      This way the compiler will issue a warning if two implementations are used
 *      to create a final domain.
 *
 * @author Michael Eichberg
 */
trait TheProject extends ProjectBasedClassHierarchy with LogContextProvider {

    /**
     * Returns the project that is currently analyzed.
     */
    def project: SomeProject

    final implicit def logContext: LogContext = project.logContext
}
