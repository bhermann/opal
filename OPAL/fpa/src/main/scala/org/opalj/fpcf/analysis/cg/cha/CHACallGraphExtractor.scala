/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
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
package fpcf
package analysis
package cg
package cha

import org.opalj.br.{ClassFile, Method, MethodDescriptor, MethodSignature, ObjectType, PC}
import org.opalj.br.analyses.{CallBySignatureResolution, CallBySignatureResolutionKey, SomeProject}
import org.opalj.br.instructions.{INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL}
import net.ceedubs.ficus.Ficus._
import scala.collection.Set
import scala.collection.mutable.HashSet
import org.opalj.log.OPALLogger
import org.opalj.log.OPALLogger

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
 * ==Thread Safety==
 * '''This domain is not thread-safe'''. However, given the strong coupling of a
 * domain instance to a specific method this is usually not an issue.
 *
 * @author Michael Reif
 */
class CHACallGraphExtractor(
        val cache: CallGraphCache[MethodSignature, Set[Method]]
) extends CallGraphExtractor {

    protected[this] class AnalysisContext(
            val project:      SomeProject,
            val classFile:    ClassFile,
            val method:       Method,
            val cbsIndex:     CallBySignatureResolution,
            val analysisMode: AnalysisMode
    ) extends super.AnalysisContext {

        final val classHierarchy = project.classHierarchy

        def staticCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ) = {

            def handleUnresolvedMethodCall() = {
                addUnresolvedMethodCall(
                    classFile.thisType, method, pc,
                    declaringClassType, name, descriptor
                )
            }

            if (classHierarchy.isKnown(declaringClassType)) {
                classHierarchy.lookupMethodDefinition(
                    declaringClassType, name, descriptor, project
                ) match {
                    case Some(callee) ⇒
                        addCallEdge(pc, HashSet(callee))
                    case None ⇒
                        handleUnresolvedMethodCall()
                }
            } else {
                handleUnresolvedMethodCall()
            }
        }

        def doNonVirtualCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            def handleUnresolvedMethodCall(): Unit = {
                addUnresolvedMethodCall(
                    classFile.thisType, method, pc,
                    declaringClassType, name, descriptor
                )
            }

            if (classHierarchy.isKnown(declaringClassType)) {
                classHierarchy.lookupMethodDefinition(
                    declaringClassType, name, descriptor, project
                ) match {
                    case Some(callee) ⇒
                        val callees = HashSet(callee)
                        addCallEdge(pc, callees)
                    case None ⇒
                        handleUnresolvedMethodCall()
                }
            } else {
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

            if (receiverIsNull.isYesOrUnknown)
                addCallToNullPointerExceptionConstructor(classFile.thisType, method, pc)

            doNonVirtualCall(pc, declaringClassType, name, descriptor)
        }

        /**
         * @note A virtual method call is always an instance based call and never a call to
         *      a static method. However, the receiver may be `null` unless it is the
         *      self reference (`this`).
         */
        def virtualCall(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Unit = {

            addCallToNullPointerExceptionConstructor(classFile.thisType, method, pc)

            val cbsCalls = callBySignature(pc, declaringClassType, name, descriptor)

            val callees: Set[Method] = this.callees(declaringClassType, name, descriptor) ++ cbsCalls
            if (callees.isEmpty) {
                addUnresolvedMethodCall(
                    classFile.thisType, method, pc,
                    declaringClassType, name, descriptor
                )
            } else {
                addCallEdge(pc, callees)
            }
        }

        private[AnalysisContext] def callBySignature(
            pc:                 PC,
            declaringClassType: ObjectType,
            name:               String,
            descriptor:         MethodDescriptor
        ): Iterable[Method] = {
            if (analysisMode eq AnalysisModes.APP)
                return Iterable.empty[Method];

            if (!project.classHierarchy.
                allSuperinterfacetypes(declaringClassType, false).exists { iType ⇒
                    project.classFile(iType) match {
                        case Some(classFile) ⇒ !classFile.methods.exists { m ⇒
                            m.name == name && (m.descriptor eq descriptor)
                        }
                        case None ⇒ true
                    }
                }) {
                return cbsIndex.findMethods(
                    name,
                    descriptor,
                    declaringClassType,
                    analysisMode eq AnalysisModes.OPA
                );
            }

            Iterable.empty[Method]
        }
    }

    def extract(
        project:   SomeProject,
        classFile: ClassFile,
        method:    Method
    ): CallGraphExtractor.LocalCallGraphInformation = {
        val analysisMode = AnalysisModes.withName(project.config.as[String]("org.opalj.analysisMode"))
        val cbsIndex = project.get(CallBySignatureResolutionKey)
        val context = new AnalysisContext(project, classFile, method, cbsIndex, analysisMode)

        method.body.get.foreach { (pc, instruction) ⇒
            instruction.opcode match {
                case INVOKEVIRTUAL.opcode ⇒
                    val INVOKEVIRTUAL(declaringClass, name, descriptor) = instruction
                    if (declaringClass.isArrayType) {
                        context.nonVirtualCall(
                            pc, ObjectType.Object, name, descriptor, Unknown
                        )
                    } else {
                        context.virtualCall(
                            pc, declaringClass.asObjectType, name, descriptor
                        )
                    }
                case INVOKEINTERFACE.opcode ⇒
                    val INVOKEINTERFACE(declaringClass, name, descriptor) = instruction
                    context.virtualCall(pc, declaringClass, name, descriptor)

                case INVOKESPECIAL.opcode ⇒
                    val INVOKESPECIAL(declaringClass, name, descriptor) = instruction
                    // for invokespecial the dynamic type is not "relevant" (even for Java 8)
                    context.nonVirtualCall(
                        pc, declaringClass, name, descriptor,
                        receiverIsNull = No /*the receiver is "this" object*/ )

                case INVOKESTATIC.opcode ⇒
                    val INVOKESTATIC(declaringClass, name, descriptor) = instruction
                    context.staticCall(pc, declaringClass, name, descriptor)

                case _ ⇒
                // Nothing to do...
            }
        }

        (context.allCallEdges, context.unresolvableMethodCalls)
    }

}

