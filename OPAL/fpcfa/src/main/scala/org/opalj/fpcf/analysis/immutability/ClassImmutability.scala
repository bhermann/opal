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
package fpcf
package analysis
package immutability

sealed trait ClassImmutabilityPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = ClassImmutability

}

/**
 * Specifies the mutability of instances of a class type. The
 * highest rating a class type can have is "Immutable", then "Conditionally Immutable",
 * then "Mutable".
 *
 * A class is considered immutable if the state of a class does not change after
 * initialization; this includes all classes referenced by the class (transitive hull).
 * A class is considered conditionally immutable if the state of the class itself
 * cannot be mutated, but objects referenced by the class can be mutated (so called
 * immutable collections are typically rated as "conditionally immutable"). A class is
 * – at the latest – considered mutable if a client can mutate (directly or indirectly)
 * the state of respective objects. In general the state of a class is determined w.r.t.
 * the declared fields. I.e., a method that has, e.g., a call time dependent behavior,
 * but which does not mutate the state of the class does not affect the mutability rating.
 *
 * The mutability assessment is by default done on a per class basis and only includes
 * the super classes of a class. A rating that includes all usages is only meaningful
 * if we analyze an application.
 *
 * ==Thread-safe Lazily Initialized Fields==
 * A field that is initialized lazily in a thread-safe manner; i.e.,
 * which is set at most once after construction and which is always set to the
 * same value independent of the time of (lazy) initialization, may not affect the
 * mutability rating. However, an analysis may rate such a class as mutable. An
 * example of such a field is the field that stores the lazily calculated hashCode of
 * a `String` object.
 *
 * ==Inheritance==
 *  - Instances of `java.lang.Object` are immutable. However, if a class defines a
 * constructor which has a parameter of type object and which assigns the respective
 * parameter value to a field will at-most be conditionally immutable; in general
 * we must assume that the referenced object may be (at runtime) some mutable object.
 *  - In general, only classes that inherit from (conditionally) immutable class can be
 * (conditionally) immutable; if a class is mutable, all subclasses are also
 * considered to be mutable. I.e., a subclass can never have a higher mutability rating
 * than a superclass.
 *  - All classes for which the superclasstype information is not complete are rated
 * as unknown. (Interfaces are generally ignored as they are always immutable.)
 *
 * ==Native Methods==
 * Native methods are ignored.
 *
 * ==Class Instances==
 * The mutability of class instances is determined by analyzing the class instance
 * only.
 *
 * ==Interfaces==
 * Are not considered during the analysis as they are always immutable. (All fields are
 * `static` and `final`.)
 *
 * @author Andre Pacak
 * @author Michael Eichberg
 */
sealed trait ClassImmutability extends Property with ClassImmutabilityPropertyMetaInformation {

    /**
     * Returns the key used by all `ClassImmutability` properties.
     */
    final def key = ClassImmutability.key

}
/**
 * Common constants use by all [[ClassImmutability]] properties associated with methods.
 */
object ClassImmutability extends ClassImmutabilityPropertyMetaInformation {

    /**
     * The key associated with every [[ClassImmutability]] property.
     */
    final val key =
        PropertyKey.create[ClassImmutability](
            "ClassImmutability",
            // The default property that will be used if no analysis is able
            // to (directly) compute the respective property.
            MutableClassDueToUnresolvableDependency,
            // When we have a cycle all properties are necessarily at least conditionally immutable
            // hence, we can leverage the "immutability" 
            ImmutableClass

        )
}

case object UnknownClassImmutability extends ClassImmutability

/**
 * An instance of the respective class is effectively immutable. I.e., after creation it is not
 * possible for a client to set a field or to call a method that updates the internal state
 *
 */
case object ImmutableClass extends ClassImmutability {
    final val isRefineable = false
}

case object ConditionallyImmutableClass extends ClassImmutability {
    final val isRefineable = false
}

case object AtLeastConditionallyImmutableClass extends ClassImmutability {
    final val isRefineable = true
}

sealed trait MutableClass extends ClassImmutability {
    final val isRefineable = false
    val reason: String
}

case object MutableClassByAnalysis extends MutableClass {
    final val reason = "determined by analysis"
}

case object MutableClassDueToUnknownSupertypes extends MutableClass {
    final val reason = "the type hierarchy is upwards incomplete"
}

case object MutableClassDueToUnresolvableDependency extends MutableClass {
    final val reason = "a dependency cannot be resolved"
}

