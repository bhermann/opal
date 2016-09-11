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
package br

import scala.math.Ordered
import org.opalj.bi.ACC_ABSTRACT
import org.opalj.bi.ACC_STRICT
import org.opalj.bi.ACC_NATIVE
import org.opalj.bi.ACC_BRIDGE
import org.opalj.bi.ACC_VARARGS
import org.opalj.bi.ACC_SYNCHRONIZED
import org.opalj.bi.AccessFlagsContexts
import org.opalj.bi.AccessFlags
import org.opalj.bi.AccessFlagsMatcher
import org.opalj.bi.ACC_PUBLIC
import org.opalj.bi.VisibilityModifier
import org.opalj.br.instructions.Instruction

/**
 * Represents a single method.
 *
 * Method objects are constructed using the companion object's factory methods.
 *
 * @note Methods have – by default – no link to their defining [[ClassFile]]. However,
 *      if a [[analyses.Project]] is available then it is possible to get a `Method`'s
 *      [[ClassFile]] by using `Project`'s `classFile(Method)` method.
 * @note Equality of methods is – by purpose – reference based.
 *
 * @param accessFlags The ''access flags'' of this method. Though it is possible to
 *     directly work with the `accessFlags` field, it may be more convenient to use
 *     the respective methods (`isNative`, `isAbstract`,...) to query the access flags.
 * @param name The name of the method. The name is interned (see `String.intern()`
 *      for details) to enable reference comparisons.
 * @param descriptor This method's descriptor.
 * @param body The body of the method if any.
 * @param attributes This method's defined attributes. (Which attributes are available
 *      generally depends on the configuration of the class file reader. However,
 *      the `Code_Attribute` is – if it was loaded – always directly accessible by
 *      means of the `body` attribute.).
 *
 * @author Michael Eichberg
 * @author Marco Torsello
 */
