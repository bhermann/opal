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
package analyses
package cg

import scala.collection.Set
import scala.collection.mutable.HashSet

import org.opalj.br.Method
import org.opalj.br.MethodDescriptor
import org.opalj.br.MethodSignature
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject
import org.opalj.br.instructions.INVOKEINTERFACE
import org.opalj.br.instructions.INVOKESPECIAL
import org.opalj.br.instructions.INVOKESTATIC
import org.opalj.br.instructions.INVOKEVIRTUAL

/**
 * Domain object that can be used to calculate a call graph using CHA. This domain
 * basically collects – for all invoke instructions of a method – the potential target
 * methods that may be invoked at runtime.
 *
 * Virtual calls on Arrays (clone(), toString(),...) are replaced by calls to the
 * respective methods of `java.lang.Object`.
 *
 * Signature polymorphic methods are correctly resolved (done by the method
 * `lookupImplementingMethod` defined in `ClassHierarchy`.)
 *
 * @author Michael Eichberg
 * @author Michael Reif
 */
class CHACallGraphExtractor(
        val cache: CallGraphCache[MethodSignature, Set[Method]]
) extends CallGraphExtractor {

    protected[this] class AnalysisContext(
            val method: Method
    )(
            implicit
            val project: SomeProject
    ) extends super.AnalysisContext {

        implicit val classHierarchy = project.classHierarchy
        import project.lookupMethodDefinition

        def staticCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            def handleUnresolvedMethodCall() = {
                addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            }

            lookupMethodDefinition(declaringClassType, name, descriptor) match {
                case Some(callee) ⇒ addCallEdge(pc, HashSet(callee))
                case None         ⇒ handleUnresolvedMethodCall()
            }
        }

        def doNonVirtualCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            def handleUnresolvedMethodCall(): Unit = {
                addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            }

            lookupMethodDefinition(declaringClassType, name, descriptor) match {
                case Some(callee) ⇒
                    val callees = HashSet(callee)
                    addCallEdge(pc, callees)
                case None ⇒
                    handleUnresolvedMethodCall()
            }

        }

        /**
         * @param receiverMayBeNull The parameter is `false` if:
         *      - a static method is called,
         *      - this is an invokespecial call (in this case the receiver is `this`),
         *      - the receiver is known not to be null and the type is known to be precise.
         */
        def nonVirtualCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor,
            receiverIsNull:     Answer
        ): Unit = {

            if (receiverIsNull.isYesOrUnknown) {
                addCallToNullPointerExceptionConstructor(method, pc)
            }

            doNonVirtualCall(pc, declaringClassType, name, descriptor)
        }

        /**
         * @note A virtual method call is always an instance based call and never a call to
         *      a static method. However, the receiver may be `null` unless it is the
         *      self reference (`this`).
         */
        def virtualCall(
            pc:                    PC,
            declaringClassType:    ObjectType,
            name:                  String,
            descriptor:            MethodDescriptor,
            isInterfaceInvocation: Boolean          = false
        ): Unit = {

            addCallToNullPointerExceptionConstructor(method, pc)

            val callees: Set[Method] = this.callees(declaringClassType, name, descriptor)
            if (callees.isEmpty) {
                addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            } else {
                addCallEdge(pc, callees)
            }
        }
    }

    def extract(
        method: Method
    )(
        implicit
        project: SomeProject
    ): CallGraphExtractor.LocalCallGraphInformation = {
        val context = new AnalysisContext(method)

        method.body.get.iterate { (pc, instruction) ⇒
            instruction.opcode match {
                case INVOKEVIRTUAL.opcode ⇒
                    val INVOKEVIRTUAL(declaringClass, name, descriptor) = instruction
                    if (declaringClass.isArrayType) {
                        context.nonVirtualCall(pc, ObjectType.Object, name, descriptor, Unknown)
                    } else {
                        context.virtualCall(pc, declaringClass.asObjectType, name, descriptor)
                    }
                case INVOKEINTERFACE.opcode ⇒
                    val INVOKEINTERFACE(declaringClass, name, descriptor) = instruction
                    context.virtualCall(pc, declaringClass, name, descriptor, true)

                case INVOKESPECIAL.opcode ⇒
                    val INVOKESPECIAL(declaringClass, _, name, descriptor) = instruction
                    // for invokespecial the dynamic type is not "relevant" (even for Java 8)
                    // "here", the receiver is this <=> non-null
                    context.nonVirtualCall(pc, declaringClass, name, descriptor, receiverIsNull = No)

                case INVOKESTATIC.opcode ⇒
                    val INVOKESTATIC(declaringClass, _, name, descriptor) = instruction
                    context.staticCall(pc, declaringClass, name, descriptor)

                case _ ⇒
                // Nothing to do...
            }
        }

        (context.allCallEdges, context.unresolvableMethodCalls)
    }

}
