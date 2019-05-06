/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FinalP
import org.opalj.fpcf.NoResult
import org.opalj.fpcf.PartialResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyComputationResult
import org.opalj.fpcf.PropertyKind
import org.opalj.fpcf.PropertyStore
import org.opalj.br.fpcf.cg.properties.NoCallers
import org.opalj.br.DeclaredMethod
import org.opalj.br.ObjectType
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.cg.properties.CallersProperty
import org.opalj.br.fpcf.BasicFPCFTriggeredAnalysisScheduler
import org.opalj.br.fpcf.cg.properties.InstantiatedTypes
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.tac.fpcf.analyses.pointsto.AllocationSiteDescription
import org.opalj.tac.fpcf.analyses.pointsto.ConfiguredNativeMethods
import org.opalj.tac.fpcf.analyses.pointsto.PointsToRelation

/**
 * Handles the effect of certain (configured native methods) to the set of instantiated types.
 *
 *
 * @author Dominik Helm
 * @author Florian Kuebler
 */
class ConfiguredNativeMethodsInstantiatedTypesAnalysis private[analyses] (
        final val project: SomeProject
) extends FPCFAnalysis {

    private[this] implicit val declaredMethods: DeclaredMethods = p.get(DeclaredMethodsKey)

    private[this] val nativeMethodData: Map[DeclaredMethod, Option[Array[PointsToRelation]]] = {
        ConfiguredNativeMethods.reader.read(
            p.config, "org.opalj.fpcf.analyses.ConfiguredNativeMethodsAnalysis"
        ).nativeMethods.map { v ⇒ (v.method, v.pointsTo) }.toMap
    }

    def getInstantiatedTypesUB(
        instantiatedTypesEOptP: EOptionP[SomeProject, InstantiatedTypes]
    ): UIDSet[ObjectType] = {
        instantiatedTypesEOptP match {
            case eps: EPS[_, _] ⇒ eps.ub.types
            case _              ⇒ UIDSet.empty
        }
    }

    def analyze(declaredMethod: DeclaredMethod): PropertyComputationResult = {
        (propertyStore(declaredMethod, CallersProperty.key): @unchecked) match {
            case FinalP(NoCallers) ⇒
                // nothing to do, since there is no caller
                return NoResult;

            case eps: EPS[_, _] ⇒
                if (eps.ub eq NoCallers) {
                    // we can not create a dependency here, so the analysis is not allowed to create
                    // such a result
                    throw new IllegalStateException("illegal immediate result for callers")
                }
            // the method is reachable, so we analyze it!
        }

        // we only allow defined methods
        if (!declaredMethod.hasSingleDefinedMethod)
            return NoResult;

        val method = declaredMethod.definedMethod

        if (!method.isNative || !nativeMethodData.contains(declaredMethod))
            return NoResult;

        val dataO = nativeMethodData(declaredMethod)
        if (dataO.isEmpty)
            return NoResult;

        val instantiatedTypes = dataO.get.collect {
            case PointsToRelation(_, as: AllocationSiteDescription) ⇒ ObjectType(as.instantiatedType)
        }

        val instantiatedTypesUB =
            getInstantiatedTypesUB(propertyStore(project, InstantiatedTypes.key))

        val newInstantiatedTypes = UIDSet(
            instantiatedTypes.filterNot(instantiatedTypesUB.contains): _*
        )

        if (newInstantiatedTypes.isEmpty)
            return NoResult;

        PartialResult(
            p,
            InstantiatedTypes.key,
            InstantiatedTypesAnalysis.update(p, newInstantiatedTypes)
        )
    }
}

object TriggeredConfiguredNativeMethodsInstantiatedTypesAnalysis extends BasicFPCFTriggeredAnalysisScheduler {
    override def uses: Set[PropertyBounds] =
        PropertyBounds.ubs(CallersProperty, InstantiatedTypes)

    override def derivesCollaboratively: Set[PropertyBounds] =
        PropertyBounds.ubs(InstantiatedTypes)

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): ConfiguredNativeMethodsInstantiatedTypesAnalysis = {
        val analysis = new ConfiguredNativeMethodsInstantiatedTypesAnalysis(p)

        ps.registerTriggeredComputation(CallersProperty.key, analysis.analyze)

        analysis
    }

    override def triggeredBy: PropertyKind = CallersProperty
}