final class Method private (
        val accessFlags: Int,
        val name:        String,
        val descriptor:  MethodDescriptor,
        val body:        Option[Code],
        val attributes:  Attributes
) extends ClassMember with Ordered[Method] with InstructionsContainer {

    final override def instructionsOption: Option[Array[Instruction]] = body.map(_.instructions)

    final override def isMethod = true

    final override def asMethod = this

    final def asVirtualMethod(declaringClassFile: ClassFile): VirtualMethod = {
        asVirtualMethod(declaringClassFile.thisType)
    }

    def asVirtualMethod(declaringClassType: ObjectType): VirtualMethod = {
        VirtualMethod(declaringClassType, name, descriptor)
    }

    /**
     * Returns `true` if this method has the given name and descriptor.
     *
     * @param ignoreReturnType If `false` (default), then the return type is taken
     *      into consideration. This models the behavior of the JVM w.r.t. method
     *      dispatch.
     */
    def hasSignature(
        name:             String,
        descriptor:       MethodDescriptor,
        ignoreReturnType: Boolean
    ): Boolean = {
        this.name == name && {
            if (ignoreReturnType)
                this.descriptor.equalParameters(descriptor)
            else
                this.descriptor == descriptor
        }
    }

    /**
     * Returns `true` if this method and the given method have the same signature.
     *
     * @param ignoreReturnType If `false` (default), then the return type is taken
     *      into consideration. This models the behavior of the JVM w.r.t. method
     *      dispatch.
     *      However, if you want to determine whether this method potentially overrides
     *      the given one, you may want to specify that you want to ignore the return type.
     *      (The Java compiler generate the appropriate methods.)
     */
    def hasSignature(other: Method, ignoreReturnType: Boolean = false): Boolean = {
        this.hasSignature(other.name, other.descriptor, ignoreReturnType)
    }

    /**
     * Returns `true` if this method has the given name and descriptor.
     *
     * @note When matching the descriptor the return type is also taken into consideration.
     */
    def hasSignature(name: String, descriptor: MethodDescriptor): Boolean = {
        this.hasSignature(name, descriptor, false)
    }

    def runtimeVisibleParameterAnnotations: ParameterAnnotations = {
        attributes.collectFirst { case RuntimeVisibleParameterAnnotationTable(as) ⇒ as } match {
            case Some(annotations) ⇒ annotations
            case None              ⇒ IndexedSeq.empty
        }
    }

    def runtimeInvisibleParameterAnnotations: ParameterAnnotations = {
        attributes.collectFirst { case RuntimeInvisibleParameterAnnotationTable(as) ⇒ as } match {
            case Some(annotations) ⇒ annotations
            case None              ⇒ IndexedSeq.empty
        }
    }

    def parameterAnnotations: ParameterAnnotations = {
        runtimeVisibleParameterAnnotations ++ runtimeInvisibleParameterAnnotations
    }

    /**
     * If this method represents a method of an annotation that defines a default
     * value then this value is returned.
     */
    def annotationDefault: Option[ElementValue] = {
        attributes collectFirst { case ev: ElementValue ⇒ ev }
    }

    // This is directly supported due to its need for the resolution of signature
    // polymorphic methods.
    final def isNativeAndVarargs = Method.isNativeAndVarargs(accessFlags)

    final def isVarargs: Boolean = (ACC_VARARGS.mask & accessFlags) != 0

    final def isSynchronized: Boolean = (ACC_SYNCHRONIZED.mask & accessFlags) != 0

    final def isBridge: Boolean = (ACC_BRIDGE.mask & accessFlags) != 0

    final def isNative = (ACC_NATIVE.mask & accessFlags) != 0

    final def isStrict: Boolean = (ACC_STRICT.mask & accessFlags) != 0

    final def isAbstract: Boolean = (ACC_ABSTRACT.mask & accessFlags) != 0

    final def isNotAbstract: Boolean = (ACC_ABSTRACT.mask & accessFlags) == 0

    final def isConstructor: Boolean = name == "<init>"

    final def isStaticInitializer: Boolean = name == "<clinit>"

    final def isInitializer: Boolean = isConstructor || isStaticInitializer

    def returnType = descriptor.returnType

    def parameterTypes = descriptor.parameterTypes

    /**
     * The number of explicit and implicit – that is, including `this` in case of a
     * non-static method – parameters of this method.
     */
    def parametersCount = (if (isStatic) 0 else 1) + descriptor.parametersCount

    /**
     * Each method optionally defines a method type signature.
     */
    def methodTypeSignature: Option[MethodTypeSignature] = {
        attributes collectFirst { case s: MethodTypeSignature ⇒ s }
    }

    def exceptionTable: Option[ExceptionTable] = {
        attributes collectFirst { case et: ExceptionTable ⇒ et }
    }

    /**
     * Defines an absolute order on `Method` instances based on their method signatures.
     *
     * The order is defined by lexicographically comparing the names of the methods
     * and – in case that the names of both methods are identical – by comparing
     * their method descriptors.
     */
    def compare(other: Method): Int = {
        if (this.name eq other.name)
            this.descriptor.compare(other.descriptor)
        else
            this.name.compareTo(other.name)
    }

    def toJava(): String = {
        val visibility = VisibilityModifier.get(accessFlags).map(_.javaName.get+" ").getOrElse("")
        visibility + descriptor.toJava(name)
    }

    def toJava(declaringClass: ClassFile): String = toJava(declaringClass.thisType)

    def toJava(declaringType: ObjectType): String = s"${declaringType.toJava}{ $toJava }"

    def fullyQualifiedSignature(declaringClassType: ObjectType): String = {
        descriptor.toJava(declaringClassType.toJava+"."+name)
    }

    def copy(
        accessFlags: Int              = this.accessFlags,
        name:        String           = this.name,
        descriptor:  MethodDescriptor = this.descriptor,
        body:        Option[Code]     = this.body,
        attributes:  Attributes       = this.attributes
    ): Method = {
        new Method(accessFlags, name, descriptor, body, attributes)
    }

    override def toString(): String = {
        import AccessFlagsContexts.METHOD
        val jAccessFlags = AccessFlags.toStrings(accessFlags, METHOD).mkString(" ")
        val method =
            if (jAccessFlags.nonEmpty)
                jAccessFlags+" "+descriptor.toJava(name)
            else
                descriptor.toJava(name)

        if (attributes.nonEmpty)
            method + attributes.map(_.getClass.getSimpleName).mkString("«", ", ", "»")
        else
            method

    }

}
/**
 * Defines factory and extractor methods for `Method` objects.
 *
 * @author Michael Eichberg
 */
object Method {

