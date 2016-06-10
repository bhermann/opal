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
package da

import scala.xml.Node
import org.opalj.bi.AccessFlags
import org.opalj.bi.AccessFlagsContexts
import scala.xml.NodeSeq

/**
 * @author Michael Eichberg
 * @author Wael Alkhatib
 * @author Isbel Isbel
 * @author Noorulla Sharief
 * @author Andre Pacak
 */
case class Field_Info(
        access_flags:     Int,
        name_index:       Constant_Pool_Index,
        descriptor_index: Constant_Pool_Index,
        attributes:       Attributes
) {

    def size: Int = 2 + 2 + 2 + 2 /* attributes_count*/ + attributes.view.map(_.size).sum

    /**
     * @param definingTypeFQN The FQN of the class defining this field.
     */
    def toXHTML(definingTypeFQN: String)(implicit cp: Constant_Pool): Node = {
        val TypeInfo(fieldType, isBaseType) = parseFieldType(cp(descriptor_index).asString)
        val typeInfo =
            if (isBaseType)
                fieldType
            else
                abbreviateFQN(definingTypeFQN, fieldType)

        // create row which describes the field
        <tr class="field">
            <td class="access_flags">{ AccessFlags.toString(access_flags, AccessFlagsContexts.FIELD) }</td>
            <td>{ typeInfo }</td>
            <td class="field_name"> { cp(name_index).asString } </td>
            <td>{ attributesToXHTML(cp) }</td>
        </tr>
    }

    def attributesToXHTML(implicit cp: Constant_Pool): Seq[Node] = {
        if (attributes.nonEmpty)
            attributes.map(_.toXHTML(cp))
        else
            NodeSeq.Empty
    }
}
