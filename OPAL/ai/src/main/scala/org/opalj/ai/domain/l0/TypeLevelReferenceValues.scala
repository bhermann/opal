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
package domain
package l0

import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.UIDSet
import org.opalj.collection.immutable.UIDSet1

import org.opalj.br.ArrayType
import org.opalj.br.FieldType
import org.opalj.br.ObjectType
import org.opalj.br.ReferenceType
import org.opalj.br.Type
import org.opalj.br.UpperTypeBound

/**
 * Implements the foundations for performing computations related to reference values.
 *
 * ==Extending/Implementing This Domain==
 * The following implementation decisions need to be taken into account when
 * inheriting from this trait:
 *  - By default equality of `DomainValue`s that represent reference values is
 *    reference based. I.e., two instances of `DomainValue`s that represent
 *    reference values are never equal. However, subclasses may implement their
 *    own strategy.
 *  - Instances of `DomainValue`s are always immutable or are at least considered and
 *    treated as immutable.
 *    Every update of a value's properties creates a new value. This is a general design
 *    decision underlying OPAL and should not be changed.
 *  - A new instance of a `DomainValue` is always exclusively created by one of the
 *    factory methods. (The factory methods generally start with a capital letter
 *    and are correspondingly documented.) This greatly facilitates domain adaptability
 *    and selective customizations.
 *
 * @author Michael Eichberg
 */
trait TypeLevelReferenceValues extends GeneralizedArrayHandling with AsJavaObject {
    domain: IntegerValuesDomain with Configuration with TheClassHierarchy ⇒

    /**
     * Merges those exceptions that have the same upper type bound. This ensures
     * that per upper type bound only one [[ValuesDomain.DomainValue]] (which may be a
     * `MultipleReferenceValues`) is used. For those values that are merged, the
     * given `pc` is used.
     */
    def mergeMultipleExceptionValues(
        pc:  PC,
        v1s: ExceptionValues,
        v2s: ExceptionValues
    ): ExceptionValues = {

        var v: List[ExceptionValue] = Nil
        var remainingv2s = v2s
        v1s foreach { v1 ⇒
            val v1UTB = domain.asObjectValue(v1).upperTypeBound
            remainingv2s find (domain.asObjectValue(_).upperTypeBound == v1UTB) match {
                case Some(v2) ⇒
                    remainingv2s = remainingv2s filterNot (_ == v2)
                    v = mergeDomainValues(pc, v1, v2).asInstanceOf[ExceptionValue] :: v
                case None ⇒
                    v = v1 :: v
            }
        }
        v ++ remainingv2s
    }

    /**
     * Merges two computations that both return some `DomainValue` and some
     * `ExceptionValues`. If values are merged the merged value will use the
     * specified `pc`.
     */
    protected[this] def mergeDEsComputations(
        pc: PC,
        c1: Computation[DomainValue, ExceptionValues],
        c2: Computation[DomainValue, ExceptionValues]
    ): Computation[DomainValue, ExceptionValues] = {

        c1 match {
            case ComputationWithResultAndException(r1, e1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(
                            mergeDomainValues(pc, r1, r2),
                            mergeMultipleExceptionValues(pc, e1, e2)
                        )
                    case ComputationWithResult(r2) ⇒
                        ComputedValueOrException(mergeDomainValues(pc, r1, r2), e1)
                    case ComputationWithException(e2) ⇒
                        ComputedValueOrException(r1, mergeMultipleExceptionValues(pc, e1, e2))
                }

            case ComputationWithResult(r1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(mergeDomainValues(pc, r1, r2), e2)
                    case ComputationWithResult(r2) ⇒
                        ComputedValue(mergeDomainValues(pc, r1, r2))
                    case ComputationWithException(e2) ⇒
                        ComputedValueOrException(r1, e2)
                }

            case ComputationWithException(e1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(r2, mergeMultipleExceptionValues(pc, e1, e2))
                    case ComputationWithResult(r2) ⇒
                        ComputedValueOrException(r2, e1)
                    case ComputationWithException(e2) ⇒
                        ThrowsException(mergeMultipleExceptionValues(pc, e1, e2))
                }
        }
    }