    /**
     * Returns `true` if the method is object serialization related.
     * That is, if the declaring class is `Externalizable` then the methods readObject and
     * writeObject are unused.
     * If the declaring class is '''only''' `Seralizable` then the write and read
     * external methods are not serialization related unless a subclass exists that inherits
     * these two methods and implements the interface `Externalizable`.
     *
     * @note Calling this method only makes sense if the given class or a subclass thereof
     *       is at least `Serializable`.
     *
     * @param method A method defined by a class that inherits from Serializable or which has
     * 			at least one sublcass that is Serializable and that inherits the given method.
     * @param isInheritedBySerializableOnlyClass This parameter should be `Yes` iff this method is
     *      defined in a `Serializable` class or is inherited by at least one class that is
     *      (just) `Serializable`, but which is not `Externalizable`.
     * @param isInheritedByExternalizableClass This parameter should be `Yes` iff the method's
     *      defining class is `Externalizable` or if this method is inherited by at least one class
     *      that is `Externalizable`.
     */
    def isObjectSerializationRelated(
        method:                             Method,
        isInheritedBySerializableOnlyClass: ⇒ Answer,
        isInheritedByExternalizableClass:   ⇒ Answer
    ): Boolean = {
        import MethodDescriptor.JustReturnsObject
        import MethodDescriptor.NoArgsAndReturnVoid
        import MethodDescriptor.ReadObjectDescriptor
        import MethodDescriptor.WriteObjectDescriptor
        import MethodDescriptor.ReadObjectInputDescriptor
        import MethodDescriptor.WriteObjectOutputDescriptor

        val name = method.name
        val descriptor = method.descriptor
        /*The default constructor is used by the deserialization process*/
        (name == "<init>" && descriptor == NoArgsAndReturnVoid) ||
            (name == "readObjectNoData" && descriptor == NoArgsAndReturnVoid) ||
            (name == "readResolve" && descriptor == JustReturnsObject) ||
            (name == "writeReplace" && descriptor == JustReturnsObject) ||
            ((
                (name == "readObject" && descriptor == ReadObjectDescriptor) ||
                (name == "writeObject" && descriptor == WriteObjectDescriptor)
            ) && isInheritedBySerializableOnlyClass.isYesOrUnknown) ||
                (
                    method.isPublic /*we are implementing an interface...*/ &&
                    (
                        (name == "readExternal" && descriptor == ReadObjectInputDescriptor) ||
                        (name == "writeExternal" && descriptor == WriteObjectOutputDescriptor)
                    ) &&
                        isInheritedByExternalizableClass.isYesOrUnknown
                )
    }

    private def isNativeAndVarargs(accessFlags: Int) = {
        import AccessFlagsMatcher.ACC_NATIVEAndVARARGS
        (accessFlags & ACC_NATIVEAndVARARGS) == ACC_NATIVEAndVARARGS
    }

    /**
     * @param 	name The name of the method. In case of a constructor the method
     *      	name has to be "<init>". In case of a static initializer the name has to
     *      	be "<clinit>".
     */
    def apply(
        accessFlags: Int,
        name:        String,
        descriptor:  MethodDescriptor,
        attributes:  Attributes
    ): Method = {

        val (bodySeq, remainingAttributes) = attributes partition { _.isInstanceOf[Code] }
        val theBody = bodySeq.headOption.asInstanceOf[Option[Code]]

        new Method(
            accessFlags,
            name.intern(),
            descriptor,
            theBody,
            remainingAttributes
        )
    }

    /**
     * Factory for Method objects.
     *
     * @example A new method that is public abstract that takes no parameters and
     * 			returns void and has the name "myMethod" can be created as shown next:
     * 			{{{
     *  		val myMethod = Method(name="myMethod");
     * 			}}}
     */
    def apply(
        accessFlags:    Int                   = ACC_ABSTRACT.mask | ACC_PUBLIC.mask,
        name:           String,
        parameterTypes: IndexedSeq[FieldType] = IndexedSeq.empty,
        returnType:     Type                  = VoidType,
        attributes:     Attributes            = Seq.empty[Attribute]
    ): Method = {
        Method(accessFlags, name, MethodDescriptor(parameterTypes, returnType), attributes)
    }

    def unapply(method: Method): Option[(Int, String, MethodDescriptor)] =
        Some((method.accessFlags, method.name, method.descriptor))
}
