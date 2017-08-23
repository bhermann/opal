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
package fpcf
package analysis

import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import scala.collection.mutable.ListBuffer
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyKind
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.properties.ClassLocal
import org.opalj.fpcf.properties.Global
import org.opalj.fpcf.properties.PackageLocal
import org.opalj.fpcf.properties.ProjectAccessibility
import org.opalj.fpcf.properties.NotClientCallable
import org.opalj.fpcf.properties.ClientCallable

/**
 *
 *
 * @author Michael Reif
 */
class MethodAccessibilityAnalysis(val project: SomeProject) extends FPCFAnalysis {

    def determineProperty(method: Method): PropertyComputationResult = {
        import org.opalj.util.GlobalPerformanceEvaluation

        GlobalPerformanceEvaluation.time('methodAccess) {
            if (method.isPrivate)
                return ImmediateResult(method, ClassLocal)

            if (isOpenLibrary)
                return ImmediateResult(method, Global)

            // THE ANALYSISMODE IS NOW "CLOSED LIBRARY" OR "APPLICATION"
            //
            if (method.isPackagePrivate)
                return ImmediateResult(method, PackageLocal)

            if (method.isStatic)
                determineStaticMethodAccessibility(method)
            else
                determineInstanceMethodAccessibility(method)
        }
    }

    private[this] def determineStaticMethodAccessibility(
        method: Method
    ): PropertyComputationResult = {

        val classFile = method.classFile
        if (classFile.isPublic && (method.isPublic || (!classFile.isFinal && method.isProtected)))
            return ImmediateResult(method, Global);

        val classHierarchy = project.classHierarchy

        val classType = classFile.thisType
        val methodDescriptor = method.descriptor
        val methodName = method.name

        val subtypes = ListBuffer.empty ++= classHierarchy.directSubtypesOf(classType)
        while (subtypes.nonEmpty) {
            val subtype = subtypes.head
            project.classFile(subtype) match {
                case Some(subclass) ⇒
                    if (subclass.findMethod(methodName, methodDescriptor).isEmpty)
                        if (subclass.isPublic) {
                            // the original method is now visible (and not shadowed)
                            return ImmediateResult(method, Global);
                        } else
                            subtypes ++= classHierarchy.directSubtypesOf(subtype)

                // we need to continue our search for a class that makes the method visible
                case None ⇒
                    // The type hierarchy is obviously not downwards closed; i.e.,
                    // the project configuration is rather strange!
                    return ImmediateResult(method, Global);
            }
            subtypes -= subtype
        }

        // The method does not become visible through inheritance.
        // Hence, it is not globally visible.
        ImmediateResult(method, PackageLocal)
    }

    private[this] def determineInstanceMethodAccessibility(
        method: Method
    ): PropertyComputationResult = {

        val classFile = method.classFile
        val isFinalClass = classFile.isFinal
        val isPublicClass = classFile.isPublic

        val isPublicMethod = method.isPublic
        val isProtectedMethod = method.isProtected

        if (isPublicClass && (isPublicMethod || (!isFinalClass && isProtectedMethod)))
            return ImmediateResult(method, Global);

        def c(dependeeE: Entity, dependeeP: Property) = {
            if (dependeeP == NotClientCallable)
                Result(method, PackageLocal)
            else
                Result(method, Global)
        }

        propertyStore.require(
            method, ProjectAccessibility.Key,
            method, ClientCallable.Key
        )(
            c
        )
    }
}

/**
 * Companion object for the [[MethodAccessibilityAnalysis]] class.
 */
object MethodAccessibilityAnalysis extends FPCFAnalysisRunner {

    def start(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        val analysis = new MethodAccessibilityAnalysis(project)
        propertyStore.scheduleForCollected {
            case m: Method if !m.isStaticInitializer && (m.isNative || !m.isAbstract) ⇒ m
        }(
            analysis.determineProperty
        )
        analysis
    }

    override def recommendations: Set[FPCFAnalysisRunner] = {
        Set(CallableFromClassesInOtherPackagesAnalysis)
    }

    override def derivedProperties: Set[PropertyKind] = {
        Set(ProjectAccessibility.Key)
    }

    override def usedProperties: Set[PropertyKind] = {
        Set(ClientCallable.Key)
    }
}

/**
 * Companion object for the [[StaticMethodAccessibilityAnalysis]] class.
 */
object StaticMethodAccessibilityAnalysis extends FPCFAnalysisRunner {

    override def recommendations: Set[FPCFAnalysisRunner] = {
        Set(CallableFromClassesInOtherPackagesAnalysis)
    }

    override def derivedProperties: Set[PropertyKind] = {
        Set(ProjectAccessibility.Key)
    }

    override def usedProperties: Set[PropertyKind] = {
        Set(ClientCallable.Key)
    }

    def start(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        val analysis = new MethodAccessibilityAnalysis(project)
        propertyStore.scheduleForCollected {
            case m: Method if m.isStatic && !m.isStaticInitializer && (m.isNative || !m.isAbstract) ⇒ m
        }(
            analysis.determineProperty
        )
        analysis
    }
}
