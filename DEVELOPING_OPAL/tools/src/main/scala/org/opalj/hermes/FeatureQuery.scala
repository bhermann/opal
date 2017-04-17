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
package hermes

import java.net.URL

import scala.io.Source
import scala.io.Codec

import com.github.rjeschke.txtmark.Processor

import scalafx.beans.property.ObjectProperty
import scalafx.beans.property.LongProperty

import org.opalj.io.processSource
import org.opalj.br.analyses.Project

/**
 * Extracts a feature/a set of closely related features of a given project.
 *
 * @author Michael Eichberg
 */
trait FeatureQuery {

    /**
     * Queries should regularly check if they are interrupted using this method.
     */
    final def isInterrupted(): Boolean = Thread.currentThread().isInterrupted()

    // ================================ ABSTRACT FUNCTIONALITY ================================

    /**
     * The unique ids of the extracted features.
     */
    def featureIDs: Seq[String]

    /**
     * The function which analyzes the project and extracts the feature information.
     *
     * @param  project A representation of the project. To speed up queries, intermediate
     *         information that may also be required by other queries can/should be stored in the
     *         project using the [[org.opalj.fpcf.PropertyStore]] or using a
     *         [[org.opalj.br.analyses.ProjectInformationKey]].
     * @param  rawClassFiles A direct 1:1 representation of the class files. This makes it possible
     *         to write queries that need to get an understanding of an unprocessed class file; e.g.
     *         that need to analyze the constant pool in detail.
     * @note   '''Every query should regularly check that its thread is not interrupted
     *         using `isInterrupted`'''.
     */
    def apply[S](
        projectConfiguration: ProjectConfiguration,
        project:              Project[S],
        rawClassFiles:        Traversable[(da.ClassFile, S)]
    ): TraversableOnce[Feature[S]]

    // =================================== DEFAULT FUNCTIONALITY ===================================
    // ==================================== (can be overridden) ====================================

    /**
     * A short descriptive name; by default the simple name of `this` class.
     */
    val id: String = {
        val simpleClassName = this.getClass.getSimpleName
        val dollarPosition = simpleClassName.indexOf('$')
        if (dollarPosition == -1)
            simpleClassName
        else
            simpleClassName.substring(0, dollarPosition)
    }

    /**
     * Returns an explanation of the feature (group) using Markdown as its formatting language.
     *
     * By default the name of the class is used to lookup the resource "className.markdown"
     * which is expected to be found along the extractor.
     */
    protected def mdDescription: String = {
        val descriptionResource = s"$id.markdown"
        val descriptionResourceURL = this.getClass.getResource(descriptionResource)
        try {
            processSource(Source.fromURL(descriptionResourceURL)(Codec.UTF8)) { _.mkString }
        } catch {
            case t: Throwable ⇒ s"Not Available ($descriptionResourceURL; ${t.getMessage})"
        }
    }

    /**
     * Returns an HTML description of this feature query that is targeted at end users; by default
     * it calls `mdDescription` to try to find a markdown document that describes this feature and
     * then uses TxtMark to convert the document. If a document is returned the web engine's
     * user style sheet is set to [[org.opalj.hermes.FeatureQueries.MDCSS]]; in case of an URL no
     * stylesheet is set.
     *
     * @return An HTML document/a link to an HTML document that describes this query.
     */
    val htmlDescription: Either[String, URL] = Left(Processor.process(mdDescription))

    // =============================== HERMES INTERNAL FUNCTIONALITY ===============================

    /**
     * The time it took to evaluate the query across all projects in nanoseconds.
     */
    private[hermes] val accumulatedAnalysisTime = new LongProperty()

    private[hermes] def createInitialFeatures[S]: Seq[ObjectProperty[Feature[S]]] = {
        featureIDs.map(fid ⇒ ObjectProperty(Feature[S](fid)))
    }

}

abstract class DefaultFeatureQuery extends FeatureQuery {

    def evaluate[S](
        projectConfiguration: ProjectConfiguration,
        project:              Project[S],
        rawClassFiles:        Traversable[(da.ClassFile, S)]
    ): IndexedSeq[LocationsContainer[S]]

    final def apply[S](
        projectConfiguration: ProjectConfiguration,
        project:              Project[S],
        rawClassFiles:        Traversable[(da.ClassFile, S)]
    ): TraversableOnce[Feature[S]] = {
        val locations = evaluate(projectConfiguration, project, rawClassFiles)
        for { (featureID, featureIDIndex) ← featureIDs.iterator.zipWithIndex } yield {
            Feature[S](featureID, locations(featureIDIndex))
        }
    }

}

/**
 * Common constants related to feature queries.
 *
 * @author Michael Eichberg
 */
object FeatureQueries {

    /**
     * The URL of the CSS file which used to style the HTML document.
     */
    final val MDCSS: URL = getClass().getResource("Queries.css")

}
