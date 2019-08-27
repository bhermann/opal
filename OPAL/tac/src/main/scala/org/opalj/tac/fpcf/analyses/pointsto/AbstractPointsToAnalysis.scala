/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import scala.collection.mutable.ArrayBuffer

import org.opalj.log.OPALLogger.logOnce
import org.opalj.log.Warn
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPK
import org.opalj.fpcf.EPS
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyKind
import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEOptionP
import org.opalj.fpcf.SomeEPK
import org.opalj.fpcf.SomeEPS
import org.opalj.fpcf.UBP
import org.opalj.value.IsMultipleReferenceValue
import org.opalj.value.IsReferenceValue
import org.opalj.value.IsSArrayValue
import org.opalj.value.ValueInformation
import org.opalj.br.DefinedMethod
import org.opalj.br.fpcf.properties.cg.Callees
import org.opalj.br.DeclaredMethod
import org.opalj.br.Method
import org.opalj.br.fpcf.properties.cg.NoCallees
import org.opalj.br.fpcf.properties.pointsto.PointsToSetLike
import org.opalj.br.ReferenceType
import org.opalj.br.FieldType
import org.opalj.br.ObjectType
import org.opalj.br.ArrayType
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.ProjectInformationKeys
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.VirtualFormalParametersKey
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.properties.cg.Callers
import org.opalj.br.fpcf.FPCFTriggeredAnalysisScheduler
import org.opalj.tac.common.DefinitionSite
import org.opalj.tac.common.DefinitionSitesKey
import org.opalj.tac.fpcf.analyses.cg.ReachableMethodAnalysis
import org.opalj.tac.fpcf.analyses.cg.valueOriginsOfPCs
import org.opalj.tac.fpcf.analyses.cg.V
import org.opalj.tac.fpcf.properties.TACAI

/**
 *
 * @author Florian Kuebler
 */
trait AbstractPointsToAnalysis extends PointsToAnalysisBase with ReachableMethodAnalysis {

    override def processMethod(
        definedMethod: DefinedMethod, tacEP: EPS[Method, TACAI]
    ): ProperPropertyComputationResult = {
        doProcessMethod(new PointsToAnalysisState[ElementType, PointsToSet](definedMethod, tacEP))
    }

    private[this] def doProcessMethod(
        implicit
        state: State
    ): ProperPropertyComputationResult = {
        if (state.hasTACDependee)
            throw new IllegalStateException("points to analysis does not support refinement based tac")
        val tac = state.tac
        val method = state.method.definedMethod
        if (method.returnType.isReferenceType) {
            state.includeSharedPointsToSet(
                state.method,
                emptyPointsToSet,
                t ⇒ classHierarchy.isSubtypeOf(t, method.returnType.asReferenceType)
            )
        }

        tac.cfg.abnormalReturnNode.predecessors.foreach { throwingBB ⇒
            val throwingStmt = tac.stmts(throwingBB.asBasicBlock.endPC)
            throwingStmt match {
                case Throw(_, UVar(_, defSites)) ⇒
                    val entity = MethodExceptions(state.method)
                    val filter = { t: ReferenceType ⇒
                        classHierarchy.isSubtypeOf(t, ObjectType.Throwable)
                    }
                    state.includeSharedPointsToSets(
                        entity, currentPointsToOfDefSites(entity, defSites, filter), filter
                    )

                case Assignment(_, _, _: Call[_]) | ExprStmt(_, _: Call[_]) | _: Call[_] ⇒
                    val entity = MethodExceptions(state.method)
                    val callSite = definitionSites(state.method.definedMethod, throwingStmt.pc)
                    val filter = { t: ReferenceType ⇒
                        classHierarchy.isSubtypeOf(t, ObjectType.Throwable)
                    }
                    state.includeSharedPointsToSet(
                        entity, currentPointsTo(entity, CallExceptions(callSite), filter), filter
                    )

                case _ ⇒
                // TODO handle implicit exceptions (do that for Throw and Calls, too (NPE!))
            }
        }

        for (stmt ← tac.stmts) stmt match {
            case Assignment(pc, _, New(_, tpe)) ⇒
                handleAllocation(pc, tpe)

            case Assignment(pc, _, NewArray(_, counts, tpe)) ⇒
                handleArrayAllocation(pc, counts, tpe)

            case Assignment(pc, targetVar, const: Const) if targetVar.value.isReferenceValue ⇒
                val defSite = definitionSites(method, pc)
                state.setAllocationSitePointsToSet(
                    defSite,
                    if (const.isNullExpr)
                        emptyPointsToSet
                    // note, this is wrong for alias analyses
                    else
                        createPointsToSet(
                            pc, state.method, const.tpe.asObjectType, isConstant = true
                        )
                )

            // that case should not happen
            case Assignment(pc, DVar(_: IsReferenceValue, _), UVar(_, defSites)) ⇒
                val defSiteObject = definitionSites(method, pc)
                val index = tac.pcToIndex(pc)
                val nextStmt = tac.stmts(index + 1)
                val filter = nextStmt match {
                    case Checkcast(_, value, cmpTpe) if value.asVar.definedBy.contains(index) ⇒ t: ReferenceType ⇒ classHierarchy.isSubtypeOf(t, cmpTpe)
                    case _ ⇒ PointsToSetLike.noFilter
                }
                state.includeSharedPointsToSets(
                    defSiteObject,
                    currentPointsToOfDefSites(defSiteObject, defSites, filter),
                    filter
                )

            case Assignment(pc, _, GetField(_, declaringClass, name, fieldType: ReferenceType, UVar(_, objRefDefSites))) ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    handleGetField(RealField(fieldOpt.get), pc, objRefDefSites)
                } else {
                    state.addIncompletePointsToInfo(pc)
                }

            case Assignment(pc, _, GetStatic(_, declaringClass, name, fieldType: ReferenceType)) ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    handleGetStatic(fieldOpt.get, pc)
                } else {
                    state.addIncompletePointsToInfo(pc)
                }