    /**
     * Merges two computations that both resulted in at most one `ExceptionValue` each.
     *
     * If values are merged the merged value will use the specified `pc`.
     */
    protected[this] def mergeEsComputations(
        pc: PC,
        c1: Computation[Nothing, ExceptionValues],
        c2: Computation[Nothing, ExceptionValues]
    ): Computation[Nothing, ExceptionValues] = {

        (c1, c2) match {
            case (ComputationWithException(e1), ComputationWithException(e2)) ⇒
                ComputationWithSideEffectOrException(mergeMultipleExceptionValues(pc, e1, e2))
            case (ComputationWithException(e1), _ /*ComputationWithoutException*/ ) ⇒
                c1
            case (_ /*ComputationWithoutException*/ , ComputationWithException(e2)) ⇒
                c2
            case _ ⇒
                ComputationWithSideEffectOnly
        }
    }

    /**
     * Merges two computations that both resulted in at most one `DomainValue` or
     * at most one `ExceptionValue`.
     *
     * If values are merged the merged value will use the specified `pc`.
     */
    protected[this] def mergeDEComputations(
        pc: PC,
        c1: Computation[DomainValue, ExceptionValue],
        c2: Computation[DomainValue, ExceptionValue]
    ): Computation[DomainValue, ExceptionValue] = {

        c1 match {
            case ComputationWithResultAndException(r1, e1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(
                            mergeDomainValues(pc, r1, r2) /*Value*/ ,
                            mergeDomainValues(pc, e1, e2).asInstanceOf[ExceptionValue]
                        )
                    case ComputationWithResult(r2) ⇒
                        ComputedValueOrException(mergeDomainValues(pc, r1, r2), e1)
                    case ComputationWithException(e2) ⇒
                        ComputedValueOrException(
                            r1,
                            mergeDomainValues(pc, e1, e2).asInstanceOf[ExceptionValue]
                        )
                }

            case ComputationWithResult(r1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(mergeDomainValues(pc, r1, r2), e2)
                    case ComputationWithResult(r2) ⇒
                        ComputedValue(mergeDomainValues(pc, r1, r2))
                    case ComputationWithException(e2) ⇒
                        ComputedValueOrException(r1, e2)
                }

            case ComputationWithException(e1) ⇒
                c2 match {
                    case ComputationWithResultAndException(r2, e2) ⇒
                        ComputedValueOrException(
                            r2,
                            mergeDomainValues(pc, e1, e2).asInstanceOf[ExceptionValue]
                        )
                    case ComputationWithResult(r2) ⇒
                        ComputedValueOrException(r2, e1)
                    case ComputationWithException(e2) ⇒
                        ThrowsException(
                            mergeDomainValues(pc, e1, e2).asInstanceOf[ExceptionValue]
                        )
                }
        }
    }

    // ---------------------------------------------------------------------------------------------
    //
    // REPRESENTATION OF REFERENCE VALUES
    //
    // ---------------------------------------------------------------------------------------------

    type AReferenceValue <: DomainReferenceValue with ReferenceValue

    type DomainObjectValue <: ObjectValue with AReferenceValue

    type DomainArrayValue <: ArrayValue with AReferenceValue

    type DomainNullValue <: NullValue with AReferenceValue

    trait ArrayAbstraction {

        def load(pc: PC, index: DomainValue): ArrayLoadResult

        def store(pc: PC, value: DomainValue, index: DomainValue): ArrayStoreResult

        def length(pc: PC): Computation[DomainValue, ExceptionValue]
    }

    /**
     * Abstracts over all values with computational type `reference`. I.e.,
     * abstracts over class and array values and also the `null` value.
     */
    trait ReferenceValue extends super.ReferenceValue with IsReferenceValue with ArrayAbstraction {
        this: AReferenceValue ⇒

        override def valueType: Option[ReferenceType] = {
            upperTypeBound.size match {
                case 0 ⇒ None /* only null has an empty upper type bound */
                case 1 ⇒ Some(upperTypeBound.head)
                case 2 ⇒ Some(classHierarchy.joinReferenceTypesUntilSingleUpperBound(upperTypeBound))
            }
        }

        override def isPrecise: Boolean = {
            upperTypeBound match {
                case UIDSet1(theUpperTypeBound) ⇒ classHierarchy.isKnownToBeFinal(theUpperTypeBound)
                case _                          ⇒ false
            }
        }

