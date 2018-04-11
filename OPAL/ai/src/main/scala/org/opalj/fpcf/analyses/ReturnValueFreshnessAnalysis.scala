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

import org.opalj
import org.opalj.ai.Domain
import org.opalj.ai.ValueOrigin
import org.opalj.ai.domain.RecordDefUse
import org.opalj.br.AllocationSite
import org.opalj.br.DeclaredMethod
import org.opalj.br.DefinedMethod
import org.opalj.br.Field
import org.opalj.br.Method
import org.opalj.br.MethodDescriptor
import org.opalj.br.ObjectType
import org.opalj.br.analyses.AllocationSites
import org.opalj.br.analyses.SomeProject
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.analyses.escape.AbstractEscapeAnalysisState
import org.opalj.fpcf.analyses.escape.FallBackEscapeAnalysis
import org.opalj.fpcf.properties.AtMost
import org.opalj.fpcf.properties.Conditional
import org.opalj.fpcf.properties.ConditionalExtensibleGetter
import org.opalj.fpcf.properties.ConditionalExtensibleLocalField
import org.opalj.fpcf.properties.ConditionalExtensibleLocalFieldWithGetter
import org.opalj.fpcf.properties.ConditionalGetter
import org.opalj.fpcf.properties.ConditionalLocalField
import org.opalj.fpcf.properties.ConditionalLocalFieldWithGetter
import org.opalj.fpcf.properties.EscapeInCallee
import org.opalj.fpcf.properties.EscapeProperty
import org.opalj.fpcf.properties.EscapeViaReturn
import org.opalj.fpcf.properties.ExtensibleGetter
import org.opalj.fpcf.properties.ExtensibleLocalField
import org.opalj.fpcf.properties.ExtensibleLocalFieldWithGetter
import org.opalj.fpcf.properties.FieldLocality
import org.opalj.fpcf.properties.FreshReturnValue
import org.opalj.fpcf.properties.Getter
import org.opalj.fpcf.properties.LocalField
import org.opalj.fpcf.properties.LocalFieldWithGetter
import org.opalj.fpcf.properties.NoEscape
import org.opalj.fpcf.properties.NoFreshReturnValue
import org.opalj.fpcf.properties.NoLocalField
import org.opalj.fpcf.properties.PrimitiveReturnValue
import org.opalj.fpcf.properties.ReturnValueFreshness
import org.opalj.fpcf.properties.VConditionalExtensibleGetter
import org.opalj.fpcf.properties.VConditionalGetter
import org.opalj.fpcf.properties.VExtensibleGetter
import org.opalj.fpcf.properties.VFreshReturnValue
import org.opalj.fpcf.properties.VGetter
import org.opalj.fpcf.properties.VNoFreshReturnValue
import org.opalj.fpcf.properties.VPrimitiveReturnValue
import org.opalj.fpcf.properties.VirtualMethodReturnValueFreshness
import org.opalj.tac.Assignment
import org.opalj.tac.Const
import org.opalj.tac.DUVar
import org.opalj.tac.DefaultTACAIKey
import org.opalj.tac.GetField
import org.opalj.tac.New
import org.opalj.tac.NewArray
import org.opalj.tac.NonVirtualFunctionCall
import org.opalj.tac.ReturnValue
import org.opalj.tac.StaticFunctionCall
import org.opalj.tac.Stmt
import org.opalj.tac.TACMethodParameter
import org.opalj.tac.TACode
import org.opalj.tac.VirtualFunctionCall

class ReturnValueFreshnessState(val dm: DeclaredMethod) {
    private[this] var returnValueDependees: Set[EOptionP[DeclaredMethod, Property]] = Set.empty
    private[this] var fieldDependees: Set[EOptionP[Field, FieldLocality]] = Set.empty
    private[this] var allocationSiteDependees: Set[EOptionP[AllocationSite, EscapeProperty]] = Set.empty

    private[this] var temporary: ReturnValueFreshness = FreshReturnValue

    def dependees: Set[EOptionP[Entity, Property]] = {
        returnValueDependees ++ fieldDependees ++ allocationSiteDependees
    }

    def hasDependees: Boolean = dependees.nonEmpty

    def addMethodDependee(epOrEpk: EOptionP[DeclaredMethod, Property]): Unit = {
        returnValueDependees += epOrEpk
    }

    def addFieldDependee(epOrEpk: EOptionP[Field, FieldLocality]): Unit = {
        fieldDependees += epOrEpk
    }

    def addAllocationDependee(epOrEpk: EOptionP[AllocationSite, EscapeProperty]): Unit = {
        allocationSiteDependees += epOrEpk
    }

