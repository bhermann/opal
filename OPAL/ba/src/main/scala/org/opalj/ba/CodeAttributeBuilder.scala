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
package ba

import scala.collection.mutable.ArrayBuffer
import org.opalj.collection.immutable.UShortPair
import org.opalj.ai.BaseAI
import org.opalj.ai.domain.l0.TypeCheckingDomain
import org.opalj.bi.ACC_STATIC
import org.opalj.br.StackMapTable
import org.opalj.br.ClassHierarchy
import org.opalj.br.Method
import org.opalj.br.ClassFile
import org.opalj.br.ObjectType
import org.opalj.br.StackMapFrame
import org.opalj.br.FullFrame
import org.opalj.br.ChopFrame
import org.opalj.br.AppendFrame
import org.opalj.br.SameLocals1StackItemFrameExtended
import org.opalj.br.SameLocals1StackItemFrame
import org.opalj.br.SameFrame
import org.opalj.br.SameFrameExtended
import org.opalj.br.VerificationTypeInfo
import org.opalj.br.TopVariableInfo
import org.opalj.br.instructions.Instruction
import org.opalj.bytecode.BytecodeProcessingFailedException
import org.opalj.collection.mutable.Locals

/**
 * Builder for the [[org.opalj.br.Code]] attribute with all its properties. The ''Builder'' is
 * created using the [[CODE]] factory.
 *
 * The `max_stack` and `max_locals` values will be calculated if not explicitly defined.
 *
 * @author Malte Limmeroth
 * @author Michael Eichberg
 */