        final override def asDomainValue(
            implicit
            targetDomain: Domain
        ): targetDomain.DomainReferenceValue = {
            if (targetDomain ne domain)
                throw new UnsupportedOperationException(
                    "the given domain has to be equal to this value's domain"
                )
            this.asInstanceOf[targetDomain.DomainReferenceValue]
        }
    }

    /**
     * A reference value with a single (upper) type (bound).
     */
    protected[this] trait SReferenceValue[T <: ReferenceType] { this: AReferenceValue ⇒

        val theUpperTypeBound: T

        final override def valueType: Some[ReferenceType] = Some(theUpperTypeBound)

        final override def referenceValues: Iterable[IsAReferenceValue] = Iterable(this)

        final override def upperTypeBound: UpperTypeBound = new UIDSet1(theUpperTypeBound)

        final override def summarize(pc: PC): this.type = this

        override def toString: String = theUpperTypeBound.toJava

    }

    /**
     * Represents the runtime value `null`. Null values are basically found in the
     * following two cases:
     *  1. The value `null` was pushed onto the stack using `aconst_null`.
     *  1. A reference value that is not guaranteed to be non-null is tested against
     *    `null` using `ifnull` or `ifnonnull` and we are now on the branch where
     *    the value has to be `null`.
     */
    protected trait NullValue extends ReferenceValue { this: DomainNullValue ⇒

        final override def valueType: None.type = None

        /** Returns an empty upper type bound. */
        final override def upperTypeBound: UpperTypeBound = UIDSet.empty

        final override def referenceValues: Iterable[IsAReferenceValue] = Iterable(this)

        /** Returns `Yes`. */
        final override def isNull: Answer = Yes

        /** Returns `true`. */
        final override def isPrecise: Boolean = true

        // IMPLEMENTATION OF THE ARRAY RELATED METHODS
        //

        final override def load(pc: PC, index: DomainValue): ArrayLoadResult =
            justThrows(VMNullPointerException(pc))

        final override def store(
            pc:    PC,
            value: DomainValue,
            index: DomainValue
        ): ArrayStoreResult = {
            justThrows(VMNullPointerException(pc))
        }

        final override def length(pc: PC): Computation[DomainValue, ExceptionValue] = {
            throws(VMNullPointerException(pc))
        }

        /**
         * Always throws a [[DomainException]] since it is not possible to give a generic
         * answer. The answer depends on the context (instanceof/classcast/...)).
         *
         * @return Throws a `DomainException` that states that this method is not supported.
         */
        @throws[DomainException]("Always - isValueSubtypeOf is not defined on \"null\" values.")
        final override def isValueSubtypeOf(referenceType: ReferenceType): Nothing = {
            throw DomainException("\"isValueSubtypeOf\" is not defined on \"null\" values")
        }

        override def summarize(pc: PC): this.type = this

        override def adapt(target: TargetDomain, pc: PC): target.DomainValue = target.NullValue(pc)

        override def toString: String = "ReferenceValue(null)"
    }

    /**
     * Represents a class/interface value which may have a single class and/or
     * multiple interfaces as its upper type bound.
     */
    protected[this] trait ObjectValue extends ReferenceValue { this: DomainObjectValue ⇒

    }

