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
package bi
package reader

import scala.reflect.ClassTag

import java.io.DataInputStream

import org.opalj.control.repeat

/**
 * Implementation of a template method to read in the StackMapTable attribute.
 *
 * @author Michael Eichberg
 */
trait StackMapTable_attributeReader extends AttributeReader {

    //
    // ABSTRACT DEFINITIONS
    //

    type StackMapTable_attribute >: Null <: Attribute

    type StackMapFrame
    implicit val StackMapFrameManifest: ClassTag[StackMapFrame]

    def StackMapFrame(cp: Constant_Pool, in: DataInputStream): StackMapFrame

    //
    // IMPLEMENTATION
    //

    type StackMapFrames = IndexedSeq[StackMapFrame]

    def StackMapTable_attribute(
        constant_pool:        Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        stack_map_frames:     StackMapFrames
    ): StackMapTable_attribute

    /**
     * <pre>
     * StackMapTable_attribute {
     *      u2              attribute_name_index;
     *      u4              attribute_length;
     *      u2              number_of_entries;
     *      stack_map_frame entries[number_of_entries];
     * }
     * </pre>
     */
    private[this] def parserFactory() = (
        ap: AttributeParent,
        cp: Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        in: DataInputStream
    ) ⇒ {
        /*val attribute_length =*/ in.readInt()
        val number_of_entries = in.readUnsignedShort()
        if (number_of_entries > 0 || reifyEmptyAttributes) {
            val frames = repeat(number_of_entries) { StackMapFrame(cp, in) }
            StackMapTable_attribute(cp, attribute_name_index, frames)
        } else {
            null
        }
    }

    registerAttributeReader(StackMapTableAttribute.Name → parserFactory())
}
