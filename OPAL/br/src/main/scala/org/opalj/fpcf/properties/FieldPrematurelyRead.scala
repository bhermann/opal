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
package properties

sealed trait FieldPrematurelyReadPropertyMetaInformation extends PropertyMetaInformation {

    type Self = FieldPrematurelyRead

}

/**
 * Identifies fields that are read before they are initialized. This may e.g. happen through virtual
 * calls in a super class constructor.
 *
 * @author Dominik Helm
 */
sealed trait FieldPrematurelyRead extends Property with FieldPrematurelyReadPropertyMetaInformation {

    final def key = FieldPrematurelyRead.key // All instances have to share the SAME key!

}

object FieldPrematurelyRead extends FieldPrematurelyReadPropertyMetaInformation {

    final val PropertyKeyName = "FieldPrematurelyRead"

    final val key: PropertyKey[FieldPrematurelyRead] = {
        PropertyKey.create(
            PropertyKeyName,
            PrematurelyReadField
        )
    }

}

case object NotPrematurelyReadField extends FieldPrematurelyRead

case object PrematurelyReadField extends FieldPrematurelyRead