    /**
     * Represents an array value.
     */
    protected[this] trait ArrayValue extends ReferenceValue { this: DomainArrayValue ⇒

        /**
         * Returns `Yes` if we can statically determine that the given value can
         * be stored in the array represented by this `ArrayValue`.
         */
        /*ABSTRACT*/ def isAssignable(value: DomainValue): Answer

        /**
         * Called by the load method if the index is potentially valid.
         */
        /*ABSTRACT*/ protected def doLoad(
            pc:                  PC,
            index:               DomainValue,
            potentialExceptions: ExceptionValues
        ): ArrayLoadResult

        /**
         * @note It is in general not necessary to override this method. If you need some
         *      special handling if a value is loaded from an array, override the method
         *      [[doLoad]].
         */
        override def load(pc: PC, index: DomainValue): ArrayLoadResult = {
            // The case "this.isNull == Yes" will not occur as the value "null" is always
            // represented by an instance of the respective class.

            val isIndexValid =
                length.map((l: Int) ⇒ intIsSomeValueInRange(pc, index, 0, l - 1)).
                    getOrElse {
                        if (intIsLessThan0(pc, index).isYes)
                            No
                        else
                            Unknown // the index may be too large...
                    }
            if (isIndexValid.isNo)
                return justThrows(VMArrayIndexOutOfBoundsException(pc))

            var thrownExceptions: List[ExceptionValue] = Nil
            if (isNull.isUnknown && throwNullPointerExceptionOnArrayAccess)
                thrownExceptions = VMNullPointerException(pc) :: thrownExceptions
            if (isIndexValid.isUnknown && throwArrayIndexOutOfBoundsException)
                thrownExceptions = VMArrayIndexOutOfBoundsException(pc) :: thrownExceptions
            doLoad(pc, index, thrownExceptions)
        }

        /**
         * Called by the store method if the value is potentially assignable and if
         * the index is potentially valid.
         */
        /*ABSTRACT*/ protected def doStore(
            pc:               PC,
            value:            DomainValue,
            index:            DomainValue,
            thrownExceptions: ExceptionValues
        ): ArrayStoreResult

        /**
         * @note It is in general not necessary to override this method. If you need some
         *      special handling if a value is stored in an array, override the method
         *      [[doStore]].
         */
        override def store(
            pc:    PC,
            value: DomainValue,
            index: DomainValue
        ): ArrayStoreResult = {
            // @note
            // The case "this.isNull == Yes" will not occur as the value "null" is always
            // represented by an instance of the respective class

            val isIndexValid =
                length.map((l: Int) ⇒ intIsSomeValueInRange(pc, index, 0, l - 1)).
                    getOrElse(
                        if (intIsLessThan0(pc, index).isYes)
                            No
                        else
                            Unknown
                    )
            if (isIndexValid.isNo)
                return justThrows(VMArrayIndexOutOfBoundsException(pc))

            val isAssignable = this.isAssignable(value)
            if (isAssignable.isNo)
                return justThrows(VMArrayStoreException(pc))

            var thrownExceptions: List[ExceptionValue] = Nil
            if (isIndexValid.isUnknown && throwArrayIndexOutOfBoundsException)
                thrownExceptions = VMArrayIndexOutOfBoundsException(pc) :: thrownExceptions
            if (isAssignable.isUnknown && throwArrayStoreException)
                thrownExceptions = VMArrayStoreException(pc) :: thrownExceptions
            if (isNull.isUnknown && throwNullPointerExceptionOnArrayAccess)
                thrownExceptions = VMNullPointerException(pc) :: thrownExceptions

            doStore(pc, value, index, thrownExceptions)
        }

        /**
         * Returns the length of this array, if this information is available.
         */
        def length: Option[Int] = None

        final def doGetLength(pc: PC): DomainValue =
            length.map(IntegerValue(pc, _)).getOrElse(IntegerValue(pc))

        override def length(pc: PC): Computation[DomainValue, ExceptionValue] = {
            if (isNull == Unknown && throwNullPointerExceptionOnArrayAccess)
                ComputedValueOrException(doGetLength(pc), VMNullPointerException(pc))
            else
                ComputedValue(doGetLength(pc))
        }
    }

    /**
     * Returns the given value as a [[DomainReferenceValue]]. Basically just performs
     * a type cast and is intended to be used to communicate that the value has
     * to be a reference value (if the underlying byte code is valid.)
     */
    def asReferenceValue(value: DomainValue): AReferenceValue = value.asInstanceOf[AReferenceValue]

    def asObjectValue(value: DomainValue): DomainObjectValue = value.asInstanceOf[DomainObjectValue]

    def asArrayAbstraction(value: DomainValue): ArrayAbstraction = {
        value.asInstanceOf[ArrayAbstraction]
    }

    // -----------------------------------------------------------------------------------
    //
    // QUESTION'S ABOUT VALUES
    //
    // -----------------------------------------------------------------------------------