            case Assignment(pc, DVar(_: IsReferenceValue, _), ArrayLoad(_, _, UVar(av: IsSArrayValue, arrayDefSites))) ⇒
                val arrayType = av.theUpperTypeBound
                handleArrayLoad(arrayType, pc, arrayDefSites)

            case Assignment(pc, DVar(_: IsReferenceValue, _), ArrayLoad(_, _, UVar(av: IsMultipleReferenceValue, arrayDefSites))) ⇒
                val arrayType = av.leastUpperType.get.asArrayType
                handleArrayLoad(arrayType, pc, arrayDefSites)

            case Assignment(pc, targetVar, call: FunctionCall[DUVar[ValueInformation]]) ⇒
                val callees: Callees = state.callees(ps)
                val targets = callees.callees(pc)
                val defSiteObject = definitionSites(method, pc)

                if (call.descriptor.returnType.isReferenceType) {
                    val index = tac.pcToIndex(pc)
                    val nextStmt = tac.stmts(index + 1)
                    val filter = nextStmt match {
                        case Checkcast(_, value, cmpTpe) if value.asVar.definedBy.contains(index) ⇒
                            t: ReferenceType ⇒ classHierarchy.isSubtypeOf(t, cmpTpe)
                        case _ ⇒
                            PointsToSetLike.noFilter
                    }
                    if (state.hasCalleesDepenedee) {
                        state.includeSharedPointsToSet(defSiteObject, emptyPointsToSet, filter)
                        state.addDependee(defSiteObject, state.calleesDependee, filter)
                    }
                    state.includeSharedPointsToSets(
                        defSiteObject,
                        targets.collect {
                            case target if target.descriptor.returnType.isReferenceType ⇒
                                currentPointsTo(defSiteObject, target, filter)
                        },
                        filter
                    )
                }
                handleCall(call, pc)

            case Assignment(pc, _, idc: InvokedynamicFunctionCall[_]) ⇒
                state.addIncompletePointsToInfo(pc)
                logOnce(
                    Warn("analysis - points-to analysis", s"unresolved invokedynamic: $idc")
                )

            case ExprStmt(pc, idc: InvokedynamicFunctionCall[V]) ⇒
                state.addIncompletePointsToInfo(pc)
                logOnce(
                    Warn("analysis - points-to analysis", s"unresolved invokedynamic: $idc")
                )

            case idc: InvokedynamicMethodCall[_] ⇒
                state.addIncompletePointsToInfo(idc.pc)
                logOnce(
                    Warn("analysis - points-to analysis", s"unresolved invokedynamic: $idc")
                )

            case Assignment(_, DVar(_: IsReferenceValue, _), _) ⇒
                throw new IllegalArgumentException(s"unexpected assignment: $stmt")

            case call: Call[_] ⇒
                handleCall(call.asInstanceOf[Call[DUVar[ValueInformation]]], call.pc)

            case ExprStmt(pc, call: Call[_]) ⇒
                handleCall(call.asInstanceOf[Call[DUVar[ValueInformation]]], pc)

