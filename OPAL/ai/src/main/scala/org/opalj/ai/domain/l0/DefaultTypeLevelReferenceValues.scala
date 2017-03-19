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

import org.opalj.collection.immutable.UIDSet
import org.opalj.collection.immutable.UIDSet0
import org.opalj.collection.immutable.UIDSet1
import org.opalj.br.ArrayType
import org.opalj.br.ObjectType
import org.opalj.br.ReferenceType

/**
 * Default implementation for handling reference values.
 *
 * @author Michael Eichberg
 */
trait DefaultTypeLevelReferenceValues
        extends DefaultDomainValueBinding
        with TypeLevelReferenceValues {
    domain: IntegerValuesDomain with TypedValuesFactory with Configuration with TheClassHierarchy ⇒

    // -----------------------------------------------------------------------------------
    //
    // REPRESENTATION OF REFERENCE VALUES
    //
    // -----------------------------------------------------------------------------------

    type DomainNullValue <: NullValue with AReferenceValue
    type DomainObjectValue <: ObjectValue with AReferenceValue // <= SObject.. and MObject...
    type DomainArrayValue <: ArrayValue with AReferenceValue

    protected[this] class NullValue extends super.NullValue { this: DomainNullValue ⇒

        override protected def doJoin(pc: PC, other: DomainValue): Update[DomainValue] = {
            other match {
                case _: NullValue ⇒ NoUpdate
                case _: ReferenceValue ⇒
                    // THIS domain does not track whether ReferenceValues
                    // are definitively not null!
                    StructuralUpdate(other)
            }
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            other.isInstanceOf[NullValue]
        }
    }

    /**
     * @param theUpperTypeBound The upper type bound of this array, which is necessarily
     *      precise if the element type of the array is a base type (primitive type).
     */
    protected[this] class ArrayValue(override val theUpperTypeBound: ArrayType)
            extends super.ArrayValue with SReferenceValue[ArrayType] {
        this: DomainArrayValue ⇒

        override def isValueSubtypeOf(supertype: ReferenceType): Answer = {
            val isSubtypeOf = domain.isSubtypeOf(theUpperTypeBound, supertype)
            isSubtypeOf match {
                case Yes ⇒ Yes
                case No if isPrecise ||
                    /* the array's supertypes: Object, Serializable and Cloneable
                     * are handled by domain.isSubtypeOf */
                    supertype.isObjectType ||
                    theUpperTypeBound.elementType.isBaseType ||
                    (
                        supertype.isArrayType &&
                        supertype.asArrayType.elementType.isBaseType &&
                        (
                            theUpperTypeBound.dimensions >= supertype.asArrayType.dimensions ||
                            (theUpperTypeBound.componentType ne ObjectType.Object)
                        )
                    ) ⇒ No
                case _ ⇒ Unknown
            }
        }

        override def isAssignable(value: DomainValue): Answer = {

            typeOfValue(value) match {

                case IsPrimitiveValue(primitiveType) ⇒
                    // The following is an over approximation that makes it theoretically
                    // possible to store an int value in a byte array. However,
                    // such bytecode is illegal
                    Answer(
                        theUpperTypeBound.componentType.computationalType eq
                            primitiveType.computationalType
                    )

                case elementValue @ IsAReferenceValue(UIDSet0) ⇒
                    // the elementValue is "null"
                    assert(elementValue.isNull.isYes)
                    // e.g., it is possible to store null in the n-1 dimensions of
                    // a n-dimensional array of primitive values
                    if (theUpperTypeBound.componentType.isReferenceType)
                        Yes
                    else
                        No

                case elementValue @ IsAReferenceValue(UIDSet1(elementValueType)) ⇒
                    classHierarchy.canBeStoredIn(
                        elementValueType,
                        elementValue.isPrecise,
                        this.theUpperTypeBound,
                        this.isPrecise
                    )

                case elementValue @ IsAReferenceValue(otherUpperTypeBound) ⇒
                    val elementValueIsPrecise = elementValue.isPrecise
                    val thisArrayType = this.theUpperTypeBound
                    val thisIsPrecise = this.isPrecise
                    var finalAnswer: Answer = No
                    otherUpperTypeBound.exists { elementValueType ⇒
                        classHierarchy.canBeStoredIn(
                            elementValueType,
                            elementValueIsPrecise,
                            thisArrayType,
                            thisIsPrecise
                        ) match {
                            case Yes ⇒
                                return Yes;

                            case intermediateAnswer ⇒
                                finalAnswer = finalAnswer join intermediateAnswer
                                false
                        }
                    }
                    finalAnswer
            }
        }

        override protected def doLoad(
            pc:                  PC,
            index:               DomainValue,
            potentialExceptions: ExceptionValues
        ): ArrayLoadResult = {
            val value = TypedValue(pc, theUpperTypeBound.componentType)
            ComputedValueOrException(value, potentialExceptions)
        }

        override protected def doStore(
            pc:               PC,
            value:            DomainValue,
            index:            DomainValue,
            thrownExceptions: ExceptionValues
        ): ArrayStoreResult =
            ComputationWithSideEffectOrException(thrownExceptions)

        // WIDENING OPERATION
        override protected def doJoin(joinPC: Int, other: DomainValue): Update[DomainValue] = {
            val thisUTB = this.theUpperTypeBound
            other match {

                case SObjectValue(thatUpperTypeBound) ⇒
                    classHierarchy.joinAnyArrayTypeWithObjectType(thatUpperTypeBound) match {
                        case UIDSet1(newUpperTypeBound) ⇒
                            if (newUpperTypeBound eq `thatUpperTypeBound`)
                                StructuralUpdate(other)
                            else
                                StructuralUpdate(ReferenceValue(joinPC, newUpperTypeBound))
                        case newUpperTypeBound ⇒
                            StructuralUpdate(ObjectValue(joinPC, newUpperTypeBound))
                    }

                case MObjectValue(thatUpperTypeBound) ⇒
                    classHierarchy.joinAnyArrayTypeWithMultipleTypesBound(thatUpperTypeBound) match {
                        case `thatUpperTypeBound` ⇒
                            StructuralUpdate(other)
                        case UIDSet1(newUpperTypeBound) ⇒
                            StructuralUpdate(ReferenceValue(joinPC, newUpperTypeBound))
                        case newUpperTypeBound ⇒
                            // this case should not occur...
                            StructuralUpdate(ObjectValue(joinPC, newUpperTypeBound))
                    }

                case ArrayValue(thatUpperTypeBound) ⇒
                    classHierarchy.joinArrayTypes(thisUTB, thatUpperTypeBound) match {
                        case Left(`thisUTB`) ⇒
                            NoUpdate
                        case Left(`thatUpperTypeBound`) ⇒
                            StructuralUpdate(other)
                        case Left(newUpperTypeBound) ⇒
                            StructuralUpdate(ArrayValue(joinPC, newUpperTypeBound))
                        case Right(newUpperTypeBound) ⇒
                            StructuralUpdate(ObjectValue(joinPC, newUpperTypeBound))
                    }

                case that: NullValue ⇒
                    NoUpdate
            }
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            other match {
                case _: NullValue ⇒ true
                case ArrayValue(thatUpperTypeBound) ⇒
                    domain.isSubtypeOf(thatUpperTypeBound, this.theUpperTypeBound).isYes
                case _ ⇒ false
            }
        }

        override def adapt(target: TargetDomain, origin: ValueOrigin): target.DomainValue = {
            target.ReferenceValue(origin, theUpperTypeBound)
        }
    }

    /**
     * Enables matching of `DomainValue`s that are array values.
     */
    object ArrayValue {
        def unapply(value: ArrayValue): Some[ArrayType] = Some(value.theUpperTypeBound)
    }

    protected trait ObjectValue extends super.ObjectValue { this: DomainObjectValue ⇒

        protected def asStructuralUpdate(
            pc:                PC,
            newUpperTypeBound: UIDSet[ObjectType]
        ): Update[DomainValue] = {
            if (newUpperTypeBound.size == 1)
                StructuralUpdate(ObjectValue(pc, newUpperTypeBound.first))
            else
                StructuralUpdate(ObjectValue(pc, newUpperTypeBound))
        }

        final override def load(pc: PC, index: DomainValue): ArrayLoadResult =
            throw DomainException("arrayload not possible; this is not an array value: "+this)

        final override def store(pc: PC, value: DomainValue, index: DomainValue): ArrayStoreResult =
            throw DomainException("arraystore not possible; this is not an array value: "+this)

        final override def length(pc: PC): Computation[DomainValue, ExceptionValue] =
            throw DomainException("arraylength not possible; this is not an array value: "+this)
    }

    protected class SObjectValue(
            override val theUpperTypeBound: ObjectType
    ) extends ObjectValue with SReferenceValue[ObjectType] { this: DomainObjectValue ⇒

        /**
         * @inheritdoc
         *
         * @note It is often not necessary to override this method as this method already
         *      takes the property whether the upper type bound '''is precise''' into
         *      account.
         */
        override def isValueSubtypeOf(supertype: ReferenceType): Answer = {
            val isSubtypeOf = domain.isSubtypeOf(theUpperTypeBound, supertype)
            val result = isSubtypeOf match {
                case Yes ⇒
                    Yes
                case No if isPrecise
                    || (
                        supertype.isArrayType &&
                        // and it is impossible that this value is actually an array...
                        (theUpperTypeBound ne ObjectType.Object) &&
                        (theUpperTypeBound ne ObjectType.Serializable) &&
                        (theUpperTypeBound ne ObjectType.Cloneable)
                    ) || (
                            // If both types represent class types and it is not
                            // possible that some value of this type may be a subtype
                            // of the given supertype, the answer "No" is correct.
                            supertype.isObjectType &&
                            classHierarchy.isKnown(supertype.asObjectType) &&
                            classHierarchy.isKnown(theUpperTypeBound) &&
                            classHierarchy.isInterface(supertype.asObjectType).isNo &&
                            classHierarchy.isInterface(theUpperTypeBound).isNo &&
                            domain.isSubtypeOf(supertype, theUpperTypeBound).isNo
                        ) ⇒
                    No
                case _ if isPrecise &&
                    // Note "reflexivity is already captured by the first isSubtypeOf call
                    domain.isSubtypeOf(supertype, theUpperTypeBound).isYes ⇒
                    Yes
                case _ ⇒
                    Unknown
            }
            result
        }

        // WIDENING OPERATION
        override protected def doJoin(pc: PC, other: DomainValue): Update[DomainValue] = {
            val thisUpperTypeBound = this.theUpperTypeBound
            other match {

                case SObjectValue(thatUpperTypeBound) ⇒
                    classHierarchy.joinObjectTypes(thisUpperTypeBound, thatUpperTypeBound, true) match {
                        case UIDSet1(newUpperTypeBound) ⇒
                            if (newUpperTypeBound eq `thisUpperTypeBound`)
                                NoUpdate
                            else if (newUpperTypeBound eq `thatUpperTypeBound`)
                                StructuralUpdate(other)
                            else
                                StructuralUpdate(ObjectValue(pc, newUpperTypeBound))
                        case newUpperTypeBound ⇒
                            StructuralUpdate(ObjectValue(pc, newUpperTypeBound))
                    }

                case MObjectValue(thatUpperTypeBound) ⇒
                    classHierarchy.joinObjectTypes(thisUpperTypeBound, thatUpperTypeBound, true) match {
                        case `thatUpperTypeBound` ⇒
                            StructuralUpdate(other)
                        case UIDSet1(`thisUpperTypeBound`) ⇒
                            NoUpdate
                        case newUpperTypeBound ⇒
                            asStructuralUpdate(pc, newUpperTypeBound)
                    }

                case that: ArrayValue ⇒
                    classHierarchy.joinAnyArrayTypeWithObjectType(thisUpperTypeBound) match {
                        case UIDSet1(newUpperTypeBound) ⇒
                            if (newUpperTypeBound eq `thisUpperTypeBound`)
                                NoUpdate
                            else
                                StructuralUpdate(ObjectValue(pc, newUpperTypeBound))
                        case newUpperTypeBound ⇒
                            StructuralUpdate(ObjectValue(pc, newUpperTypeBound))
                    }

                case that: NullValue ⇒
                    NoUpdate
            }
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            other match {
                case SObjectValue(thatUpperTypeBound) ⇒
                    domain.isSubtypeOf(thatUpperTypeBound, this.theUpperTypeBound).isYes
                case that: NullValue ⇒
                    true
                case ArrayValue(thatUpperTypeBound) ⇒
                    domain.isSubtypeOf(thatUpperTypeBound, this.theUpperTypeBound).isYes
                case MObjectValue(thatUpperTypeBound) ⇒
                    classHierarchy.isSubtypeOf(thatUpperTypeBound, this.theUpperTypeBound).isYes
            }
        }

        override def adapt(target: TargetDomain, origin: ValueOrigin): target.DomainValue = {
            target.ReferenceValue(origin, theUpperTypeBound)
        }

    }

    object SObjectValue {
        def unapply(that: SObjectValue): Some[ObjectType] = Some(that.theUpperTypeBound)
    }

    /**
     * @param upperTypeBound All types from which the (precise, but unknown) type of the
     *      represented value inherits. I.e., the value represented by this domain value
     *      is known to have a type that (in)directly inherits from all given types at
     *      the same time.
     */
    protected class MObjectValue(
            override val upperTypeBound: UIDSet[ObjectType]
    ) extends ObjectValue { value: DomainObjectValue ⇒

        assert(upperTypeBound.size > 1)

        override def referenceValues: Iterable[IsAReferenceValue] = Iterable(this)

        /**
         * Determines if this value is a subtype of the given supertype by
         * delegating to the `isSubtypeOf(ReferenceType,ReferenceType)` method of the
         * domain.
         *
         * @note This is a very basic implementation that cannot determine that this
         *      value is '''not''' a subtype of the given type as this implementation
         *      does not distinguish between class types and interface types.
         */
        override def isValueSubtypeOf(supertype: ReferenceType): Answer = {
            var isSubtypeOf: Answer = No
            upperTypeBound foreach { anUpperTypeBound ⇒
                domain.isSubtypeOf(anUpperTypeBound, supertype) match {
                    case Yes     ⇒ return Yes; // <= Shortcut evaluation
                    case Unknown ⇒ isSubtypeOf = Unknown
                    case No      ⇒ /*nothing to do*/
                }
            }
            /* No | Unknown*/
            // In general, we could check whether a type exists that is a
            // proper subtype of the type identified by this value's type bounds
            // and that is also a subtype of the given `supertype`.
            //
            // If such a type does not exist the answer is truly `no` (if we
            // assume that we know the complete type hierarchy);
            // if we don't know the complete hierarchy or if we currently
            // analyze a library the answer generally has to be `Unknown`
            // unless we also consider the classes that are final or ....

            isSubtypeOf match {
                // Yes is not possible here!

                case No if (
                    supertype.isArrayType &&
                    upperTypeBound != ObjectType.SerializableAndCloneable
                ) ⇒
                    // even if the upper bound is not precise we are now 100% sure
                    // that this value is not a subtype of the given supertype
                    No
                case _ ⇒
                    Unknown
            }
        }

        override protected def doJoin(pc: PC, other: DomainValue): Update[DomainValue] = {
            val thisUTB = this.upperTypeBound
            other match {

                case SObjectValue(thatUTB) ⇒
                    classHierarchy.joinObjectTypes(thatUTB, thisUTB, true) match {
                        case `thisUTB`          ⇒ NoUpdate
                        case UIDSet1(`thatUTB`) ⇒ StructuralUpdate(other)
                        case newUTB             ⇒ asStructuralUpdate(pc, newUTB)
                    }

                case MObjectValue(thatUTB) ⇒
                    classHierarchy.joinUpperTypeBounds(thisUTB, thatUTB, true) match {
                        case `thisUTB` ⇒ NoUpdate
                        case `thatUTB` ⇒ StructuralUpdate(other)
                        case newUTB    ⇒ asStructuralUpdate(pc, newUTB)
                    }

                case ArrayValue(_) ⇒
                    classHierarchy.joinAnyArrayTypeWithMultipleTypesBound(thisUTB) match {
                        case `thisUTB` ⇒ NoUpdate
                        case newUTB    ⇒ asStructuralUpdate(pc, newUTB)
                    }

                case that: NullValue ⇒
                    NoUpdate
            }
        }

        override def adapt(target: TargetDomain, origin: ValueOrigin): target.DomainValue =
            target match {
                case td: TypeLevelReferenceValues ⇒
                    td.ObjectValue(origin, upperTypeBound).asInstanceOf[target.DomainValue]
                case _ ⇒
                    super.adapt(target, origin)
            }

        override def summarize(origin: ValueOrigin): this.type = this

        override def toString: String = {
            upperTypeBound.map(_.toJava).mkString("ReferenceValue(", " with ", ")")
        }
    }

    object MObjectValue {
        def unapply(that: MObjectValue): Option[UIDSet[ObjectType]] = Some(that.upperTypeBound)
    }
}