    /**
     * Tests if both values refer to the same object instance.
     *
     * Though this is in general intractable, there are some cases where a definitive
     * answer is possible.
     *
     * This implementation completely handles the case where at least one value
     * definitively represents the `null` value.
     * Additionally, if we have precise type information and the types are different,
     * `No` is returned. Otherwise, `Unknown` is returned.
     *
     * @note This method is intended to be overridden by subclasses and may be the first
     *      one that is called (by means of `super`) by the overriding method to handle checks
     *      related to null. E.g.
     *      {{{
     *      super.areEqualReferences(value1,value2).ifUnknown {
     *          ...
     *      }
     *      }}}
     *
     * @param value1 A value of type `ReferenceValue`.
     * @param value2 A value of type `ReferenceValue`.
     */
    override def refAreEqual(pc: PC, value1: DomainValue, value2: DomainValue): Answer = {
        val v1 = asReferenceValue(value1)
        val v2 = asReferenceValue(value2)
        val value1IsNull = v1.isNull
        val value2IsNull = v2.isNull
        if (value1IsNull.isYes)
            // the answer is unknown if the second value is unknown, no if the second
            // value is no and yes if the second value is also yes
            value2IsNull
        else if (value2IsNull.isYes)
            // value1IsNull is either No or unknown, both represents the correct answer
            value1IsNull
        else {
            val v1UTB = v1.upperTypeBound
            val v2UTB = v2.upperTypeBound
            if (v1.isPrecise && v2.isPrecise)
                if (v1UTB != v2UTB)
                    // two objects with different runtime types are never equal
                    No
                else
                    // though both values have the same runtime type, we don't know
                    // if they refer to the same object
                    Unknown
            else {
                val ch = classHierarchy
                // - both values may not be null
                // - at least one value is not precise
                if (ch.isSubtypeOf(v1UTB, v2UTB).isNo &&
                    ch.isSubtypeOf(v2UTB, v1UTB).isNo &&
                    // two interfaces that are not in an inheritance relation can
                    // still be implemented by the same class and, hence, the references
                    // can still be equal
                    v1UTB.exists(t ⇒ t.isObjectType && ch.isInterface(t.asObjectType).isNo) &&
                    v2UTB.exists(t ⇒ t.isObjectType && ch.isInterface(t.asObjectType).isNo))
                    No
                else
                    Unknown
            }
        }
    }

    final override def isValueSubtypeOf(value: DomainValue, supertype: ReferenceType): Answer = {
        asReferenceValue(value).isValueSubtypeOf(supertype)
    }

    /**
     * Determines the nullness-property of the given value.
     *
     * @param value A value of type `ReferenceValue`.
     */
    final override def refIsNull(pc: PC, value: DomainValue): Answer = {
        asReferenceValue(value).isNull
    }

    // -----------------------------------------------------------------------------------
    //
    // ARRAY RELATED OPERATIONS
    //
    // -----------------------------------------------------------------------------------

    //
    // CREATE ARRAY
    //

    /**
     * Creates a new array.
     *
     * @note It is generally not necessary to override this method as it handles all
     *      cases in a generic manner.
     */
    override def newarray(
        pc:            PC,
        count:         DomainValue,
        componentType: FieldType
    ): Computation[DomainValue, ExceptionValue] = {
        val validCount = intIsSomeValueInRange(pc, count, 0, Int.MaxValue)
        if (validCount.isNo)
            return throws(VMNegativeArraySizeException(pc))

        val newarray = NewArray(pc, count, ArrayType(componentType))
        if (validCount.isUnknown && throwNegativeArraySizeException)
            ComputedValueOrException(newarray, VMNegativeArraySizeException(pc))
        else
            ComputedValue(newarray)
    }

    /**
     * Creates a multi-dimensional array.
     *
     * @note The componentType may be (again) an array type.
     * @note It is generally not necessary to override this method as it handles all
     *      cases in a generic manner.
     */
    override def multianewarray(
        pc:        PC,
        counts:    Operands,
        arrayType: ArrayType
    ): Computation[DomainArrayValue, ExceptionValue] = {
        var validCounts: Answer = Yes
        counts foreach { (count) ⇒
            val validCount = intIsSomeValueInRange(pc, count, 0, Int.MaxValue)
            if (validCount.isNo)
                return throws(VMNegativeArraySizeException(pc))
            else if (validCount.isUnknown)
                validCounts = Unknown
        }

        val newarray =
            if (counts.tail.isEmpty)
                NewArray(pc, counts.head, arrayType)
            else
                NewArray(pc, counts, arrayType)
        if (validCounts.isUnknown && throwNegativeArraySizeException)
            ComputedValueOrException(newarray, VMNegativeArraySizeException(pc))
        else
            ComputedValue(newarray)
    }

    //
    // OPERATIONS ON ARRAYS
    //