    def removeMethodDependee(epOrEpk: EOptionP[DeclaredMethod, Property]): Unit = {
        returnValueDependees = returnValueDependees.filter(other ⇒ (other.e ne epOrEpk.e) || other.pk != epOrEpk.pk)
    }

    def removeFieldDependee(epOrEpk: EOptionP[Field, FieldLocality]): Unit = {
        fieldDependees = fieldDependees.filter(other ⇒ (other.e ne epOrEpk.e) || other.pk != epOrEpk.pk)
    }

    def removeAllocationDependee(epOrEpk: EOptionP[AllocationSite, EscapeProperty]): Unit = {
        allocationSiteDependees = allocationSiteDependees.filter(other ⇒ (other.e ne epOrEpk.e) || other.pk != epOrEpk.pk)
    }

    def updateWithMeet(property: ReturnValueFreshness): Unit = {
        temporary = temporary meet property
    }

    def temporaryState: ReturnValueFreshness = temporary

}

/**
 * An analysis that determines for a given method, whether its the return value is a fresh object,
 * that is created within the method and does not escape by other than [[EscapeViaReturn]].
 *
 * In other words, it aggregates the escape information for all allocation-sites, that might be used
 * as return value.
 *
 * @author Florian Kuebler
 */
class ReturnValueFreshnessAnalysis private ( final val project: SomeProject) extends FPCFAnalysis {
    type V = DUVar[(Domain with RecordDefUse)#DomainValue]
    private[this] val tacaiProvider: (Method) ⇒ TACode[TACMethodParameter, V] = project.get(DefaultTACAIKey)
    private[this] val allocationSites: AllocationSites = propertyStore.context[AllocationSites]
    private[this] val declaredMethods: DeclaredMethods = propertyStore.context[DeclaredMethods]

    def determineFreshness(e: Entity): PropertyComputationResult = e match {
        case dm: DefinedMethod ⇒ doDetermineFreshness(dm)
        case _                 ⇒ throw new RuntimeException(s"Unsupported entity $e")
    }

    def doDetermineFreshness(dm: DefinedMethod): PropertyComputationResult = {

        if (dm.descriptor.returnType.isBaseType || dm.descriptor.returnType.isVoidType)
            return Result(dm, PrimitiveReturnValue)

        if (dm.declaringClassType.isArrayType) {
            if (dm.name == "clone" && dm.descriptor == MethodDescriptor.JustReturnsObject) {
                return Result(dm, FreshReturnValue)
            }
        }

        val m = dm.definedMethod
        if (m.body.isEmpty)
            return Result(dm, NoFreshReturnValue)

        implicit val state: ReturnValueFreshnessState = new ReturnValueFreshnessState(dm)
        implicit val p: SomeProject = project

        val code = tacaiProvider(m).stmts

        // for every return-value statement check the def-sites
        for {
            ReturnValue(_, expr) ← code
            defSite ← expr.asVar.definedBy
        } {

            // parameters are not fresh by definition
            if (defSite < 0)
                return Result(dm, NoFreshReturnValue)

            code(defSite) match {
                // if the def-site of the return-value statement is a new, we check the escape state
                case Assignment(pc, _, New(_, _) | NewArray(_, _, _)) ⇒
                    val allocationSite = allocationSites(m)(pc)
                    val resultState = propertyStore(allocationSite, EscapeProperty.key)
                    handleEscapeProperty(resultState).foreach(x ⇒ return Result(dm, x))

                /*
                 * if the def-site came from a field and the field is local except for the existence
                 * of a getter, we can report this method as being a getter.
                 */
                case Assignment(_, tgt, GetField(_, declaringClass, name, fieldType, objRef)) ⇒
                    if (objRef.asVar.definedBy != IntTrieSet(tac.OriginOfThis))
                        return Result(dm, NoFreshReturnValue)

                    val field = project.resolveFieldReference(declaringClass, name, fieldType) match {
                        case Some(f) ⇒ f
                        case _       ⇒ return Result(dm, NoFreshReturnValue)
                    }

                    handleEscape(defSite, tgt.usedBy, code).foreach(return _)

                    val locality = propertyStore(field, FieldLocality.key)
                    handleFieldLocalityProperty(locality).foreach(x ⇒ return Result(dm, x))

                // const values are handled as fresh
                case Assignment(_, _, _: Const) ⇒

                case Assignment(_, tgt, call: StaticFunctionCall[V]) ⇒
                    handleEscape(defSite, tgt.usedBy, code).foreach(return _)

                    val callee = call.resolveCallTarget
                    handleConcreteCall(callee).foreach(return _)

                case Assignment(_, tgt, call: NonVirtualFunctionCall[V]) ⇒
                    handleEscape(defSite, tgt.usedBy, code).foreach(return _)

                    val callee = call.resolveCallTarget
                    handleConcreteCall(callee).foreach(return _)

                case Assignment(_, tgt, VirtualFunctionCall(_, dc, _, name, desc, receiver, _)) ⇒
                    handleEscape(defSite, tgt.usedBy, code).foreach(return _)

                    val value = receiver.asVar.value.asDomainReferenceValue

                    if (value.isNull.isNoOrUnknown) {
                        val receiverType =
                            project.classHierarchy.joinReferenceTypesUntilSingleUpperBound(
                                value.upperTypeBound
                            )

                        if (receiverType.isArrayType) {
                            val callee = project.instanceCall(
                                ObjectType.Object, ObjectType.Object, name, desc
                            )
                            handleConcreteCall(callee).foreach(return _)
                        } else if (value.isPrecise) {
                            val preciseType = value.valueType.get
                            val callee = project.instanceCall(
                                m.classFile.thisType, preciseType, name, desc
                            )
                            handleConcreteCall(callee).foreach(return _)
                        } else {
                            var callee = project.instanceCall(m.classFile.thisType, dc, name, desc)

                            // check if the method is abstract?
                            if (callee.isEmpty) {
                                project.classFile(receiverType.asObjectType) match {
                                    case Some(cf) ⇒
                                        callee = if (cf.isInterfaceDeclaration) {
                                            org.opalj.Result(
                                                project.resolveInterfaceMethodReference(
                                                    receiverType.asObjectType, name, desc
                                                )
                                            )
                                        } else {
                                            project.resolveClassMethodReference(
                                                receiverType.asObjectType, name, desc
                                            )
                                        }
                                    case None ⇒
                                        return Result(dm, NoFreshReturnValue)
                                }
                            }

                            // unkown method
                            if (callee.isEmpty)
                                return Result(dm, NoFreshReturnValue)

                            val dmCallee = declaredMethods(callee.value)
                            val rvf = propertyStore(dmCallee, VirtualMethodReturnValueFreshness.key)
                            handleReturnValueFreshness(rvf).foreach(x ⇒ return Result(dm, x))
                        }
                    }

                // other kinds of assignments like expressions etc.
                case Assignment(_, _, _) ⇒ return Result(dm, NoFreshReturnValue)
                case _                   ⇒ throw new RuntimeException("not yet implemented")
            }
        }

        returnResult
    }

    def handleConcreteCall(callee: opalj.Result[Method])(implicit state: ReturnValueFreshnessState): Option[PropertyComputationResult] = {
        // unkown method
        if (callee.isEmpty)
            return Some(Result(state.dm, NoFreshReturnValue))

        val dmCallee = declaredMethods(callee.value)

        if (dmCallee != state.dm) {
            val rvf = propertyStore(dmCallee, ReturnValueFreshness.key)
            handleReturnValueFreshness(rvf).foreach(x ⇒ return Some(Result(state.dm, x)))
        }
        None
    }

    // Todo later remove me
    def handleEscape(
        defSite: ValueOrigin, uses: IntTrieSet, code: Array[Stmt[V]]
    )(implicit state: ReturnValueFreshnessState): Option[Result] = {
        val analysis = new FallBackEscapeAnalysis(project)
        val ctx = analysis.createContext(
            entityParam = null,
            defSiteParam = defSite,
            targetMethodParam = null,
            usesParam = uses,
            codeParam = code,
            cfgParam = null
        )
        val escapeState = new AbstractEscapeAnalysisState {}

        analysis.doDetermineEscape(ctx, escapeState) match {
            case Result(_, EscapeViaReturn) ⇒ None
            case _                          ⇒ Some(Result(state.dm, NoFreshReturnValue))
        }
    }

    def handleEscapeProperty(ep: EOptionP[AllocationSite, EscapeProperty])(implicit state: ReturnValueFreshnessState): Option[ReturnValueFreshness] = ep match {
        case EP(_, NoEscape | EscapeInCallee) ⇒
            throw new RuntimeException("unexpected result")

        case EP(_, EscapeViaReturn) ⇒
            None

        //TODO what if escapes via exceptions?
        case EP(_, p) if p.isFinal         ⇒ Some(NoFreshReturnValue)

        // it could happen anything
        case EP(_, AtMost(_))              ⇒ Some(NoFreshReturnValue)
        case EP(_, Conditional(AtMost(_))) ⇒ Some(NoFreshReturnValue)

        case EP(_, Conditional(EscapeViaReturn)) ⇒
            state.addAllocationDependee(ep)
            None

        case EP(_, Conditional(NoEscape) | Conditional(EscapeInCallee)) ⇒
            throw new RuntimeException("unexpected result")

        // p is worse than via return
        case EP(_, Conditional(_)) ⇒ Some(NoFreshReturnValue)

        case _ ⇒
            state.addAllocationDependee(ep)
            None
    }

    def handleFieldLocalityProperty(ep: EOptionP[Field, FieldLocality])(implicit state: ReturnValueFreshnessState): Option[ReturnValueFreshness] = ep match {
        case EP(_, LocalFieldWithGetter) ⇒
            state.updateWithMeet(Getter)
            None

        case EP(_, ConditionalLocalFieldWithGetter) ⇒
            state.updateWithMeet(Getter)
            state.addFieldDependee(ep)
            None

        case EP(_, NoLocalField) ⇒
            Some(NoFreshReturnValue)

        case EP(_, ExtensibleLocalFieldWithGetter) ⇒
            state.updateWithMeet(ExtensibleGetter)
            None

        case EP(_, ConditionalExtensibleLocalFieldWithGetter) ⇒
            state.updateWithMeet(ExtensibleGetter)
            state.addFieldDependee(ep)
            None

        case EP(_, LocalField | ConditionalLocalField) ⇒
            throw new RuntimeException("unexpected result")

        case EP(_, ExtensibleLocalField | ConditionalExtensibleLocalField) ⇒
            throw new RuntimeException("unexpected result")

        case _ ⇒
            state.addFieldDependee(ep)
            None
    }

    def handleReturnValueFreshness(
        ep: EOptionP[DeclaredMethod, Property]
    )(implicit state: ReturnValueFreshnessState): Option[ReturnValueFreshness] = ep match {
        case EP(_, NoFreshReturnValue | VNoFreshReturnValue) ⇒
            Some(NoFreshReturnValue)

        case EP(_, FreshReturnValue | VFreshReturnValue) ⇒
            None

        //IMPROVE
        case EP(_, Getter | ConditionalGetter | VGetter | VConditionalGetter) ⇒
            Some(NoFreshReturnValue)

        case EP(_, ExtensibleGetter | ConditionalExtensibleGetter) ⇒
            Some(NoFreshReturnValue)

        case EP(_, VExtensibleGetter | VConditionalExtensibleGetter) ⇒
            Some(NoFreshReturnValue)

        case EP(_, PrimitiveReturnValue | VPrimitiveReturnValue) ⇒
            throw new RuntimeException("unexpected property")

        case _ ⇒
            state.addMethodDependee(ep)
            None
    }

    /**
     * A continuation function, that handles updates for the escape state.
     */
    def c(
        e: Entity, p: Property, ut: UpdateType
    )(implicit state: ReturnValueFreshnessState): PropertyComputationResult = {
        val dm = state.dm

        if (p eq PropertyIsLazilyComputed)
            return IntermediateResult(dm, state.temporaryState.asConditional, state.dependees, c);

        e match {
            case e: AllocationSite ⇒
                val newEP = EP(e, p.asInstanceOf[EscapeProperty])
                state.removeAllocationDependee(newEP)
                handleEscapeProperty(newEP).foreach(x ⇒ return Result(dm, x))

            case e: DeclaredMethod ⇒
                val newEP = EP(e, p)
                state.removeMethodDependee(newEP)
                handleReturnValueFreshness(newEP).foreach(x ⇒ return Result(dm, x))

            case e: Field ⇒
                val newEP = EP(e, p.asInstanceOf[FieldLocality])
                state.removeFieldDependee(newEP)
                handleFieldLocalityProperty(newEP).foreach(x ⇒ return Result(dm, x))
        }

        returnResult
    }

    def returnResult(implicit state: ReturnValueFreshnessState): PropertyComputationResult = {
        if (state.hasDependees)
            IntermediateResult(state.dm, state.temporaryState.asConditional, state.dependees, c)
        else
            Result(state.dm, state.temporaryState)
    }
}

object ReturnValueFreshnessAnalysis extends FPCFAnalysisScheduler {

    override def derivedProperties: Set[PropertyKind] = Set(ReturnValueFreshness)

    override def usedProperties: Set[PropertyKind] =
        Set(EscapeProperty, VirtualMethodReturnValueFreshness, FieldLocality)

    def start(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        val declaredMethods = propertyStore.context[DeclaredMethods].declaredMethods
        val analysis = new ReturnValueFreshnessAnalysis(project)
        propertyStore.scheduleForEntities(declaredMethods)(analysis.determineFreshness)
        analysis
    }

    /**
     * Registers the analysis as a lazy computation, that is, the method
     * will call `ProperytStore.scheduleLazyComputation`.
     */
    def startLazily(project: SomeProject, propertyStore: PropertyStore): FPCFAnalysis = {
        val analysis = new ReturnValueFreshnessAnalysis(project)
        propertyStore.scheduleLazyPropertyComputation(
            ReturnValueFreshness.key, analysis.determineFreshness
        )
        analysis
    }
}
