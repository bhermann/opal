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
package org.opalj.fpcf

/**
 * An information associated with an entity. Each property belongs to exactly one
 * property kind identified by a [[PropertyKey]]. Furthermore, each property
 * is associated with at most one property per property kind.
 *
 * Instances of SetProperty will always get negative ids [-X,...,-1] and instances of per
 * entity properties will get positive ids [0,...X]; to be precise the underlying property keys.
 *
 * @author Michael Eichberg
 */
trait Property {

    /**
     * The key uniquely identifies this property's category. All property objects
     * of the same kind have to use the same key.
     *
     * In general each `Property` kind is expected to have a companion object that
     * stores the unique `PropertyKey`.
     */
    def key: PropertyKey

    /**
     * The id uniquely identifies this property's category. All property objects of the
     * same kind have to use the same id which is guaranteed since they share the same `PropertyKey`
     */
    final def id: Int = key.id

    /**
     * Returns `true` if the current property may be refined in the future and, hence,
     * it is meaningful to register for update events.
     */
    def isRefineable: Boolean

    /**
     *  Returns `true` if this property is always final and no refinement is possible.
     */
    def isFinal = !isRefineable

}
