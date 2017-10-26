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
package l1

import scala.reflect.ClassTag

import org.opalj.br.Type
import org.opalj.br.BooleanType
import org.opalj.br.ByteType
import org.opalj.br.CharType
import org.opalj.br.ShortType
import org.opalj.br.IntegerType
import org.opalj.br.LongType
import org.opalj.br.FloatType
import org.opalj.br.DoubleType
import org.opalj.br.ReferenceType
import org.opalj.br.ObjectType
import org.opalj.br.FieldType
import org.opalj.br.MethodDescriptor

/**
 * Enables the tracking of concrete `Class` values.
 *
 * This class overrides `invokestatic` and only delegates to the default implementation
 * if it cannot successfully handle the call. Hence, this trait needs to be mixed in after
 * the trait that handles the default case but before all other traits that "just"
 * analyze invokestatic calls.
 * {{{
 * class MyDomain
 *  extends DefaultTypeLevelInvokeInstructions
 *  with ClassValues
 *  with <DOES ANAYLZE INVOKE CALLS>
 * }}}
 *
 * @author Michael Eichberg (fixes for multi-parameter Class.forName(...) calls)
 * @author Arne Lottmann
 */
trait ClassValues extends StringValues with FieldAccessesDomain with MethodCallsDomain {
    domain: CorrelationalDomain with IntegerValuesDomain with TypedValuesFactory with Configuration with TheClassHierarchy ⇒

    type DomainClassValue <: ClassValue with DomainObjectValue
    val DomainClassValue: ClassTag[DomainClassValue]

    /**
     * All values (`Class<...> c`) that represent the same type (e.g. `java.lang.String`)
     * are actually represented by the same class (object) value at runtime.
     */
    protected class ClassValue(origin: ValueOrigin, val value: Type, refId: RefId)
        extends SObjectValue(origin, No, true, ObjectType.Class, refId) {
        this: DomainClassValue ⇒

        override def doJoinWithNonNullValueWithSameOrigin(
            joinPC: PC,
            other:  DomainSingleOriginReferenceValue
        ): Update[DomainSingleOriginReferenceValue] = {

            other match {
                case that: ClassValue ⇒
                    if (this.value eq that.value)
                        // Recall: all instances are the same; i.e.,
                        // String.class "is reference equal to" Class.forName("java.lang.String")
                        NoUpdate
                    else
                        StructuralUpdate(ObjectValue(origin, No, true, ObjectType.Class, nextRefId()))
                case _ ⇒
                    val result = super.doJoinWithNonNullValueWithSameOrigin(joinPC, other)
                    if (result.isStructuralUpdate) {
                        result
                    } else {
                        // This (class) value and the other value may have a corresponding
                        // abstract representation (w.r.t. the next abstraction level!)
                        // but we still need to drop the concrete information.
                        StructuralUpdate(result.value.update())
                    }
            }
        }

        override def adapt(
            target:       TargetDomain,
            targetOrigin: ValueOrigin
        ): target.DomainValue = {
            target.ClassValue(targetOrigin, this.value)
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            if (this eq other)
                return true;

            other match {
                case that: ClassValue ⇒ that.value eq this.value
                case _                ⇒ false
            }
        }

        override def equals(other: Any): Boolean =
            other match {
                case cv: ClassValue ⇒ super.equals(other) && cv.value == this.value
                case _              ⇒ false
            }

        override protected def canEqual(other: SObjectValue): Boolean =
            other.isInstanceOf[ClassValue]

        override def hashCode: Int = super.hashCode + 71 * value.hashCode

        override def toString(): String = s"Class<${value.toJava}>[↦$origin;refId=$refId]"
    }

    // Needs to be implemented since the default implementation does not make sense here
    override def ClassValue(vo: ValueOrigin, value: Type): DomainObjectValue