    /**
     * Loads the value stored in the array at the given index or throws an
     * exception (`NullPointerException` or `IndexOutOfBoundsException`).
     *
     * @note It is in general not necessary to override this method. If you need
     *      some special handling refine the `load` method defined by the trait
     *      `ArrayValue`.
     */
    override def arrayload(
        pc:       PC,
        index:    DomainValue,
        arrayref: DomainValue
    ): ArrayLoadResult = {
        // if the bytecode is valid, the type cast (asArrayValue) is safe
        asArrayAbstraction(arrayref).load(pc, index)
    }

    /**
     * Stores the given value in the array at the given index or throws an exception
     * (`NullPointerException`, `ArrayStoreException` or `IndexOutOfBoundsException`).
     *
     * @note It is in general not necessary to override this method. If you need
     *      some special handling refine the `store` method defined by the trait
     *      `ArrayValue`.
     */
    override def arraystore(
        pc:       PC,
        value:    DomainValue,
        index:    DomainValue,
        arrayref: DomainValue
    ): ArrayStoreResult = {
        // if the bytecode is valid, the type cast (asArrayValue) is safe
        asArrayAbstraction(arrayref).store(pc, value, index)
    }

    /**
     * Returns the array's length or throws a `NullPointerException` if the given
     * reference is `null`.
     *
     * @note It is in general not necessary to override this method. If you need
     *      some special handling refine the trait `ArrayValue`.
     */
    override def arraylength(
        pc:       PC,
        arrayref: DomainValue
    ): Computation[DomainValue, ExceptionValue] = {
        asArrayAbstraction(arrayref).length(pc)
    }

    // -----------------------------------------------------------------------------------
    //
    // EXTRACTORS
    //
    // -----------------------------------------------------------------------------------

    object IsNull {
        def unapply(value: AReferenceValue): Some[Answer] = Some(value.isNull)
    }

    object IsPrecise {
        def unapply(value: AReferenceValue): Some[Boolean] = Some(value.isPrecise)
    }

    object UpperTypeBound {
        def unapply(value: AReferenceValue): Some[UIDSet[_ <: ReferenceType]] = {
            Some(value.upperTypeBound)
        }
    }

    // -----------------------------------------------------------------------------------
    //
    // FACTORY METHODS
    //
    // -----------------------------------------------------------------------------------

    //
    // REFINEMENT OF EXISTING DOMAIN VALUE FACTORY METHODS
    //

    override def NullValue(pc: PC): DomainNullValue

    override def ReferenceValue(
        pc:             PC,
        upperTypeBound: ReferenceType
    ): AReferenceValue = {
        if (upperTypeBound.isArrayType)
            ArrayValue(pc, upperTypeBound.asArrayType)
        else
            ObjectValue(pc, upperTypeBound.asObjectType)
    }

    override def NonNullObjectValue(pc: PC, objectType: ObjectType): DomainObjectValue = {
        ObjectValue(pc, objectType)
    }

    override def NewObject(pc: PC, objectType: ObjectType): DomainObjectValue = {
        ObjectValue(pc, objectType)
    }

    override def InitializedObjectValue(pc: PC, objectType: ObjectType): DomainObjectValue = {
        ObjectValue(pc, objectType)
    }

    override def StringValue(pc: PC, value: String): DomainObjectValue = {
        ObjectValue(pc, ObjectType.String)
    }

    override def ClassValue(pc: PC, t: Type): DomainObjectValue = {
        ObjectValue(pc, ObjectType.Class)
    }

    override def InitializedArrayValue(
        pc:        PC,
        arrayType: ArrayType,
        counts:    Chain[Int]
    ): DomainArrayValue = {
        ArrayValue(pc, arrayType)
    }

    //
    // DECLARATION OF ADDITIONAL DOMAIN VALUE FACTORY METHODS
    //

    /**
     * Factory method to create a `DomainValue` that represents ''either an class-/interface
     * value that has the given type or the value `null`''. However, the
     * information whether the value is `null` or not is not available. Furthermore, the
     * type may also just be an upper bound and it is not known if the value is
     * properly initialized.
     *
     * ==Summary==
     * The properties of the domain value are:
     *
     *  - Initialized: '''Unknown'''
     *  - Type: '''Upper Bound'''
     *  - Null: '''Unknown'''
     *  - Content: '''Unknown'''
     */
    def ObjectValue(pc: PC, objectType: ObjectType): DomainObjectValue