            case PutField(pc, declaringClass, name, fieldType: ReferenceType, UVar(_, objRefDefSites), UVar(_, defSites)) ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    handlePutField(RealField(fieldOpt.get), objRefDefSites, defSites)
                } else {
                    state.addIncompletePointsToInfo(pc)
                }

            case PutStatic(pc, declaringClass, name, fieldType: ReferenceType, UVar(_, defSites)) ⇒
                val fieldOpt = p.resolveFieldReference(declaringClass, name, fieldType)
                if (fieldOpt.isDefined) {
                    handlePutStatic(fieldOpt.get, defSites)
                } else {
                    state.addIncompletePointsToInfo(pc)
                }

            case ArrayStore(_, UVar(av: IsSArrayValue, arrayDefSites), _, UVar(_: IsReferenceValue, defSites)) ⇒
                val arrayType = av.theUpperTypeBound
                handleArrayStore(arrayType, arrayDefSites, defSites)

            case ArrayStore(_, UVar(av: IsMultipleReferenceValue, arrayDefSites), _, UVar(_: IsReferenceValue, defSites)) ⇒
                val arrayType = av.leastUpperType.get.asArrayType
                handleArrayStore(arrayType, arrayDefSites, defSites)

            case ReturnValue(_, UVar(_: IsReferenceValue, defSites)) ⇒
                val filter = { t: ReferenceType ⇒
                    classHierarchy.isSubtypeOf(
                        t, state.method.descriptor.returnType.asReferenceType
                    )
                }
                state.includeSharedPointsToSets(
                    state.method, currentPointsToOfDefSites(state.method, defSites, filter), filter
                )

            case _ ⇒
        }

        // todo: we have to handle the exceptions that might implicitly be thrown by this method

        Results(createResults(state))
    }

    @inline private[this] def handleAllocation(
        pc: Int, tpe: ReferenceType
    )(implicit state: State): Unit = {
        val defSite = definitionSites(state.method.definedMethod, pc)
        if (!state.hasAllocationSitePointsToSet(defSite)) {
            state.setAllocationSitePointsToSet(
                defSite,
                createPointsToSet(pc, state.method, tpe, isConstant = false)
            )
        }
    }

    @inline private[this] def handleArrayAllocation(
        pc: Int, counts: Seq[Expr[V]], tpe: ArrayType
    )(implicit state: State): Unit = {
        val defSite = definitionSites(state.method.definedMethod, pc)
        if (!state.hasAllocationSitePointsToSet(defSite)) {
            @inline def countIsZero(theCounts: Seq[Expr[V]]): Boolean = {
                theCounts.head.asVar.definedBy.forall { ds ⇒
                    ds >= 0 &&
                        state.tac.stmts(ds).asAssignment.expr.isIntConst &&
                        state.tac.stmts(ds).asAssignment.expr.asIntConst.value == 0
                }
            }

            val isEmptyArray = countIsZero(counts)
            var arrayReferencePTS = createPointsToSet(
                pc, state.method, tpe, isConstant = false, isEmptyArray
            )
            state.setAllocationSitePointsToSet(
                defSite,
                arrayReferencePTS
            )
            var remainingCounts = counts.tail
            var allocatedType: FieldType = tpe.componentType
            var continue = !isEmptyArray
            while (remainingCounts.nonEmpty && allocatedType.isArrayType && continue) {
                val theType = allocatedType.asArrayType
                val arrayEntity = ArrayEntity(arrayReferencePTS.getNewestElement())

                if (countIsZero(remainingCounts))
                    continue = false

                arrayReferencePTS = createPointsToSet(
                    pc,
                    state.method,
                    theType,
                    isConstant = false,
                    isEmptyArray = !continue
                )
                state.includeSharedPointsToSet(
                    arrayEntity,
                    arrayReferencePTS,
                    { t: ReferenceType ⇒ classHierarchy.isSubtypeOf(t, theType) }
                )

                remainingCounts = remainingCounts.tail
                allocatedType = theType.componentType
            }
        }
    }

    // maps the points-to set of actual parameters (including *this*) the the formal parameters
    private[this] def handleCall(
        call: Call[DUVar[ValueInformation]], pc: Int
    )(implicit state: State): Unit = {
        val tac = state.tac
        val callees: Callees = state.callees(ps)

        for (target ← callees.directCallees(pc)) {
            handleDirectCall(call, pc, target)
        }

        for (target ← callees.indirectCallees(pc)) {
            handleIndirectCall(pc, target, callees, tac)
        }

        val callExceptions = CallExceptions(definitionSites(state.method.definedMethod, pc))
        val filter = { t: ReferenceType ⇒ classHierarchy.isSubtypeOf(t, ObjectType.Throwable) }
        for (target ← callees.callees(pc)) {
            val targetExceptions = MethodExceptions(target)
            state.includeSharedPointsToSet(
                callExceptions,
                currentPointsTo(callExceptions, targetExceptions, filter), filter
            )
        }
        if (state.hasCalleesDepenedee) {
            state.addDependee(callExceptions, state.calleesDependee, filter)
            state.includeSharedPointsToSet(
                callExceptions,
                emptyPointsToSet,
                filter
            )
        }
    }

    private[this] def handleDirectCall(
        call: Call[V], pc: Int, target: DeclaredMethod
    )(implicit state: State): Unit = {
        val receiverOpt: Option[Expr[DUVar[ValueInformation]]] = call.receiverOption
        val fps = formalParameters(target)

        if (fps != null) {
            // handle receiver for non static methods
            if (receiverOpt.isDefined) {
                val isNonVirtualCall = call match {
                    case _: NonVirtualFunctionCall[V] |
                        _: NonVirtualMethodCall[V] |
                        _: StaticFunctionCall[V] |
                        _: StaticMethodCall[V] ⇒ true
                    case _ ⇒ false
                }
                handleCallReceiver(receiverOpt.get.asVar.definedBy, target, isNonVirtualCall)
            }

            val descriptor = target.descriptor
            // in case of signature polymorphic methods, we give up
            if (call.params.size == descriptor.parametersCount) {
                // handle params
                for (i ← 0 until descriptor.parametersCount) {
                    handleCallParameter(call.params(i).asVar.definedBy, i, target)
                }
            } else {
                // it is not needed to mark it as incomplete here
            }
        } else {
            state.addIncompletePointsToInfo(pc)
        }
    }

    // TODO reduce code duplication
    private def handleIndirectCall(
        pc:      Int,
        target:  DeclaredMethod,
        callees: Callees,
        tac:     TACode[TACMethodParameter, DUVar[ValueInformation]]
    )(implicit state: State): Unit = {
        val fps = formalParameters(target)

        val indirectParams = callees.indirectCallParameters(pc, target)
        val descriptor = target.descriptor

        // Prevent spuriously matched targets (e.g. from tamiflex with unknown source line number)
        // from interfering with the points-to analysis
        // TODO That rather is a responsibility of the reflection analysis though
        if (indirectParams.isEmpty || descriptor.parametersCount == indirectParams.size) {
            if (fps != null) {
                // handle receiver for non static methods
                val receiverOpt = callees.indirectCallReceiver(pc, target)
                if (receiverOpt.isDefined) {
                    val receiverDefSites = valueOriginsOfPCs(receiverOpt.get._2, tac.pcToIndex)
                    handleCallReceiver(receiverDefSites, target, isNonVirtualCall = false)
                } else if (target.name == "<init>") {
                    handleCallReceiver(
                        IntTrieSet(tac.pcToIndex(pc)), target, isNonVirtualCall = false
                    )
                } else {
                    // TODO distinguish between static methods and unavailable info
                }

                for (i ← indirectParams.indices) {
                    val paramType = descriptor.parameterType(i)
                    if (paramType.isReferenceType) {
                        val indirectParam = indirectParams(i)
                        if (indirectParam.isDefined) {
                            handleCallParameter(
                                valueOriginsOfPCs(indirectParam.get._2, tac.pcToIndex),
                                i,
                                target
                            )
                        } else {
                            state.addIncompletePointsToInfo(pc)
                        }
                    }
                }
            } else {
                state.addIncompletePointsToInfo(pc)
            }
        }
    }

    override protected[this] def createResults(
        state: State
    ): ArrayBuffer[ProperPropertyComputationResult] = {
        val results = super.createResults(state)

        if (state.hasCalleesDepenedee) {
            val calleesDependee = state.calleesDependee
            results += InterimPartialResult(
                Some(calleesDependee),
                continuationForCallees(
                    calleesDependee,
                    new PointsToAnalysisState[ElementType, PointsToSet](state.method, state.tacDependee)
                )
            )
        }

        results
    }

    override protected[this] def continuationForShared(
        e: Entity, dependees: Map[SomeEPK, (SomeEOptionP, ReferenceType ⇒ Boolean)]
    )(eps: SomeEPS): ProperPropertyComputationResult = {
        // The shared entities are not affected by changes of the tac and use partial results.
        // Thus, we could simply recompute them on updates for the tac
        eps match {
            case UBP(callees: Callees) ⇒
                // this will never happen for method return values or method exceptions
                val (defSite, dependeeIsExceptions) = e match {
                    case ds: DefinitionSite ⇒ (ds, false)
                    case ce: CallExceptions ⇒ (ce.defSite, true)
                }
                // TODO: Only handle new callees
                val tgts = callees.callees(defSite.pc)

                val typeFilter = dependees(eps.toEPK)._2

                var newDependees = updatedDependees(eps, dependees)
                var newPointsToSet = emptyPointsToSet
                tgts.foreach { target ⇒
                    val entity = if (dependeeIsExceptions) MethodExceptions(target) else target
                    // if we already have a dependency to that method, we do not need to process it
                    // otherwise, it might still be the case that we processed it before but it is
                    // final and thus not part of dependees anymore
                    if (dependeeIsExceptions || target.descriptor.returnType.isReferenceType) {
                        if (!dependees.contains(EPK(entity, pointsToPropertyKey))) {
                            val p2s = ps(entity, pointsToPropertyKey)
                            if (p2s.isRefinable) {
                                newDependees += (p2s.toEPK → ((p2s, typeFilter)))
                            }
                            newPointsToSet = newPointsToSet.included(pointsToUB(p2s), typeFilter)
                        }
                    }
                }

                val results = createPartialResults(
                    e,
                    newDependees,
                    newPointsToSet,
                    { old ⇒
                        old.included(newPointsToSet, typeFilter)
                    },
                    true
                )

                Results(results)

            case _ ⇒ super.continuationForShared(e, dependees)(eps)
        }
    }

    def continuationForCallees(
        oldCalleeEOptP: EOptionP[DeclaredMethod, Callees],
        state:          State
    )(eps: SomeEPS): ProperPropertyComputationResult = {
        eps match {
            case UBP(newCallees: Callees) ⇒
                val tac = state.tac
                val oldCallees = if (oldCalleeEOptP.hasUBP) oldCalleeEOptP.ub else NoCallees
                for {
                    (pc, targets) ← newCallees.directCallSites()
                    target ← targets
                } {
                    if (!oldCallees.containsDirectCall(pc, target)) {
                        val call = tac.stmts(tac.pcToIndex(pc)) match {
                            case call: Call[DUVar[ValueInformation]] @unchecked ⇒
                                call
                            case Assignment(_, _, call: Call[DUVar[ValueInformation]] @unchecked) ⇒
                                call
                            case ExprStmt(_, call: Call[DUVar[ValueInformation]] @unchecked) ⇒
                                call
                            case e ⇒ throw new IllegalArgumentException(s"unexpected stmt $e")
                        }
                        handleDirectCall(call, pc, target)(state)
                    }
                }
                for {
                    (pc, targets) ← newCallees.indirectCallSites()
                    target ← targets
                } {
                    if (!oldCallees.containsIndirectCall(pc, target)) {
                        handleIndirectCall(pc, target, newCallees, tac)(state)
                    }
                }

                state.setCalleesDependee(eps.asInstanceOf[EPS[DeclaredMethod, Callees]])

                Results(createResults(state))
            case _ ⇒ throw new IllegalArgumentException(s"unexpected eps $eps")
        }
    }
}

