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
package analyses

import scala.annotation.switch
import org.opalj.ai.Domain
import org.opalj.ai.domain.RecordDefUse
import org.opalj.br.ComputationalTypeReference
import org.opalj.br.FieldType
import org.opalj.br.Method
import org.opalj.br.MethodDescriptor
import org.opalj.br.ObjectType
import org.opalj.br.ReferenceType
import org.opalj.br.analyses.SomeProject
import org.opalj.fpcf.properties.AtLeastConditionallyImmutableObject
import org.opalj.fpcf.properties.AtLeastConditionallyImmutableType
import org.opalj.fpcf.properties.ClassImmutability
import org.opalj.fpcf.properties.ConditionallyPure
import org.opalj.fpcf.properties.ConditionallySideEffectFree
import org.opalj.fpcf.properties.EffectivelyFinalField
import org.opalj.fpcf.properties.FieldMutability
import org.opalj.fpcf.properties.ImmutableObject
import org.opalj.fpcf.properties.ImmutableType
import org.opalj.fpcf.properties.Impure
import org.opalj.fpcf.properties.MaybePure
import org.opalj.fpcf.properties.Pure
import org.opalj.fpcf.properties.Purity
import org.opalj.fpcf.properties.SideEffectFree
import org.opalj.fpcf.properties.TypeImmutability
import org.opalj.tac.ArrayLength
import org.opalj.tac.ArrayLoad
import org.opalj.tac.ArrayStore
import org.opalj.tac.Assignment
import org.opalj.tac.BinaryExpr
import org.opalj.tac.Checkcast
import org.opalj.tac.Compare
import org.opalj.tac.DUVar
import org.opalj.tac.DefaultTACAIKey
import org.opalj.tac.Expr
import org.opalj.tac.ExprStmt
import org.opalj.tac.GetField
import org.opalj.tac.GetStatic
import org.opalj.tac.If
import org.opalj.tac.InstanceOf
import org.opalj.tac.Invokedynamic
import org.opalj.tac.MonitorEnter
import org.opalj.tac.MonitorExit
import org.opalj.tac.New
import org.opalj.tac.NewArray
import org.opalj.tac.NonVirtualFunctionCall
import org.opalj.tac.NonVirtualMethodCall
import org.opalj.tac.PrefixExpr
import org.opalj.tac.PrimitiveTypecastExpr
import org.opalj.tac.PutField
import org.opalj.tac.PutStatic
import org.opalj.tac.ReturnValue
import org.opalj.tac.StaticFunctionCall
import org.opalj.tac.StaticMethodCall
import org.opalj.tac.Stmt
import org.opalj.tac.Switch
import org.opalj.tac.Throw
import org.opalj.tac.VirtualFunctionCall
import org.opalj.tac.VirtualMethodCall

/**
 * A simple analysis for method purity based on the three address code representation.
 *
 * @author Dominik Helm
 *
 * @note This analysis is sound even if the three address code hierarchy is not flat, it will
 *       produce better results for a flat hierarchy, though.
 */
class MethodPurityAnalysis private (val project: SomeProject) extends FPCFAnalysis {

