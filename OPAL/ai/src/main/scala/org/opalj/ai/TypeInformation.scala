/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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

import org.opalj.br.UpperTypeBound
import org.opalj.br.{BaseType, ReferenceType}
import org.opalj.br.{BooleanType, ByteType, CharType, ShortType, IntegerType, LongType}
import org.opalj.br.{FloatType, DoubleType}

/**
 * Encapsulates the available type information about a `DomainValue`.
 *
 * (See `ValuesDomain.typeOfValue(DomainValue)` for further details.)
 *
 * @author Michael Eichberg
 */
sealed trait TypeInformation {

    /**
     * Returns `true` if no type information is available.
     */
    def unknown: Boolean

    @throws[DomainException]("if no type information is available")
    def isReferenceValue: Boolean

    def asIsAReferenceValue: IsAReferenceValue

    @throws[DomainException]("if no type information is available")
    def isPrimitiveValue: Boolean

}

/**
 * Specifies that no type information is available.
 *
 * @note Recall that the computational type of a value always has to
 *      be available, but that a
 *      `ValuesDomain.typeOfValue(...)` query does not need to take the computational type
 *      into account. (Whenever the core framework requires the computational type of a
 *      value it uses the respective method.) However, in case that the
 *      underlying value may be an array or exception
 *      value the reported type must not be `TypeUnknown`.
 *
 * @author Michael Eichberg
 */
case object TypeUnknown extends TypeInformation {

    def unknown: Boolean = true

    def isReferenceValue: Boolean = throw DomainException("the type is unknown")

    final def asIsAReferenceValue: IsAReferenceValue = {
        throw new ClassCastException("cannot cast TypeUnknown to IsAReferenceValue")
    }

    def isPrimitiveValue: Boolean = throw DomainException("the type is unknown")
}

/**
 * The value has the primitive type.
 */
sealed trait IsPrimitiveValue extends TypeInformation {

    final def unknown: Boolean = false

    final def isReferenceValue: Boolean = false

    final def asIsAReferenceValue: IsAReferenceValue = {
        val className = this.getClass().getName()
        throw new ClassCastException(s"cannot cast $className to IsAReferenceValue")
    }

    final def isPrimitiveValue: Boolean = true

    def primitiveType: BaseType
}

object IsPrimitiveValue {

    def unapply(answer: IsPrimitiveValue): Option[BaseType] = Some(answer.primitiveType)

}

trait IsBooleanValue extends IsPrimitiveValue {
    final def primitiveType: BooleanType = BooleanType
}

trait IsByteValue extends IsPrimitiveValue {
    final def primitiveType: ByteType = ByteType
}

trait IsCharValue extends IsPrimitiveValue {
    final def primitiveType: CharType = CharType
}

trait IsShortValue extends IsPrimitiveValue {
    final def primitiveType: ShortType = ShortType
}

trait IsIntegerValue extends IsPrimitiveValue {
    final def primitiveType: IntegerType = IntegerType
}

trait IsFloatValue extends IsPrimitiveValue {
    final def primitiveType: FloatType = FloatType
}

trait IsLongValue extends IsPrimitiveValue {
    final def primitiveType: LongType = LongType
}

trait IsDoubleValue extends IsPrimitiveValue {
    final def primitiveType: DoubleType = DoubleType
}

/**
 * Characterizes a single reference value. Captures the information about one of the values
 * a domain value may refer to. For example, in the following:
 * {{{
 * val o = If(...) new Object() else "STRING"
 * }}}
 * o is a reference value (`IsReferenceValue`) that refers to two "simple" values each
 * represented by an instance of an `IsAReferenceValue` (`new Object()` and `"STRING"`).
 *
 * @author Michael Eichberg
 */
trait IsAReferenceValue {

    /**
     * The upper bound of the value's type. The upper bound is empty if this
     * value is `null` (i.e., `isNull == Yes`). The upper bound is only guaranteed to contain
     * exactly one type if the type is precise. (i.e., `isPrecise == true`). Otherwise,
     * the upper type bound may contain one or more types that are not known to be
     * in an inheritance relation, but which will ''correctly'' approximate the runtime
     * type.
     *
     * @note If only a part of a project is analyzed, the class hierarchy may be
     *      fragmented and it may happen that two classes that are indeed in an
     *      inheritance relation – if we would analyze the complete project – are part
     *      of the upper type bound.
     */
    def upperTypeBound: UpperTypeBound

    /**
     * If `Yes` the value is known to always be `null` at runtime. In this
     * case the upper bound  is (has to be) empty. If the answer is `Unknown` then the
     * analysis was not able to statically determine whether the value is `null` or
     * is not `null`. In this case the upper bound is expected to be non-empty.
     * If the answer is `No` then the value is statically known not to be `null`. In this
     * case, the upper bound may precisely identify the runtime type or still just identify
     * an upper bound.
     *
     * This default implementation always returns `Unknown`; this is a sound
     * over-approximation.
     *
     * @note '''This method is expected to be overridden by subtypes.'''
     *
     * @return `Unknown`.
     */
    def isNull: Answer = Unknown