    /**
     * Factory method to create a `DomainValue` that represents ''either an class-/interface
     * value that has the given types as an upper bound or the value `null`''. However, the
     * information whether the value is `null` or not is not available. Furthermore, the
     * type may also just be an upper bound and it is not known if the value is
     * properly initialized.
     *
     * ==Summary==
     * The properties of the domain value are:
     *
     *  - Initialized: '''Unknown''' (i.e., it is not guaranteed that the constructor was called.)
     *  - Type: '''Upper Bound'''
     *  - Null: '''Unknown'''
     *  - Content: '''Unknown'''
     */
    def ObjectValue(pc: PC, upperTypeBound: UIDSet[ObjectType]): DomainObjectValue

    /**
     * Factory method to create a new domain value that represents a newly created
     * array (non-null) with the size determined by count that is empty.
     *
     * ==Typical Usage==
     * This factory method is (implicitly) used, e.g., by OPAL when a `newarray`
     * instruction is found.
     *
     * ==Summary==
     * The properties of the value are:
     *
     *  - Type: '''Precise'''
     *  - Null: '''No'''
     *  - Size: '''Count'''
     *  - Content: '''Empty''' (i.e., default values w.r.t. to the array's component type)
     */
    def NewArray(pc: PC, count: DomainValue, arrayType: ArrayType): DomainArrayValue = {
        ArrayValue(pc, arrayType)
    }

    /**
     * Factory method to create a new domain value that represents a newly created
     * array (non-null) with the size determined by count that is empty.
     *
     * ==Typical Usage==
     * This factory method is (implicitly) used, e.g., by OPAL when a
     * `multianewarray` instruction is found.
     *
     * ==Summary==
     * The properties of the value are:
     *
     *  - Type: '''Precise'''
     *  - Null: '''No'''
     *  - Size: '''Depending on the values in `counts`'''
     *  - Content: '''Empty''' (i.e., default values w.r.t. to the array's component type)
     */
    def NewArray(pc: PC, counts: Operands, arrayType: ArrayType): DomainArrayValue = {
        ArrayValue(pc, arrayType)
    }

    /**
     * Creates a new `DomainValue` that represents an array value with unknown
     * values and where the specified type may also just be an upper type bound
     * (unless the component type is a primitive type or an array of primitives.)
     *
     * ==Typical Usage==
     * This factory method is (typically) used to create a domain value that represents
     * an array if we know nothing specific about the array. E.g., if you want to
     * analyze a method that takes an array as a parameter.
     *
     * ==Summary==
     * The properties of the value are:
     *  - Type: '''Upper Bound''' (unless the elementType is a base type)
     *  - Null: '''Unknown'''
     *  - Size: '''Unknown'''
     *  - Content: '''Unknown'''
     *
     * @note Java's arrays are co-variant. I.e., `Object[] a = new Serializable[100];` is valid.
     */
    protected def ArrayValue(pc: PC, arrayType: ArrayType): DomainArrayValue

    // -----------------------------------------------------------------------------------
    //
    // HANDLING OF CONSTRAINTS
    //
    // -----------------------------------------------------------------------------------

    abstract override def toJavaObject(pc: PC, value: DomainValue): Option[Object] = {
        value match {
            case _: NullValue ⇒ Some(null)
            case _            ⇒ super.toJavaObject(pc, value)
        }
    }

    // This domain does not support the propagation of constraints, since
    // the join operator reuses the current domain value (the same instance)
    // if its properties are correctly abstracting over the current state. Hence,
    // the same domain value is used to potentially represent different objects at
    // runtime/this domain does not support the identification of aliases.

    def refSetUpperTypeBoundOfTopOperand(
        pc:             PC,
        upperTypeBound: ReferenceType,
        operands:       Operands,
        locals:         Locals
    ): (Operands, Locals) = {
        (ReferenceValue(pc, upperTypeBound) :&: operands.tail, locals)
    }

    override def refTopOperandIsNull(
        pc:       PC,
        operands: Operands,
        locals:   Locals
    ): (Operands, Locals) = {
        (NullValue(pc /*Irrelevant - at least here*/ ) :&: operands.tail, locals)
    }

}
