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

import org.opalj.br.Type
import org.opalj.br.ReferenceType
import org.opalj.br.ObjectType
import org.opalj.br.MethodDescriptor
import org.opalj.br.MethodHandle

/**
 * Definition of factory methods to create `ReferenceValues`.
 *
 * @author Michael Eichberg (eichberg@informatik.tu-darmstadt.de)
 */
trait ReferenceValuesFactory extends ExceptionsFactory { domain ⇒

    /**
     * Factory method to create a `DomainValue` that represents value `null` and
     * and that was created (explicitly or implicitly) by the instruction
     * with the specified program counter.
     *
     * The domain may ignore the information about the value and the origin (`pc`).
     *
     * ==Summary==
     * The properties of the domain value are:
     *
     *  - Initialized: N/A
     *  - Type: '''Null'''
     *  - Null: '''Yes'''
     */
    def NullValue(origin: ValueOrigin): DomainReferenceValue

    /**
     * Factory method to create a `DomainValue` that represents ''either a reference
     * value that has the given type and is initialized or the value `null`''. However, the
     * information whether the value is `null` or not is not available. Furthermore, the
     * type may also just be an upper bound.
     *
     * The domain may ignore the information about the value and the origin, but
     * it has to remain possible for the domain to identify the component type of an
     * array.
     *
     * ==Summary==
     * The properties of the domain value are:
     *
     *  - Initialized: '''Yes''' (if non-null the constructor was called/the array was initialized)
     *  - Type: '''Upper Bound'''
     *  - Null: '''Unknown'''
     *  - Content: '''Unknown'''
     */
    def ReferenceValue(origin: ValueOrigin, referenceType: ReferenceType): DomainReferenceValue

    /**
     * Represents ''a non-null reference value with the given type as an upper type bound''.
     *
     * The domain may ignore the information about the value and the origin (`vo`).
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''Yes''' (the constructor was called)
     *  - Type: '''Upper Bound'''
     *  - Null: '''No''' (This value is not `null`.)
     */
    def NonNullObjectValue(origin: ValueOrigin, objectType: ObjectType): DomainReferenceValue

    /**
     * Creates a new `DomainValue` that represents ''a new,
     * uninitialized instance of an object of the given type''. The object was
     * created by the (`NEW`) instruction with the specified program counter or - if value
     * origin is -1 - represents this in a constructor call before the call of the super
     * constructor.
     *
     * OPAL calls this method when it evaluates `newobject` instructions.
     * If the bytecode is valid a call of one of the (super) object's constructors will
     * subsequently initialize the object.
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''no''' (only the memory is allocated for the object)
     *  - Type: '''precise''' (i.e., this type is not an upper bound,
     *      the type correctly models the runtime type.)
     *  - Null: '''no''' (This value is not `null`.)
     *
     * @note Instances of arrays are created by the `newarray` and
     *      `multianewarray` instructions and in both cases an exception may be thrown
     *      (e.g., `NegativeArraySizeException`).
     */
    def NewObject(origin: ValueOrigin, objectType: ObjectType): DomainReferenceValue

    /**
     * Creates a new `DomainValue` that represents ''the `this` value of a constructor before the
     * super constructor is called. Hence, the value origin is necessarily always -1.
     *
     * OPAL calls this method when it creates the initial locals for constructors.
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''no''' (only the memory is allocated for the object)
     *  - Type: '''upper bound'''
     *  - Null: '''no''' (This value is not `null`.)
     *
     * @note Instances of arrays are never uninitialized.
     */
    def UninitializedThis(objectType: ObjectType): DomainReferenceValue

    /**
     * Factory method to create a `DomainValue` that represents an '''initialized'''
     * reference value of the given type and that was created (explicitly or implicitly)
     * by the instruction with the specified program counter.
     *
     * ==General Remarks==
     * The given type usually identifies a class type (not an interface type) that is
     * not abstract, but in some cases (e.g. consider `java.awt.Toolkit()`)
     * it may be useful/meaningful to relax this requirement and to state that the
     * class precisely represents the runtime type – even
     * so the class is abstract. However, such decisions need to be made by the domain.
     *
     * This method is used by the OPAL framework to create reference values that are normally
     * internally created by the JVM (in particular exceptions such as
     * `NullPointExeception` and `ClassCastException`). However, it can generally
     * be used to create initialized objects/arrays.
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''Yes'''
     *  - Type: '''precise''' (i.e., this type is not an upper bound, the type
     *      correctly models the runtime type.)
     *  - Null: '''No''' (This value is not `null`.)
     */
    def InitializedObjectValue(origin: ValueOrigin, objectType: ObjectType): DomainReferenceValue

    /**
     * Factory method to create a `DomainValue` that represents the given string value
     * and that was created by the instruction with the specified program counter.
     *
     * This function is called by OPAL-AI when a string constant (`LDC(_W)` instruction) is
     * put on the stack.
     *
     * The domain may ignore the information about the value and the origin (`vo`).
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''Yes''' and the String's value is the given value. The string
     *      may be empty (""), but it is never `null`.
     *  - Type: '''java.lang.String'''
     *  - Null: '''No'''
     *
     * @param value A non-null string. (The string may be empty, though.)
     */
    def StringValue(origin: ValueOrigin, value: String): DomainReferenceValue

    /**
     * Factory method to create a `DomainValue` that represents a runtime value of
     * type "`Class&lt;T&gt;`" and that was created by the instruction with the
     * specified program counter.
     *
     * This function is called by OPAL when a class constant (`LDC(_W)` instruction) is
     * put on the stack.
     *
     * The domain may ignore the information about the value and the origin (`vo`).
     *
     * ==Summary==
     * The properties of the domain value are:
     *  - Initialized: '''Yes''' and the type represented by the class is the given type.
     *  - Type: '''java.lang.Class<t:Type>'''
     *  - Null: '''No'''
     */
    def ClassValue(origin: ValueOrigin, t: Type): DomainReferenceValue

    /**
     * Called by the AI framework for each ''load constant method handle''
     * ([[org.opalj.br.instructions.LoadMethodHandle]]) instruction to
     * get a representation of/a DomainValue that represents the handle.
     *
     * @param handle A valid method handle.
     * @return An `InitializedObjectValue(ObjectType.MethodHandle)`.
     *      Hence, this method needs to be overridden
     *      if resolution of MethodHandle based method calls should be performed.
     */
    def MethodHandle(origin: ValueOrigin, handle: MethodHandle): DomainReferenceValue = {
        InitializedObjectValue(origin, ObjectType.MethodHandle)
    }

    /**
     * Called by the framework for each ''load constant method type''
     * ([[org.opalj.br.instructions.LoadMethodType]]) instruction to
     * get a domain-specific representation of the method descriptor as a `MethodType`.
     *
     * @param descriptor A valid method descriptor.
     * @return An `InitializedObjectValue(ObjectType.MethodType)`.
     *      Hence, this method needs to be overridden
     *      if resolution of MethodType based method calls should be performed.
     */
    def MethodType(origin: ValueOrigin, descriptor: MethodDescriptor): DomainReferenceValue = {
        InitializedObjectValue(origin, ObjectType.MethodType)
    }

    // -----------------------------------------------------------------------------------
    //
    // Additional helper methods to create "Computation" objects
    //
    // -----------------------------------------------------------------------------------

    final def justThrows(value: ExceptionValue): ThrowsException[ExceptionValues] = {
        ThrowsException(Seq(value))
    }

    final def throws(value: ExceptionValue): ThrowsException[ExceptionValue] = {
        ThrowsException(value)
    }

}
