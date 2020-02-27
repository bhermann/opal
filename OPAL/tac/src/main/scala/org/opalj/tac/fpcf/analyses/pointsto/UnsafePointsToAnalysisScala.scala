/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.br.analyses.SomeProject
import org.opalj.br.fpcf.properties.pointsto.AllocationSitePointsToSetScala

/**
 * TODO
 *
 * @author Dominik Helm
 */
object AllocationSiteBasedUnsafePointsToScalaAnalysisScheduler
    extends UnsafePointsToAnalysisScheduler {

    override val propertyKind: PropertyMetaInformation = AllocationSitePointsToSetScala
    override val createAnalysis: SomeProject ⇒ UnsafePointsToAnalysis =
        new UnsafePointsToAnalysis(_) with AllocationSiteBasedScalaAnalysis
}