trait AbstractPointsToAnalysisScheduler extends FPCFTriggeredAnalysisScheduler {
    def propertyKind: PropertyMetaInformation
    def createAnalysis: SomeProject ⇒ AbstractPointsToAnalysis

    override type InitializationData = Null

    override def requiredProjectInformation: ProjectInformationKeys =
        Seq(DeclaredMethodsKey, VirtualFormalParametersKey, DefinitionSitesKey)

    override def uses: Set[PropertyBounds] = PropertyBounds.ubs(
        Callers,
        Callees,
        TACAI,
        propertyKind
    )

    override def derivesCollaboratively: Set[PropertyBounds] = Set(PropertyBounds.ub(propertyKind))

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def init(p: SomeProject, ps: PropertyStore): Null = {
        null
    }

    override def beforeSchedule(p: SomeProject, ps: PropertyStore): Unit = {}

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): AbstractPointsToAnalysis = {
        val analysis = createAnalysis(p)
        // register the analysis for initial values for callers (i.e. methods becoming reachable)
        ps.registerTriggeredComputation(Callers.key, analysis.analyze)
        analysis
    }

    override def afterPhaseScheduling(ps: PropertyStore, analysis: FPCFAnalysis): Unit = {}

    override def afterPhaseCompletion(
        p:        SomeProject,
        ps:       PropertyStore,
        analysis: FPCFAnalysis
    ): Unit = {}

    override def triggeredBy: PropertyKind = Callers
}

