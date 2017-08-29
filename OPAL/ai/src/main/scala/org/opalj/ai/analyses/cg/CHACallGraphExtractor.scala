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
import org.opalj.br.ArrayType
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

        def arrayCall(
            caller:     Method,
            pc:         PC,
            arrayType:  ArrayType,
            name:       String,
            descriptor: MethodDescriptor
        ): Unit = {

            addCallToNullPointerExceptionConstructor(method, pc)

            project.instanceCall(caller.classFile.thisType, ObjectType.Object, name, descriptor) match {
                case Success(callee) ⇒
                    addCallEdge(pc, HashSet(callee))
                case _ ⇒
                    addUnresolvedMethodCall(method, pc, arrayType, name, descriptor)
            }
        }

        def staticCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            isInterface:        Boolean,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            project.staticCall(declaringClassType, isInterface, name, descriptor) match {
                case Success(callee) ⇒
                    addCallEdge(pc, HashSet(callee))
                case _ ⇒
                    addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            }
        }

        def specialCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            isInterface:        Boolean,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {
            // Recall that the receiver is guaranteed to be non-null (call to super method,
            // private method or constructor call).

            project.specialCall(declaringClassType, isInterface, name, descriptor) match {
                case Success(callee) ⇒
                    val callees = HashSet(callee)
                    addCallEdge(pc, callees)
                case _ ⇒
                    addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            }
        }

        /**
         * @note A virtual method call is always a class instance based call and never a call to
         *      a static method. However, the receiver may be `null` unless it is the
         *      self reference (`this`), but the receiving object is not known in the simplest case.
         */
        def virtualCall(
            caller:                Method,
            pc:                    PC,
            declaringClassType:    ObjectType,
            name:                  String,
            descriptor:            MethodDescriptor,
            isInterfaceInvocation: Boolean          = false
        ): Unit = {

            addCallToNullPointerExceptionConstructor(method, pc)

            val callees = this.callees(caller, declaringClassType, isInterface = false, name, descriptor)
            if (callees.isEmpty) {
                addUnresolvedMethodCall(method, pc, declaringClassType, name, descriptor)
            } else {
                addCallEdge(pc, callees)
            }
        }

        /**
         * @note A virtual method call is always a class instance based call and never a call to
         *      a static method. However, the receiver may be `null` unless it is the
         *      self reference (`this`), but the receiving object is not known in the simplest case.
         */
        def interfaceCall(
            caller:             Method,
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            addCallToNullPointerExceptionConstructor(method, pc)

            val callees = this.callees(caller, declaringClassType, isInterface = true, name, descriptor)
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
                        context.arrayCall(method, pc, declaringClass.asArrayType, name, descriptor)
                    } else {
                        context.virtualCall(method, pc, declaringClass.asObjectType, name, descriptor)
                    }
                case INVOKEINTERFACE.opcode ⇒
                    val INVOKEINTERFACE(declaringClass, name, descriptor) = instruction
                    context.interfaceCall(method, pc, declaringClass, name, descriptor)

                case INVOKESPECIAL.opcode ⇒
                    val INVOKESPECIAL(declaringClass, isInterface, name, descriptor) = instruction
                    context.specialCall(pc, declaringClass, isInterface, name, descriptor)

                case INVOKESTATIC.opcode ⇒
                    val INVOKESTATIC(declaringClass, isInterface, name, descriptor) = instruction
                    context.staticCall(pc, declaringClass, isInterface, name, descriptor)

                case _ ⇒
                // Nothing to do...
            }
        }

        (context.allCallEdges, context.unresolvableMethodCalls)
    }

}