    protected[l1] def simpleClassForNameCall(pc: PC, className: String): MethodCallResult = {
        if (className.length() == 0)
            return justThrows(ClassNotFoundException(pc));

        val classValue =
            try {
                ReferenceType(className.replace('.', '/'))
            } catch {
                case _: IllegalArgumentException ⇒
                    // if "className" is not a valid descriptor
                    return justThrows(ClassNotFoundException(pc));
            }

        if (classValue.isObjectType) {
            val objectType = classValue.asObjectType
            if (classHierarchy.isKnown(objectType) || !throwClassNotFoundException) {
                ComputedValue(ClassValue(pc, classValue))
            } else {
                val exception = Iterable(ClassNotFoundException(pc))
                ComputedValueOrException(ClassValue(pc, classValue), exception)
            }
        } else {
            val elementType = classValue.asArrayType.elementType
            if (elementType.isBaseType ||
                classHierarchy.isKnown(elementType.asObjectType) ||
                !throwClassNotFoundException) {
                ComputedValue(ClassValue(pc, classValue))
            } else {
                ComputedValueOrException(
                    ClassValue(pc, classValue),
                    Iterable(ClassNotFoundException(pc))
                )
            }
        }
    }

    abstract override def invokestatic(
        pc:               PC,
        declaringClass:   ObjectType,
        isInterface:      Boolean,
        name:             String,
        methodDescriptor: MethodDescriptor,
        operands:         Operands
    ): MethodCallResult = {

        import ClassValues._

        if ((declaringClass eq ObjectType.Class) && (name == "forName") && operands.nonEmpty) {

            operands.last match {
                case sv: StringValue ⇒
                    val value = sv.value
                    methodDescriptor match {
                        case `forName_String`                     ⇒ simpleClassForNameCall(pc, value)
                        case `forName_String_boolean_ClassLoader` ⇒ simpleClassForNameCall(pc, value)
                        case _ ⇒
                            throw new DomainException(
                                s"unsupported Class { ${methodDescriptor.toJava("forName")} }"
                            )
                    }

                case _ ⇒
                    // call default handler (the first argument is not a string)
                    super.invokestatic(pc, declaringClass, isInterface, name, methodDescriptor, operands)

            }
        } else {
            // call default handler
            super.invokestatic(pc, declaringClass, isInterface, name, methodDescriptor, operands)
        }
    }

    abstract override def getstatic(
        pc:             PC,
        declaringClass: ObjectType,
        name:           String,
        fieldType:      FieldType
    ) = {
        if (name == "TYPE") {
            declaringClass match {
                case ObjectType.Boolean   ⇒ ComputedValue(ClassValue(pc, BooleanType))
                case ObjectType.Byte      ⇒ ComputedValue(ClassValue(pc, ByteType))
                case ObjectType.Character ⇒ ComputedValue(ClassValue(pc, CharType))
                case ObjectType.Short     ⇒ ComputedValue(ClassValue(pc, ShortType))
                case ObjectType.Integer   ⇒ ComputedValue(ClassValue(pc, IntegerType))
                case ObjectType.Long      ⇒ ComputedValue(ClassValue(pc, LongType))
                case ObjectType.Float     ⇒ ComputedValue(ClassValue(pc, FloatType))
                case ObjectType.Double    ⇒ ComputedValue(ClassValue(pc, DoubleType))

                case _                    ⇒ super.getstatic(pc, declaringClass, name, fieldType)
            }
        } else {
            super.getstatic(pc, declaringClass, name, fieldType)
        }
    }

    object ClassValue {
        def unapply(value: DomainValue): Option[Type] = {
            value match {
                case classValue: ClassValue ⇒ Some(classValue.value)
                case _                      ⇒ None
            }
        }
    }
}

private object ClassValues {

    final val forName_String = MethodDescriptor(ObjectType.String, ObjectType.Class)

    final val forName_String_boolean_ClassLoader = {
        MethodDescriptor(
            IndexedSeq(ObjectType.String, BooleanType, ObjectType("java/lang/ClassLoader")),
            ObjectType.Class
        )
    }
}