class CodeAttributeBuilder[T] private[ba] (
        private[ba] val instructions:                   Array[Instruction],
        private[ba] val hasControlTransferInstructions: Boolean,
        private[ba] val pcMapping:                      PCMapping, // the PCMapping must not be complete w.r.t. the set of original PCs
        private[ba] val annotations:                    Map[br.PC, T],
        private[ba] var maxStack:                       Option[Int],
        private[ba] var maxLocals:                      Option[Int],
        private[ba] var exceptionHandlers:              br.ExceptionHandlers,
        private[ba] var attributes:                     br.Attributes
) extends br.CodeAttributeBuilder[(Map[br.PC, T], List[String])] {

    def copy(attributes: br.Attributes = this.attributes): CodeAttributeBuilder[T] = {
        new CodeAttributeBuilder[T](
            instructions,
            hasControlTransferInstructions,
            pcMapping,
            annotations,
            maxStack,
            maxLocals,
            exceptionHandlers,
            attributes
        )
    }

    /**
     * Defines the max_stack value.
     *
     * (This overrides/disables the automatic computation of this value.)
     */
    def MAXSTACK(value: Int): this.type = {
        maxStack = Some(value)
        this
    }

    /**
     * Defines the max_locals value.
     *
     * (This overrides/disables the automatic computation of this value.)
     */
    def MAXLOCALS(value: Int): this.type = {
        maxLocals = Some(value)
        this
    }

    /**
     * Creates a `Code` attribute with respect to the given method; this is particularly useful
     * when we do bytecode weaving.
     *
     * @see `apply(classFileVersion:UShortPair,accessFlags:Int,name:String,...)` for more details.
     * @param classFileVersion The version of the class file to which the returned will be added
     *                         eventually.
     *
     */
    def apply(
        classFileVersion: UShortPair,
        method:           Method
    )(
        implicit
        classHierarchy: ClassHierarchy = br.Code.BasicClassHierarchy
    ): (br.Code, (Map[br.PC, T], List[String])) = {
        this(
            classFileVersion, method.classFile.thisType,
            method.accessFlags, method.name, method.descriptor
        )
    }

    /**
     * Creates a `Code` attribute.
     *
     * The `classHierarchy` is required iff a Java 6 or newer class file is created and
     * the code requires the computation of a new stack map table. If this is not the
     * case the class hierarchy can be `null`.
     *
     * @param  accessFlags The declaring method's access flags, required during code validation or
     *         when MAXSTACK/MAXLOCALS needs to be computed.
     * @param  descriptor The declaring method's descriptor; required during code validation or
     *         when MAXSTACK/MAXLOCALS needs to be computed.
     *
     * @return The tuple:
     *         `(the code attribute, (the extracted meta information, the list of warnings))`.
     */
    def apply(
        classFileVersion:   UShortPair,
        declaringClassType: ObjectType,
        accessFlags:        Int,
        name:               String,
        descriptor:         br.MethodDescriptor
    )(
        implicit
        classHierarchy: ClassHierarchy
    ): (br.Code, (Map[br.PC, T], List[String])) = {

        import CodeAttributeBuilder.warnMessage
        var warnings = List.empty[String]

        val computedMaxLocals = br.Code.computeMaxLocals(
            !ACC_STATIC.isSet(accessFlags),
            descriptor,
            instructions
        )
        if (maxLocals.isDefined && maxLocals.get < computedMaxLocals) {
            warnings ::=
                warnMessage.format(
                    descriptor.toJVMDescriptor,
                    "max_locals",
                    maxLocals.get,
                    computedMaxLocals
                )
        }

        val computedMaxStack = br.Code.computeMaxStack(
            instructions = instructions,
            exceptionHandlers = exceptionHandlers
        )
        if (maxStack.isDefined && maxStack.get < computedMaxStack) {
            warnings ::= warnMessage.format(
                descriptor.toJVMDescriptor,
                "max_stack",
                maxStack.get,
                computedMaxStack
            )
        }

        var code = br.Code(
            maxStack = maxStack.getOrElse(computedMaxStack),
            maxLocals = maxLocals.getOrElse(computedMaxLocals),
            instructions = instructions,
            exceptionHandlers = exceptionHandlers,
            attributes = attributes
        )

        // We need to compute the stack map table if we don't have one already!
        if (classFileVersion.major >= bi.Java6MajorVersion &&
            attributes.forall(a ⇒ a.kindId != StackMapTable.KindId) &&
            (hasControlTransferInstructions || exceptionHandlers.nonEmpty)) {
            // Let's create fake code and method objects to make it possible
            // to use the AI framework for computing the stack map table...
            val cf = ClassFile(
                majorVersion = classFileVersion.major,
                thisType = declaringClassType,
                methods = IndexedSeq(Method(accessFlags, name, descriptor, IndexedSeq(code)))
            )
            val m = cf.methods.head
            code = code.copy(attributes = this.attributes :+ computeStackMapTable(m))
        }

        (code, (annotations, warnings))
    }

    /**
     * Computes the [[org.opalj.br.StackMapTable]] for the given method. (Requires that
     * the method does NOT use JSR/RET instructions!)
     *
     * @param m A method (which satisfies the constraints of Java 6> methods) with a body.
     * @param classHierarchy The project's class hierarchy; i.e., computing the table generally
     *                       requires the complete class hierarchy w.r.t. to the types referred to
     *                       by the method.
     * @return The computed [[org.opalj.br.StackMapTable]].
     */
    def computeStackMapTable(
        m: Method
    )(
        implicit
        classHierarchy: ClassHierarchy
    ): StackMapTable = {
        type VerificationTypeInfos = IndexedSeq[VerificationTypeInfo]

        val c = m.body.get

        // compute info
        val theDomain = new TypeCheckingDomain(classHierarchy, m)
        val ils = CodeAttributeBuilder.ai.initialLocals(m, theDomain)(None)
        val ios = CodeAttributeBuilder.ai.initialOperands(m, theDomain)
        val r = CodeAttributeBuilder.ai.performInterpretation(c, theDomain)(ios, ils)

        // compute table
        def computeLocalsVerificationTypeInfo(
            locals: Locals[theDomain.DomainValue]
        ): VerificationTypeInfos = {
            val lastLocalsIndex = locals.indexOfLastNonNullValue
            var index = 0
            val ls = new ArrayBuffer[VerificationTypeInfo](lastLocalsIndex + 1)
            do {
                ls += (
                    locals(index) match {
                        case null | r.domain.TheIllegalValue ⇒
                            index += 1
                            TopVariableInfo

                        case dv ⇒
                            index += dv.computationalType.operandSize
                            dv.verificationTypeInfo
                    }
                )
            } while (index <= lastLocalsIndex)
            ls
        }

        var lastPC = -1 // -1 === initial stack map frame
        var lastVerificationTypeInfoLocals: VerificationTypeInfos =
            computeLocalsVerificationTypeInfo(ils)
        var lastverificationTypeInfoStack: VerificationTypeInfos =
            IndexedSeq.empty // has to be empty...

        val framePCs = c.stackMapTablePCs(classHierarchy)
        val fs = new Array[StackMapFrame](framePCs.size)
        var frameIndex = 0
        framePCs.foreach { pc ⇒
            val verificationTypeInfoLocals: VerificationTypeInfos = {
                val locals = r.localsArray(pc)
                if (locals == null) {
                    val message = m.toJava(s"pc=$pc is dead; unable to compute stack map table")
                    throw new BytecodeProcessingFailedException(message);
                }
                computeLocalsVerificationTypeInfo(locals)
            }
            val verificationTypeInfoStack: VerificationTypeInfos = {
                var operands = r.operandsArray(pc)
                var operandIndex = operands.size
                if (operandIndex == 0) {
                    IndexedSeq.empty // an empty stack is a VERY common case...
                } else {
                    val os = new Array[VerificationTypeInfo](operandIndex /*HERE == operands.size*/ )
                    operandIndex -= 1
                    do {
                        os(operandIndex) = operands.head.verificationTypeInfo
                        operands = operands.tail
                        operandIndex -= 1
                    } while (operandIndex >= 0)
                    os
                }
            }

            // let's see how the last stack map frame looked like and if we can compute
            // an "optimal" stack map frame item
            val sameLocals = lastVerificationTypeInfoLocals == verificationTypeInfoLocals
            val emptyStack = verificationTypeInfoStack.isEmpty
            val localsCount = verificationTypeInfoLocals.size
            val lastLocalsCount = lastVerificationTypeInfoLocals.size
            val localsDiffCount = localsCount - lastLocalsCount
            if (sameLocals && lastverificationTypeInfoStack == verificationTypeInfoStack) {
                // ---- SameFrame(Extended) ...
                //
                val offsetDelta = pc - lastPC - 1
                fs(frameIndex) =
                    if (offsetDelta <= 63)
                        SameFrame(offsetDelta)
                    else
                        SameFrameExtended(offsetDelta)
            } else if (sameLocals && verificationTypeInfoStack.size == 1) {
                // ---- SameLocals1StackItemFrame(Extended) ...
                //
                val offsetDelta = pc - lastPC - 1
                if (offsetDelta <= 63) {
                    val frameType = 64 + offsetDelta
                    fs(frameIndex) =
                        SameLocals1StackItemFrame(frameType, verificationTypeInfoStack(0))
                } else {
                    fs(frameIndex) =
                        SameLocals1StackItemFrameExtended(offsetDelta, verificationTypeInfoStack(0))
                }
            } else if (emptyStack && localsDiffCount < 0 && localsDiffCount >= -3 && (
                // all "still" existing locals are equal...
                verificationTypeInfoLocals.iterator.
                zipWithIndex.
                forall { case (vtil, index) ⇒ vtil == lastVerificationTypeInfoLocals(index) }
            )) {
                // ---- CHOP FRAME ...
                //
                val offsetDelta = pc - lastPC - 1
                fs(frameIndex) = ChopFrame(251 + localsDiffCount, offsetDelta)
            } else if (emptyStack && localsDiffCount > 0 && localsDiffCount <= 3 && (
                // all previously existing locals are equal...
                verificationTypeInfoLocals.iterator
                .take(lastLocalsCount)
                .zipWithIndex
                .forall { case (vtil, index) ⇒ vtil == lastVerificationTypeInfoLocals(index) }
            )) {
                // ---- APPEND FRAME ...
                //
                val offsetDelta = pc - lastPC - 1
                val newLocals = verificationTypeInfoLocals.slice(
                    from = localsCount - localsDiffCount,
                    until = localsCount
                )
                if (newLocals.forall(_ == TopVariableInfo)) {
                    // just "appending" top is not necessary - this is implicitly the case!
                    fs(frameIndex) =
                        if (offsetDelta <= 63)
                            SameFrame(offsetDelta)
                        else
                            SameFrameExtended(offsetDelta)
                } else {
                    fs(frameIndex) = AppendFrame(251 + localsDiffCount, offsetDelta, newLocals)
                }
            } else {
                // ---- FULL FRAME ...
                //
                fs(frameIndex) = FullFrame(
                    offsetDelta = if (lastPC != -1) pc - lastPC - 1 else pc,
                    verificationTypeInfoLocals,
                    verificationTypeInfoStack
                )
            }

            lastVerificationTypeInfoLocals = verificationTypeInfoLocals
            lastverificationTypeInfoStack = verificationTypeInfoStack
            frameIndex += 1
            lastPC = pc
        }
        StackMapTable(fs)
    }
}

object CodeAttributeBuilder {

    final val warnMessage = s"%s: %s is too small %d < %d"

    // the identifiocation of dead variable potentially leads to "bigger stack map tables"...
    final val ai = new BaseAI(IdentifyDeadVariables = false)

}