    /**
     * Returns `true` if the type information is precise. I.e., the type returned by
     * `upperTypeBound` precisely models the runtime type of the value.
     *  If, `isPrecise` returns true, the type of this value can
     * generally be assumed to represent a class type (not an interface type) or
     * an array type. However, this domain also supports the case that `isPrecise`
     * returns `true` even though the associated type identifies an interface type
     * or an abstract class type. The later case may be interesting in the context
     * of classes that are generated at run time.
     *
     * This default implementation always returns `false`.
     *
     * @note `isPrecise` is always `true` if this value is known to be `null`.
     *
     * @note '''This method is expected to be overridden by subtypes.'''
     *
     * @return `false`
     */
    def isPrecise: Boolean = false

    /**
     * Tests if the type of this value is potentially a subtype of the specified
     * reference type under the assumption that this value is not `null`.
     * This test takes the precision of the type information into account.
     * That is, if the currently available type information is not precise and
     * the given type has a subtype that is always a subtype of the current
     * upper type bound, then `Unknown` is returned. Given that it may be
     * computationally intensive to determine whether two types have a common subtype
     * it may be better to just return `Unknown` in case that this type and the
     * given type are not in a direct inheritance relationship.
     *
     *
     * Basically, this method implements the same semantics as the `ClassHierarchy`'s
     * `isSubtypeOf` method, but it additionally checks if the type of this value
     * ''could be a subtype'' of the given supertype. I.e., if this value's type
     * identifies a supertype of the given `supertype` and that type is not known
     * to be precise, the answer is `Unknown`.
     *
     * For example, assume that the type of this reference value is
     * `java.util.Collection` and we know/have to assume that this is only an
     * upper bound. In this case an answer is `No` if and only if it is impossible
     * that the runtime type is a subtype of the given supertype. This
     * condition holds, for example, for `java.io.File` which is not a subclass
     * of `java.util.Collection` and which does not have any further subclasses (in
     * the JDK). I.e., the classes `java.io.File` and `java.util.Collection` are
     * not in an inheritance relationship. However, if the specified supertype would
     * be `java.util.List` the answer would be unknown.
     *
     * @note The function `isValueSubtypeOf` is not defined if `isNull` returns `Yes`;
     *      if `isNull` is `Unknown` then the result is given under the
     *      assumption that the value is not `null` at runtime.
     *      In other words, if this value represents `null` this method is not supported.
     * @note This method is expected to be overridden by subtypes.
     *
     * @return This default implementation always returns `Unknown`.
     */
    @throws[DomainException]("If this value is null (isNull.yes == true).")
    def isValueSubtypeOf(referenceType: ReferenceType): Answer = Unknown

    /**
     * Returns this reference value as a `DomainValue` of its original domain.
     *
     * @param domain The domain that was used to create this object can be used
     *      to get/create a DomainValue.
     */
    @throws[UnsupportedOperationException](
        "the given domain has to be equal to the domain that was used to creat this object"
    )
    def asDomainValue(implicit domain: Domain): domain.DomainReferenceValue

    final def asIsAReferenceValue: this.type = this
}

/**
 * Extractor for reference values.
 *
 * @author Michael Eichberg
 */
object IsAReferenceValue {

    def unapply(value: IsAReferenceValue): Option[UpperTypeBound] = Some(value.upperTypeBound)
}

/**
 * The value identifies one or more reference values. Additionally, it is possible
 * to get a representation that represents a summary of all underlying reference values.
 *
 * @author Michael Eichberg
 */
trait IsReferenceValue extends TypeInformation with IsAReferenceValue {

    /**
     * In general a domain value can represent several distinct values (depending
     * on the control flow).
     *
     * Each of these values can have a different upper bound and
     * an upper bound can in turn consist of several interfaces and a class.
     */
    def referenceValues: Traversable[IsAReferenceValue]

    final def unknown: Boolean = false

    final def isReferenceValue: Boolean = true

    final def isPrimitiveValue: Boolean = false

}

/**
 * Defines an extractor method for instances of `IsReferenceValue` objects.
 *
 * @author Michael Eichberg
 */
object IsReferenceValue {

    def unapply(value: IsReferenceValue): Option[Traversable[IsAReferenceValue]] = {
        Some(value.referenceValues)
    }
}

/**
 * Defines and extractor for the null-property of reference values.
 *
 * @author Michael Eichberg
 */
object IsNullValue {

    def unapply(rv: IsReferenceValue): Boolean = rv.isNull == Yes

}
