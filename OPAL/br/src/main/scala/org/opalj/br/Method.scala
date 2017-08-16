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
import org.opalj.bi.ACC_PRIVATE
import org.opalj.bi.ACC_PROTECTED
import org.opalj.bi.VisibilityModifier
import org.opalj.br.instructions.ALOAD_0
import org.opalj.br.instructions.INVOKESPECIAL
import org.opalj.br.instructions.RETURN
import org.opalj.br.instructions.Instruction

/**
 * Represents a single method.
 *
 * Method objects are constructed using the companion object's factory methods.
 *
 * @note   Methods, which are directly created, have no link to "their defining" [[ClassFile]].
 *         This link is implicitly established when a method is added to a [[ClassFile]]. This
 *         operation also updates the method object. Hence, an empty method/constructor which
 *         is identical across multiple classes can be reused.
 *
 * @note   Equality of methods is – by purpose – reference based.
 *
 * @author Michael Eichberg
 * @author Marco Torsello
 */
sealed abstract class JVMMethod
    extends ClassMember
    with Ordered[JVMMethod]
    with InstructionsContainer {

    //
    //
    // THE STATE
    //
    //

    /**
     * The ''access flags'' of this method. Though it is possible to
     * directly work with the `accessFlags` field, it may be more convenient to use
     * the respective methods (`isNative`, `isAbstract`,...) to query the access flags.
     */
    def accessFlags: Int

    /**
     * The name of the method. The name is interned (see `String.intern()`
     * for details) to enable reference comparisons.
     */
    def name: String

    /** This method's descriptor. */
    def descriptor: MethodDescriptor

    /** The body of the method if any. */
    def body: Option[Code]

    /**
     * This method's defined attributes. (Which attributes are available
     * generally depends on the configuration of the class file reader. However,
     * the `Code_Attribute` is – if it was loaded – always directly accessible by
     * means of the `body` attribute.).
     */
    def attributes: Attributes

    // This method is only to be called by ..br.ClassFile to associate this method
    // with the respective class file.
    private[br] def prepareClassFileAttachement: Method = {
        new Method(
            null /*will be set by class file*/ ,
            accessFlags, name, descriptor, body, attributes
        )
    }

    /**
     * Creates a copy of this method object which is not associated with any class file.
     */
    def copy(
        accessFlags: Int              = this.accessFlags,
        name:        String           = this.name,
        descriptor:  MethodDescriptor = this.descriptor,
        body:        Option[Code]     = this.body,
        attributes:  Attributes       = this.attributes
    ): MethodTemplate = {
        // ensure invariant that the code attribute is explicitly extracted...
        assert(attributes.forall { a ⇒ a.kindId != Code.KindId })

        val n = if (this.name eq name) name else name.intern()

        new MethodTemplate(accessFlags, n, descriptor, body, attributes)
    }

    //
    //
    // THE METHODS
    //
    //

    /**
     * Compares this method with the given one for structural equality. The declaring class
     * file is ignored.
     *
     * Two methods are structurally equal if they have the same names, flags and descriptor.
     * The bodies and attributes are recursively checked for structural equality. In case of the
     * attributes, the order doesn't matter!
     */
    def similar(other: JVMMethod, config: SimilarityTestConfiguration): Boolean = {
        // IMPROVE Define a method "findDissimilarity" as in case of ClassFile to report the difference
        if (this.accessFlags != other.accessFlags ||
            this.name != other.name ||
            this.descriptor != other.descriptor) {
            return false;
        }

        val (thisBody, otherBody) = config.compareCode(this, this.body, other.body)
        if (!(
            (thisBody.isEmpty && otherBody.isEmpty) ||
            (
                thisBody.nonEmpty && otherBody.nonEmpty &&
                thisBody.get.similar(otherBody.get, config)
            )
        )) {
            return false;
        }

        compareAttributes(other.attributes, config).isEmpty
    }

    final override def instructionsOption: Option[Array[Instruction]] = body.map(_.instructions)

    /**
     * The number of registers required to store this method's parameters (
     * including the self reference if necessary).
     *
     * Basically, `MethodDescriptor.requiredRegisters` adapted by the required parameter for
     * `this` in case of an instance method.
     */
    def requiredRegisters: Int = {
        descriptor.requiredRegisters + (if (isStatic) 0 else 1)
    }

    /**
     * Returns `true` if this method has the given name and descriptor.
     *
     * @param  ignoreReturnType If `false`, then the return type is taken
     *         into consideration; this models the behavior of the JVM w.r.t. method
     *         dispatch.
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
     *      (The Java compiler generates the appropriate methods.)
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

    def signature: MethodSignature = new MethodSignature(name, descriptor)

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

    /**
     * If this method has extended method parameter information, the `MethodParameterTable` is
     * returned.
     */
    def methodParameters: Option[MethodParameterTable] = {
        attributes collectFirst { case mp: MethodParameterTable ⇒ mp }
    }

    /**
     * Returns `Yes` if the parameter with the given index is synthetic; `No` if not and `Unknown`
     * if the information is not available. The indexes correspond to those used by the
     * [[MethodDescriptor]].
     */
    def isSyntheticParameter(parameterIndex: Int): Answer = {
        val mpsOpt = methodParameters
        if (mpsOpt.isEmpty)
            return Unknown;

        val mps = mpsOpt.get
        Answer(mps(parameterIndex).isSynthetic)
    }

    /**
     * Returns `Yes` if the parameter with the given index is mandated; `No` if not and `Unknown`
     * if the information is not available. The indexes correspond to those used by the
     * [[MethodDescriptor]].
     */
    def isMandatedParameter(parameterIndex: Int): Answer = {
        val mpsOpt = methodParameters
        if (mpsOpt.isEmpty)
            return Unknown;

        val mps = mpsOpt.get
        Answer(mps(parameterIndex).isMandated)
    }

    // This is directly supported due to its need for the resolution of signature
    // polymorphic methods.
    final def isNativeAndVarargs: Boolean = Method.isNativeAndVarargs(accessFlags)

    final def isVarargs: Boolean = (ACC_VARARGS.mask & accessFlags) != 0

    final def isSynchronized: Boolean = (ACC_SYNCHRONIZED.mask & accessFlags) != 0

    final def isBridge: Boolean = (ACC_BRIDGE.mask & accessFlags) != 0

    final def isNative: Boolean = (ACC_NATIVE.mask & accessFlags) != 0

    final def isStrict: Boolean = (ACC_STRICT.mask & accessFlags) != 0

    final def isAbstract: Boolean = (ACC_ABSTRACT.mask & accessFlags) != 0

    final def isNotAbstract: Boolean = (ACC_ABSTRACT.mask & accessFlags) == 0

    final def isConstructor: Boolean = name == "<init>"

    final def isStaticInitializer: Boolean = name == "<clinit>"

    final def isInitializer: Boolean = isConstructor || isStaticInitializer

    /**
     * Returns true if this method is a potential target of a virtual call
     * by means of an invokevirtual or invokeinterface instruction; i.e.,
     * if the method is not an initializer, is not abstract, is not private
     * and is not static.
     */
    final def isVirtualCallTarget: Boolean = {
        isNotAbstract && !isPrivate && !isStatic && !isInitializer &&
            !isStaticInitializer // before Java 8 <clinit> was not required to be static
    }

    /**
     * Returns true if this method declares a virtual method. This method
     * may be abstract!
     */
    final def isVirtualMethodDeclaration: Boolean = {
        !isPrivate && !isStatic && !isInitializer &&
            !isStaticInitializer // before Java 8 <clinit> was not required to be static
    }

    def returnType: Type = descriptor.returnType

    def parameterTypes: IndexedSeq[FieldType] = descriptor.parameterTypes

    /**
     * The number of explicit and implicit parameters of this method – that is,
     * including `this` in case of a non-static method.
     */
    def actualArgumentsCount: Int = (if (isStatic) 0 else 1) + descriptor.parametersCount

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
    def compare(other: JVMMethod): Int = {
        if (this.name eq other.name)
            this.descriptor.compare(other.descriptor)
        else
            this.name.compareTo(other.name)
    }

    def compare(otherName: String, otherDescriptor: MethodDescriptor): Int = {
        if (this.name eq otherName)
            this.descriptor.compare(otherDescriptor)
        else
            this.name.compareTo(otherName)
    }

    def signatureToJava(withVisibility: Boolean = true): String = {
        val visibility =
            if (withVisibility)
                VisibilityModifier.get(accessFlags).map(_.javaName.get+" ").getOrElse("")
            else
                ""
        val static = if (isStatic) "static " else ""
        visibility + static + descriptor.toJava(name)
    }

    //
    //
    // DEBUGGING PURPOSES
    //
    //

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
 * A method which is not (yet) associated with a class file.
 */
final class MethodTemplate private[br] (
        val accessFlags: Int,
        val name:        String,
        val descriptor:  MethodDescriptor,
        val body:        Option[Code],
        val attributes:  Attributes
) extends JVMMethod {

    /** This template is not (yet) a [[Method]] which is a [[SourceElement]].  */
    final override def isMethod: Boolean = false

}

/**
 * A method belonging to a class file. [[Method]] objects are created by creating a class file
 * using [[MethodTemplate]]s.
 *
 * @param classFile The declaring class file.
 */
final class Method private[br] (
        private[br] var declaringClassFile: ClassFile, // unfortunately, Scala forces us to use this ugly approach to initialize the data structure
        val accessFlags:                    Int,
        val name:                           String,
        val descriptor:                     MethodDescriptor,
        val body:                           Option[Code],
        val attributes:                     Attributes
) extends JVMMethod {

    /**
     * This method's class file.
     */
    final def classFile: ClassFile = declaringClassFile

    /**
     * @return This method as a [[VirtualMethod]].
     */
    final def asVirtualMethod: VirtualMethod = asVirtualMethod(declaringClassFile.thisType)

    /**
     * This method as a virtual method belonging to the given declaring class type.
     */
    def asVirtualMethod(declaringClassType: ObjectType): VirtualMethod = {
        VirtualMethod(declaringClassType, name, descriptor)
    }

    def toJava: String = s"${classFile.thisType.toJava}{ ${signatureToJava(true)} }"

    /**
     * A Java-like representation of the signature of this method; "the body" will contain
     * the given `methodInfo` data.
     */
    def toJava(methodInfo: String): String = {
        s"${classFile.thisType.toJava}{ ${signatureToJava(true)}{ $methodInfo } }"
    }

    /**
     * The fully qualified signature of this method.
     */
    def fullyQualifiedSignature: String = descriptor.toJava(s"${classFile.thisType.toJava}.$name")

    final override def isMethod: Boolean = true

    final override def asMethod: this.type = this
}

/**
 * Defines factory and extractor methods for `Method` objects.
 *
 * @author Michael Eichberg
 */
object Method {

    @inline def isNativeAndVarargs(accessFlags: Int) = {
        import AccessFlagsMatcher.ACC_NATIVEAndVARARGS
        (accessFlags & ACC_NATIVEAndVARARGS) == ACC_NATIVEAndVARARGS
    }

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
     *          at least one sublcass that is Serializable and that inherits the given method.
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

    /**
     * Returns `true` if a method declared by a subclass in the package
     * `declaringPackageOfSubclassMethod` can directly override a method which has the
     *  given visibility and package.
     */
    def canDirectlyOverride(
        declaringPackageOfSubclassMethod:   String,
        superclassMethodVisibility:         Option[VisibilityModifier],
        declaringPackageOfSuperclassMethod: String
    ): Boolean = {
        superclassMethodVisibility match {
            case Some(ACC_PUBLIC) | Some(ACC_PROTECTED) ⇒ true
            case Some(ACC_PRIVATE)                      ⇒ false

            case None ⇒
                declaringPackageOfSubclassMethod == declaringPackageOfSuperclassMethod
        }
    }

    /**
     * @param   name The name of the method. In case of a constructor the method
     *          name has to be "<init>". In case of a static initializer the name has to
     *          be "<clinit>".
     */
    def apply(
        accessFlags: Int,
        name:        String,
        descriptor:  MethodDescriptor,
        attributes:  Attributes
    ): MethodTemplate = {

        val (bodySeq, remainingAttributes) = attributes partition { _.isInstanceOf[Code] }
        val theBody = bodySeq.headOption.asInstanceOf[Option[Code]]

        new MethodTemplate(
            accessFlags,
            name.intern(),
            descriptor,
            theBody,
            remainingAttributes
        )
    }

    // To be called only by the class file reader!
    protected[br] def unattached(
        accessFlags: Int,
        name:        String,
        descriptor:  MethodDescriptor,
        attributes:  Attributes
    ): Method = {

        val (bodySeq, remainingAttributes) = attributes partition { _.isInstanceOf[Code] }
        val theBody = bodySeq.headOption.asInstanceOf[Option[Code]]

        new Method(
            null,
            accessFlags,
            name.intern(),
            descriptor,
            theBody,
            remainingAttributes
        )
    }

    /**
     * Factory for MethodTemplate objects.
     *
     * @example A new method that is public abstract that takes no parameters and
     *          returns void and has the name "myMethod" can be created as shown next:
     *          {{{
     *          val myMethod = Method(name="myMethod");
     *          }}}
     */
    def apply(
        accessFlags:    Int                   = ACC_ABSTRACT.mask | ACC_PUBLIC.mask,
        name:           String,
        parameterTypes: IndexedSeq[FieldType] = IndexedSeq.empty,
        returnType:     Type                  = VoidType,
        attributes:     Attributes            = Seq.empty[Attribute]
    ): MethodTemplate = {
        Method(accessFlags, name, MethodDescriptor(parameterTypes, returnType), attributes)
    }

    def unapply(method: JVMMethod): Option[(Int, String, MethodDescriptor)] = {
        Some((method.accessFlags, method.name, method.descriptor))
    }

    def defaultConstructor(superclassType: ObjectType = ObjectType.Object): MethodTemplate = {
        import MethodDescriptor.NoArgsAndReturnVoid
        val theBody = Some(Code(
            maxStack = 1,
            maxLocals = 1,
            instructions = Array(
                ALOAD_0,
                INVOKESPECIAL(superclassType, false, "<init>", NoArgsAndReturnVoid),
                null,
                null,
                RETURN
            )
        ))
        val accessFlags = ACC_PUBLIC.mask
        new MethodTemplate(accessFlags, "<init>", NoArgsAndReturnVoid, theBody, IndexedSeq.empty)
    }
}
