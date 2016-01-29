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

import scala.collection.mutable
import org.opalj.collection.mutable.{ArrayMap ⇒ OArrayMap}

/**
 * The fixpoint computations framework (`fpcf`) is a general framework to perform fixpoint
 * computations on a fixed set of entities. The framework in particular
 * supports the development of static analyses. In this case, the fixpoint computations/
 * static analyses are generally operating on the code and need to be executed until
 * the computation has reached its (implicit) fixpoint. The fixpoint framework explicitly
 * supports cyclic dependencies/computations and performs all computations in parallel.
 * A prime use case of the fixpoint framework
 * are all those analyses that may interact with the results of other analyses.
 *
 * For example, an analysis that analyses all field write access to determine if we can
 * refine a field's type (for the purpose of the analysis) can (reuse) the information
 * about the return types of methods, which however may depend on the refined field types.
 *
 * The framework is generic enough to facilitate the implementation of
 * anytime algorithms.
 *
 * The framework supports two very basic kinds of properties. Those properties that are
 * associated with a specific entity; e.g., a field's ''real'' type, and those properties
 * that are shared by a set of entities; e.g., the set of all methods that are entry
 * points. In general, the latter properties can also be modeled using the first
 * mechanism, but this would be highly ineffective.
 *
 * @note The dependency relation is as follows:
 *      “A depends on B”
 *          `===`
 *      “A is the depender, B is the dependee”.
 *          `===`
 *      “B is depended on by A”
 *
 * @author Michael Eichberg
 */
package object fpcf {

    /**
     * The type of the values stored in a property store.
     *
     * Basically, a simple type alias to facilitate comprehension of the code.
     */
    type Entity = AnyRef

    /**
     * A function that takes an entity and returns a result along with the set of entities
     * that should be processed next.
     *
     * @see [[org.opalj.fpcf#PropertyComputation]] for further details regarding the result.
     */
    type IncrementalPropertyComputation = (Entity) ⇒ IncrementalPropertyComputationResult

    /**
     * A function that is given an entity and a property (which the stores was asked for) and
     * which returns an incremental property computations result.
     *
     * In general an `IncrementalStep` function is called back by the property store when
     * the analysis required some information.
     */
    type IncrementalStep = (Entity, Property) ⇒ IncrementalPropertyComputationResult

    /**
     * A function that takes an entity and returns a result. The result maybe:
     *  - the final derived property,
     *  - a function that will continue computing the result once the information
     *      about some other entity is available or,
     *  - an intermediate result.
     */
    type PropertyComputation = (Entity) ⇒ PropertyComputationResult

    /**
     * A function that continues the computation of a property. It takes
     * the entity + property of the entity on which the computation depends.
     */
    type Continuation = (Entity, Property) ⇒ PropertyComputationResult

    /**
     * The result of a computation if the computation derives multiple properties
     * at the same time.
     */
    type ComputationResults = Traversable[(Entity, Property)]

    type SomePropertyKey = PropertyKey[_ <: Property]

    type SomeEOptionP = EOptionP[_ <: Property]

    type SomeEPK = EPK[_ <: Property]

    type SomeEP = EP[_ <: Property]

    /**
     * A computation of a property that was restarted (under different properties)
     * yielded the same result.
     *
     * @note This is just an alias for [[NoResult]].
     */
    final val Unchanged: NoResult.type = NoResult

    /**
     * Computing a property for the a specific element is not/never possible.
     *
     * @note This is just an alias for [[NoResult]].
     */
    final val Impossible: NoResult.type = NoResult

    /**
     * The computation has no results (and there will be no results in the future!).
     *
     * @note This is just an alias for [[NoResult]].
     */
    final val Empty: NoResult.type = NoResult

    //
    //
    // IMPLEMENTATION SPECIFIC TYPES
    //
    //

    /**
     * The type of the observers that can be associated with a specific property
     * and element.
     */
    private[fpcf]type Observers = mutable.ListBuffer[PropertyObserver]

    /**
     * The type of the properties data structure that is associated with each
     * property.
     *
     * The property can be `null` if we have multiple analyses that are waiting for
     * the respective property.
     *
     * The underlying assumption is that not every property key is actually associated
     * with a property value for each element.
     */
    private[fpcf]type Properties = OArrayMap[PropertyAndObservers]

    private[fpcf]type UpdateType = UpdateTypes.Value

}

