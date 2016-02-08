/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package methods

import org.opalj.br.BooleanType
import org.opalj.br.IntegerType
import org.opalj.br.LongType
import org.opalj.br.Method
import org.opalj.br.MethodDescriptor
import org.opalj.br.MethodDescriptor._
import org.opalj.br.ObjectType
import org.opalj.br.VoidType
import org.opalj.br.analyses.SomeProject

import scala.collection.mutable

/** Determines for each method if it potentially can be directly called by a client.
  * This can happens if:
  * - the method is directly visible to the client
  * - the method can become visible by a visible subclass which inherits the respective method
  * - the method can become pseudo-visible by a call on a superclass/interface (if the class is upcasted)
  *
  * @note This property is computed on-demand by a direct property computation.
  * @author Michael Reif
  */
sealed trait ClientCallable extends Property {
    final type Self = ClientCallable

    final def key = ClientCallableKey

    final def isRefineable = false
}

case object IsClientCallable extends ClientCallable

case object NotClientCallable extends ClientCallable

/** This Analysis determines the ´ClientCallable´ property of a method. A method is considered as leaked
  * if it is overridden in every immediate non-abstract subclass.
  *
  * In the following scenario, m defined by B overrides m in C and (in this specific scenario) m in C is
  * also always overridden.
  * {{{
  * 		/*package visible*/ class C { public Object m() }
  * 		/*package visible*/ abstract class A extends C { /*empty*/ }
  * 		public class B extends A { public Object m() }
  * }}}
  *
  * @author Michael Reif
  */
class CallableFromClassesInOtherPackagesAnalysis private (
    val project: SomeProject) extends FPCFAnalysis {

    /** Determines the [[ClientCallable]] property of non-static methods.
      * It is tailored to entry point set computation where we have to consider different kind of
      * program/library usage scenarios.
      * Computational differences regarding static methods are :
      * - private methods can be handled equal in every context
      * - if OPA is met, all package visible classes are visible which implies that all non-private methods are
      *   visible too
      * - if CPA is met, methods in package visible classes are not visible by default.
      *
      */
    def determineProperty(e: Entity): Property = {
        val method = e.asInstanceOf[Method]

        import org.opalj.util.GlobalPerformanceEvaluation

        GlobalPerformanceEvaluation.time('callableByOthers) {

            if (method.isPrivate)
                /* private methods are only visible in the scope of the class */
                return NotClientCallable;

            if (isOpenLibrary)
                return IsClientCallable;

            //we are now either analyzing a library under CPA or an application.
            if (method.isPackagePrivate || method.isConstructor)
                /* a package private method can not leak to the client under CPA */
                return NotClientCallable;

            val classFile = project.classFile(method)
            if (classFile.isEffectivelyFinal && !method.isPublic)
                return NotClientCallable;

            // From now on, we have the following conditions:
            // - the method is public or protected
            // - the class is not final
            if (classFile.isPublic)
                return IsClientCallable;

            val classHierarchy = project.classHierarchy

            val classType = classFile.thisType
            val methodName = method.name
            val methodDescriptor = method.descriptor

            // From now on we operate with a package private class
            // We first check whether the method is inherited by a visible subclass
            if (!classFile.isFinal) {
                val subtypes = mutable.Queue.empty ++= classHierarchy.directSubtypesOf(classType)
                while (subtypes.nonEmpty) {
                    val subtype = subtypes.dequeue()
                    project.classFile(subtype) match {
                        case Some(subclass) ⇒
                            if (subclass.findMethod(methodName, methodDescriptor).isEmpty) {
                                if (subclass.isPublic) {
                                    // the original method is now visible (and not shadowed)
                                    return IsClientCallable;
                                } else
                                    subtypes ++= classHierarchy.directSubtypesOf(subtype)
                            }
                        // we need to continue our search for a class that makes the method visible
                        case None ⇒
                            // The type hierarchy is obviously not downwards closed; i.e.,
                            // the project configuration is rather strange!
                            return IsClientCallable;
                    }
                }
            }

            //Now: A method does not become visible through a public subclass, but we also have to check the superclasses
            val EqualsSignature = MethodDescriptor(ObjectType.Object, BooleanType)
            val BasicWaitSignature = MethodDescriptor(LongType, VoidType)
            val PreciseWaitSignature = MethodDescriptor(IndexedSeq(LongType, IntegerType), VoidType)
            classHierarchy.foreachSupertype(classType) { supertype ⇒
                project.classFile(supertype) match {
                    case Some(superclass) ⇒ {
                        val declMethod = superclass.findMethod(methodName, methodDescriptor)
                        if (declMethod.isDefined) {
                            val m = declMethod.get
                            if ((m.isPublic || m.isProtected) && superclass.isPublic)
                                return IsClientCallable;
                        }
                    }
                    case None if supertype eq ObjectType.Object ⇒
                        (methodName, methodDescriptor) match {
                            case ("toString", JustReturnsString) |
                                ("hashCode", JustReturnsInteger) |
                                ("equals", EqualsSignature) |
                                ("clone", JustReturnsObject) |
                                ("getClass", JustReturnsClass) |
                                ("finalize", NoArgsAndReturnVoid) |
                                ("notify", NoArgsAndReturnVoid) |
                                ("notifyAll", NoArgsAndReturnVoid) |
                                ("wait", NoArgsAndReturnVoid) |
                                ("wait", BasicWaitSignature) |
                                ("wait", PreciseWaitSignature) ⇒
                                return IsClientCallable;
                            case _ ⇒ /* nothing leaks */
                        }
                    case _ ⇒
                        return IsClientCallable;
                }
            }

            NotClientCallable
        }
    }
}

object CallableFromClassesInOtherPackagesAnalysis extends FPCFAnalysisRunner {

    //    /**
    //     * Selects all non-static and non-abstract methods.
    //     */
    //    final def entitySelector(project: SomeProject): PartialFunction[Entity, Method] = {
    //        case m: Method if !m.isStatic && !m.isAbstract && !project.isLibraryType(project.classFile(m)) ⇒ m
    //    }

    override def derivedProperties: Set[PropertyKind] = Set(ClientCallableKey)

    protected[analysis] def start(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        val analysis = new CallableFromClassesInOtherPackagesAnalysis(project)
        propertyStore <<! (ClientCallableKey, analysis.determineProperty)
        analysis
    }
}