    type V = DUVar[(Domain with RecordDefUse)#DomainValue]

    private val tacai = project.get(DefaultTACAIKey)

    def determinePurity(method: Method): PropertyComputationResult = {

        // Due to a lack of knowledge, we classify all native methods or methods that
        // have no body - because they are loaded using a library class file loader - as Impure.
        if (method.body.isEmpty /*HERE: method.isNative || "isLibraryMethod(method)"*/ )
            return ImmediateResult(method, Impure)

        // We treat all synchronized methods as impure
        if (method.isSynchronized)
            return ImmediateResult(method, Impure)

        val declClass = method.classFile.thisType

        var dependees: Set[EOptionP[Entity, Property]] = Set()
        var purity: Purity = Pure

        val code = tacai(method).stmts

        /**
         * Checks trivially if a reference was created locally, hence actions on it might not
         * influence purity.
         *
         * @note Fresh references can be treated as non-escaping as the analysis result will be
         *       impure anyways if anything escapes the method
         */
        def isFreshReference(expr: Expr[V]): Boolean = {
            // We only examine vars for now
            // TODO [FlatTAC] Can we do better?
            expr.isVar && expr.asVar.definedBy.forall { defSite ⇒
                if (defSite >= 0) {
                    assert(code(defSite).astID == Assignment.ASTID, "defSite should be assignment")
                    val astID = code(defSite).asAssignment.expr.astID
                    astID == New.ASTID || astID == NewArray.ASTID
                } else {
                    // In initializer methods, the receiver object is fresh
                    method.isConstructor && defSite == -1
                }
            }
        }

        /**
         * Examines a statement for its influence on the method purity.
         * This method will return false for impure statements,
         * so evaluation can be terminated early.
         */
        def examineStmt(stmt: Stmt[V]): Boolean = (stmt.astID: @switch) match {
            case StaticMethodCall.ASTID ⇒
                val StaticMethodCall(_, declClass, interface, name, descr, params) = stmt
                val callee = project.staticCall(declClass.asObjectType, interface, name, descr)
                examineCall(declClass, name, None, params, callee)
            case NonVirtualMethodCall.ASTID ⇒
                val NonVirtualMethodCall(_, declClass, interface, name, descr, rcvr, params) = stmt
                val callee =
                    project.specialCall(declClass.asObjectType, interface, name, descr)
                examineCall(declClass, name, Some(rcvr), params, callee)
            case VirtualMethodCall.ASTID ⇒
                val VirtualMethodCall(_, declClass, interface, name, descr, rcvr, params) = stmt
                examineVirtualCall(declClass, interface, name, rcvr, params, descr)

            case If.ASTID ⇒
                val If(_, left, _, right, _) = stmt
                examineExpr(left) && examineExpr(right)
            case Switch.ASTID ⇒
                val Switch(_, _, index, _) = stmt
                examineExpr(index)
            case Assignment.ASTID ⇒
                val Assignment(_, _, value) = stmt
                examineExpr(value)
            case ReturnValue.ASTID ⇒
                val ReturnValue(_, value) = stmt
                if (!isFreshReference(value)) examineReturn(value)
                examineExpr(value)
            case Throw.ASTID ⇒
                val Throw(_, ex) = stmt
                if (!isFreshReference(ex)) examineReturn(ex)
                examineExpr(ex)
            case MonitorEnter.ASTID ⇒
                val MonitorEnter(_, objRef) = stmt
                isFreshReference(objRef) // Synchronization on local objects is pure
            case MonitorExit.ASTID ⇒
                val MonitorExit(_, objRef) = stmt
                isFreshReference(objRef) // Synchronization on local objects is pure
            case ArrayStore.ASTID ⇒
                val ArrayStore(_, arrayRef, index, value) = stmt
                isFreshReference(arrayRef) && examineExpr(index) && examineExpr(value)
            case PutField.ASTID ⇒
                val PutField(_, _, _, _, objRef, value) = stmt
                isFreshReference(objRef) && examineExpr(value)
            case PutStatic.ASTID ⇒
                //TODO This is probably pure in a static initializer if the field assigned is a static field of this class. Is it?
                false
            case ExprStmt.ASTID ⇒
                val ExprStmt(_, expr) = stmt
                examineExpr(expr)
            case Checkcast.ASTID ⇒
                val Checkcast(_, subExpr, _) = stmt
                examineExpr(subExpr)

            case _ ⇒ true // Other statements do not influence purity
        }

        /**
         * Examines an expression for its influence on the method purity.
         * This method will return false for impure expressions,
         * so evaluation can be terminated early.
         */
        def examineExpr(expr: Expr[V]): Boolean = (expr.astID: @switch) match {
            case StaticFunctionCall.ASTID ⇒
                val StaticFunctionCall(_, declClass, interface, name, descr, params) = expr
                val callee = project.staticCall(declClass.asObjectType, interface, name, descr)
                examineCall(declClass, name, None, params, callee)
            case NonVirtualFunctionCall.ASTID ⇒
                val NonVirtualFunctionCall(_, declClass, interface, name, descr, rcvr, params) = expr
                val callee =
                    project.specialCall(declClass.asObjectType, interface, name, descr)
                examineCall(declClass, name, Some(rcvr), params, callee)
            case VirtualFunctionCall.ASTID ⇒
                val VirtualFunctionCall(_, declClass, interface, name, descr, rcvr, params) = expr
                examineVirtualCall(declClass, interface, name, rcvr, params, descr)

            case InstanceOf.ASTID ⇒
                val InstanceOf(_, subExpr, _) = expr
                examineExpr(subExpr)
            case Compare.ASTID ⇒
                val Compare(_, left, _, right) = expr
                examineExpr(left) && examineExpr(right)
            case BinaryExpr.ASTID ⇒
                val BinaryExpr(_, _, _, left, right) = expr
                examineExpr(left) && examineExpr(right)
            case PrefixExpr.ASTID ⇒
                val PrefixExpr(_, _, _, operand) = expr
                examineExpr(operand)
            case PrimitiveTypecastExpr.ASTID ⇒
                val PrimitiveTypecastExpr(_, _, operand) = expr
                examineExpr(operand)
            case GetStatic.ASTID ⇒
                val GetStatic(_, declaringClass, name, fieldType) = expr
                examineField(declaringClass, name, fieldType)
                true
            case GetField.ASTID ⇒
                val GetField(_, declaringClass, name, fieldType, objRef) = expr
                if (!isFreshReference(objRef)) examineField(declaringClass, name, fieldType)
                examineExpr(objRef)
            case ArrayLoad.ASTID ⇒
                val ArrayLoad(_, index, arrayRef) = expr
                if (!isFreshReference(arrayRef)) purity = SideEffectFree
                examineExpr(arrayRef) && examineExpr(index)
            case NewArray.ASTID ⇒
                val NewArray(_, counts, _) = expr
                counts.forall(examineExpr(_))
            case ArrayLength.ASTID   ⇒ true // The array length is immutable
            case Invokedynamic.ASTID ⇒ false // We don't handle Invokedynamic for now

            case _                   ⇒ true // Other expressions don't influence purity
        }

        /**
         * Examines a virtual call for its influence on the method purity.
         * Resolves the call and uses examineCall to examine the individual possible callees.
         * This method will return false for impure calls, so
         * evaluation can be terminated early.
         */
        def examineVirtualCall(
            receiverClass: ReferenceType,
            interface:     Boolean,
            name:          String,
            receiver:      Expr[V],
            params:        Seq[Expr[V]],
            descr:         MethodDescriptor
        ): Boolean = {
            if (receiver.isVar && receiver.asVar.value.asDomainReferenceValue.isPrecise) {
                val rcvr = receiver.asVar.value.asDomainReferenceValue
                if (rcvr.isNull.isYes)
                    true // We don't have to examine calls that will result in an NPE
                else {
                    val callee = project.instanceCall(declClass, rcvr.valueType.get, name, descr)
                    examineCall(receiverClass, name, Some(receiver), params, callee)
                }
            } else {
                // TODO Replace by call graph, currently this is only soundy for simple applications with closed call graph
                val callees =
                    if (interface) project.interfaceCall(receiverClass.asObjectType, name, descr)
                    else project.virtualCall(declClass.packageName, receiverClass, name, descr)
                if (callees.isEmpty)
                    // We know nothing about the target methods
                    // (they are not in the scope of the current project)
                    false
                else
                    callees.forall { callee ⇒
                        examineCall(receiverClass, name, Some(receiver), params, Success(callee))
                    }
            }
        }

        /**
         * Examines a call to a given callee for its influence on the method purity.
         * This method will return False for impure calls, so evaluation can be terminated early.
         */
        def examineCall(
            receiverClass: ReferenceType,
            name:          String,
            receiver:      Option[Expr[V]],
            params:        Seq[Expr[V]],
            methodResult:  org.opalj.Result[Method]
        ): Boolean = {

            if (receiverClass == ObjectType.Object && "<init>" == name)
                true // The java.lang.Object constructor is pure
            else {
                receiver.forall(examineExpr(_)) && params.forall(examineExpr(_)) && {
                    methodResult match {
                        case Success(callee) ⇒
                            if (callee == method) true // Self-recursive don't need to be checked
                            else {
                                val calleePurity = propertyStore(callee, Purity.key)
                                calleePurity match {
                                    case EP(_, Pure) ⇒ true
                                    case EP(_, SideEffectFree) ⇒
                                        purity = SideEffectFree
                                        true
                                    case ep @ EP(_, ConditionallySideEffectFree) ⇒
                                        dependees += ep
                                        purity = SideEffectFree
                                        true
                                    case ep @ EP(_, ConditionallyPure) ⇒
                                        dependees += ep
                                        true
                                    case EP(_, _) ⇒ false // Impure or unknown purity level
                                    case epk ⇒
                                        dependees += epk
                                        true
                                }
                            }
                        case _ ⇒ false // Target method unknown (not in scope of current project)
                    }
                }
            }
        }

        /**
         * Examines whether reading a field has an influence on the method purity.
         * Reading values from fields that are not (effectively) final may cause nondeterministic
         * behavior, so the method can only be side-effect free.
         */
        def examineField(declaringClass: ObjectType, name: String, fieldType: FieldType): Unit = {
            if (purity != SideEffectFree) { // Don't do costly dependee checks if already not pure
                project.resolveFieldReference(declaringClass, name, fieldType) match {
                    case Some(field) if field.isFinal ⇒ // constants do not impede purity!
                    case Some(field) if field.isPrivate /*&& field.isNonFinal*/ ⇒
                        val fieldMutability = propertyStore(field, FieldMutability.key)
                        fieldMutability match {
                            case EP(_, EffectivelyFinalField) ⇒ // Final fields don't impede purity
                            case EP(_, _)                     ⇒ purity = SideEffectFree
                            case epk                          ⇒ dependees += epk
                        }
                    case _ ⇒ purity = SideEffectFree // Mutable or unknown field
                }
            }
        }

        /**
         * Examines the effect of returning a value on the method purity.
         * Returning a reference to a mutable object or array may cause nondeterministic behavior
         * as the object/array may be modified between invocations of the method, so the method can
         * only be side-effect free.
         */
        def examineReturn(returnValue: Expr[V]): Unit = {
            // Only non-primitive return values influence purity
            // Also, we don't have to do costly dependee checks if we are already side-effect free
            if (returnValue.cTpe == ComputationalTypeReference && purity != SideEffectFree) {
                if (!returnValue.isVar) {
                    // For now, we only examine the types of Vars
                    // TODO [FlatTAC] Can we do better?
                    purity = SideEffectFree
                } else {
                    val value = returnValue.asVar.value.asDomainReferenceValue
                    if (value.isNull.isNoOrUnknown) { // Null is immutable
                        if (value.upperTypeBound.exists(_.isArrayType)) {
                            purity = SideEffectFree // Arrays are always mutable
                        } else if (value.isPrecise) { // Precise class known, use ClassImmutability
                            val cfo = project.classFile(value.upperTypeBound.head.asObjectType)
                            if (cfo.isEmpty)
                                purity = SideEffectFree // Unknown class, might be mutable
                            else
                                propertyStore(cfo.get, ClassImmutability.key) match {
                                    case EP(_, ImmutableObject) ⇒
                                    // Returning immutable objects is pure
                                    case ep @ EP(_, AtLeastConditionallyImmutableObject) ⇒
                                        dependees += ep
                                    case EP(_, _) ⇒ purity = SideEffectFree
                                    case epk      ⇒ dependees += epk
                                }
                        } else { // Precise class unknown, use TypeImmutability
                            val cfos = value.upperTypeBound.map { tpe ⇒
                                project.classFile(tpe.asObjectType)
                            }
                            if (cfos.exists(_.isEmpty))
                                purity = SideEffectFree // Unknown class, might be mutable
                            else
                                cfos.forall { cfo ⇒
                                    propertyStore(cfo.get, TypeImmutability.key) match {
                                        case EP(_, ImmutableType) ⇒
                                            true // Returning immutable objects is pure
                                        case ep @ EP(_, AtLeastConditionallyImmutableType) ⇒
                                            dependees += ep
                                            true
                                        case EP(_, _) ⇒
                                            purity = SideEffectFree
                                            false // Return early if we are already side-effect free
                                        case epk ⇒
                                            dependees += epk
                                            true
                                    }
                                }
                        }
                    }
                }
            }
        }

        /**
         * Continuation to handle updates to properties of dependees.
         * Dependees may be
         *     - methods called (for their purity)
         *     - fields read (for their mutability)
         *     - classes files for classes or types returned (for their mutability)
         */
        def c(e: Entity, p: Property, u: UpdateType): PropertyComputationResult = {
            var impure = false
            dependees = dependees.filter(_.e ne e)
            p match {
                // Cases resulting in conditional purity
                case ConditionallyPure |
                    AtLeastConditionallyImmutableType | AtLeastConditionallyImmutableObject ⇒
                    val newEP = EP(e, p)
                    dependees += newEP // For conditional result, keep the dependence

                // Cases resulting in conditional side-effect freeness
                case ConditionallySideEffectFree ⇒
                    val newEP = EP(e, p)
                    dependees += newEP // For conditional result, keep the dependence
                    purity = SideEffectFree

                // Cases that are pure
                case Pure | // Call to pure method
                    EffectivelyFinalField | // Reading eff. final fields
                    ImmutableType | ImmutableObject ⇒ // Returning immutable reference

                // Cases resulting in side-effect freeness
                case SideEffectFree | // Call to side-effect free method
                    _: FieldMutability | // Reading non-final field
                    _: TypeImmutability | _: ClassImmutability ⇒ // Returning mutable reference
                    purity = SideEffectFree

                // Cases resulting in impurity
                case Impure | MaybePure | // Call to impure method
                    _ ⇒ // Unknown property
                    impure = true
            }

            if (impure) {
                Result(method, Impure)
            } else if (dependees.isEmpty) {
                Result(method, purity)
            } else if (purity == Pure) {
                IntermediateResult(method, ConditionallyPure, dependees, c)
            } else {
                IntermediateResult(method, ConditionallySideEffectFree, dependees, c)
            }
        }

        for (stmt ← code) {
            if (!examineStmt(stmt)) // Early return for impure statements
                return ImmediateResult(method, Impure)
        }

        // Every method that is not identified as being impure is (conditionally) pure or
        // (conditionally) side-effect free.
        if (dependees.isEmpty) {
            ImmediateResult(method, purity)
        } else if (purity == Pure) {
            IntermediateResult(method, ConditionallyPure, dependees, c)
        } else {
            IntermediateResult(method, ConditionallySideEffectFree, dependees, c)
        }
    }
}

object MethodPurityAnalysis extends FPCFAnalysisRunner {

    override def derivedProperties: Set[PropertyKind] = Set(Purity.key)

    override def usedProperties: Set[PropertyKind] =
        Set(FieldMutability, ClassImmutability, TypeImmutability)

    protected[fpcf] def start(
        project:       SomeProject,
        propertyStore: PropertyStore
    ): FPCFAnalysis = {
        def analysis = new MethodPurityAnalysis(project)
        propertyStore.scheduleForEntities(project.allMethodsWithBody)(analysis.determinePurity)
        analysis
    }
}
