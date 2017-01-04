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
package av
package checking

import scala.collection.Set

/**
 * An architecture checker validates if the implemented architecture
 * complies with the expected/specified one.
 *
 * @author Marco Torsello
 */
sealed trait ArchitectureChecker {

    def violations(): Set[SpecificationViolation]

}

/**
 * A dependency checker validates if the dependencies between the elements of
 * two ensembles comply with the expected/specified dependencies.
 *
 * @author Michael Eichberg
 * @author Marco Torsello
 */
trait DependencyChecker extends ArchitectureChecker {

    def targetEnsembles: Seq[Symbol]

    def sourceEnsembles: Seq[Symbol]
}

/**
 * A property checker validates if the elements of an ensemble
 * have the expected/specified properties.
 *
 * @author Marco Torsello
 */
trait PropertyChecker extends ArchitectureChecker {

    /**
     * A textual representation of the property.
     */
    def property: String

    def ensembles: Seq[Symbol]
}
