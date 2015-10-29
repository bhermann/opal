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

import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import org.opalj.fpcf.Entity

/**
 * @author Michael Reif
 */
sealed trait ProjectAccessibility extends Property {
    final def key = ProjectAccessibility.Key
}

object ProjectAccessibility {
    final val Key = PropertyKey.create("Accessible", Global)
}

case object Global extends ProjectAccessibility { final val isRefineable = false }

case object PackageLocal extends ProjectAccessibility { final val isRefineable = false }

case object ClassLocal extends ProjectAccessibility { final val isRefineable = false }

/**
 * Determines the [[ProjectAccessibility]] property of static methods considering shadowing
 * of methods implemented by super classes.
 *
 * It is tailored to entry point set computation where we have to consider different kinds
 * of assumptions (open or closed packages assumption) depending on the analyzed program/library.
 *
 * Computational differences regarding static methods are :
 *  - private methods can be handled equal in every context
 *  - if OPA is met, all package visible classes are visible which implies that all non-private methods are
 *    visible too
 *  - if CPA is met, methods in package visible classes are not visible by default.
 *
 */
class StaticMethodAccessibilityAnalysis private (
    project: SomeProject
)
        extends FPCFAnalysisModeAnalysis[Method](
            project, StaticMethodAccessibilityAnalysis.entitySelector
        ) {

    def determineProperty(
        method: Method
    ): PropertyComputationResult = {
        if (method.isPrivate)
            return ImmediateResult(method, ClassLocal);

        if (isOpenLibrary)
            return ImmediateResult(method, Global);

        // THE ANALYSISMODE IS NOW "CLOSED LIBRARY" OR "APPLICATION"
        //

        if (method.isPackagePrivate)
            return ImmediateResult(method, PackageLocal);

        val classFile = project.classFile(method)
        if (classFile.isPublic && (method.isPublic || (!classFile.isFinal && method.isProtected)))
            return ImmediateResult(method, Global);

        val classHierarchy = project.classHierarchy

        val classType = classFile.thisType
        val methodDescriptor = method.descriptor
        val methodName = method.name

        classHierarchy.foreachSubtype(classType) { subtype ⇒
            project.classFile(subtype) match {
                case Some(subclass) ⇒
                    if (subclass.isPublic)
                        if (subclass.findMethod(methodName, methodDescriptor).isEmpty)
                            // the original method is now visible (and not shadowed)
                            return ImmediateResult(method, Global);
                // we need to continue our search for a class that makes the method visible
                case None ⇒
                    // The type hierarchy is obviously not downwards closed; i.e.,
                    // the project configuration is rather strange! 
                    return ImmediateResult(method, Global);
            }
        }
        ImmediateResult(method, PackageLocal)
    }
}

/**
 * Companion object for the [[StaticMethodAccessibilityAnalysis]] class.
 */
object StaticMethodAccessibilityAnalysis
        extends FPCFAnalysisRunner[StaticMethodAccessibilityAnalysis] {

    private[StaticMethodAccessibilityAnalysis] def entitySelector: PartialFunction[Entity, Method] = {
        case m: Method if m.isStatic && !m.isStaticInitializer ⇒ m
    }

    private[StaticMethodAccessibilityAnalysis] def apply(
        project: SomeProject
    ): StaticMethodAccessibilityAnalysis = {
        new StaticMethodAccessibilityAnalysis(project)
    }

    protected def start(project: SomeProject): Unit = {
        StaticMethodAccessibilityAnalysis(project)
    }
}