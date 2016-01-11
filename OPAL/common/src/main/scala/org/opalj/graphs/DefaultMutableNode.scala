/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package graphs

import scala.collection.Map
import org.opalj.collection.UID

/**
 * Default implementation of a mutable node of a graph.
 *
 * ==Thread Safety==
 * This is class is '''thread-safe'''.
 *
 * @author Michael Eichberg
 */
class DefaultMutableNode[I](
    theIdentifier:       I,
    identifierToString:  I ⇒ String,
    theVisualProperties: Map[String, String],
    theChildren:         List[DefaultMutableNode[I]]
) extends MutableNodeLike[I, DefaultMutableNode[I]](theIdentifier, identifierToString, theVisualProperties, theChildren)
        with MutableNode[I, DefaultMutableNode[I]] {

    def this(identifier: I) {
        this(identifier, id ⇒ id.toString, Map("shape" → "box"), List.empty)
    }

    def this(
        identifier:         I,
        identifierToString: I ⇒ String
    ) {
        this(identifier, identifierToString, Map("shape" → "box"), List.empty)
    }

    def this(
        identifier:         I,
        identifierToString: I ⇒ String     = (_: Any).toString,
        fillcolor:          Option[String]
    ) {
        this(
            identifier,
            identifierToString,
            fillcolor.map(c ⇒ DefaultMutableMode.BaseVirtualPropertiers + ("fillcolor" → c)).
                getOrElse(DefaultMutableMode.BaseVirtualPropertiers),
            List.empty
        )
    }

}
object DefaultMutableMode {

    val BaseVirtualPropertiers = Map(
        "shape" → "box",
        "style" → "filled", "fillcolor" → "white"
    )

}
