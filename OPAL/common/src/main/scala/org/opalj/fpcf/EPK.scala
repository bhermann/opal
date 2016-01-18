/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
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
package org.opalj.fpcf

/**
 * A simple pair consisting of an [[Entity]] and a [[PropertyKey]].
 *
 * Compared to a standard `Tuple2` the entities are compared using reference comparison
 * and not equality based on `equals` checks.
 *
 * @author Michael Eichberg
 */
final class EPK[P <: Property](val e: Entity, val pk: PropertyKey[P])
        extends EOptionP[P]
        with Product2[Entity, PropertyKey[P]] {

    def hasProperty: Boolean = false
    def p: Nothing = throw new UnsupportedOperationException()

    def _1 = e
    def _2 = pk

    override def equals(other: Any): Boolean = {
        other match {
            case that: EPK[_] ⇒ (that.e eq this.e) && this.pk.id == that.pk.id
            case _            ⇒ false
        }
    }

    override def canEqual(that: Any): Boolean = that.isInstanceOf[EPK[_]]

    override val hashCode: Int = e.hashCode() * 511 + pk.id

    override def toString: String = s"EPK($e,${PropertyKey.name(pk.id)})"
}

/**
 * Factory and extractor for [[EPK]] objects.
 *
 * @author Michael Eichberg
 */
object EPK {

    def apply[P <: Property](e: Entity, pk: PropertyKey[P]): EPK[P] = new EPK(e, pk)

    def unapply[P <: Property](that: EPK[P]): Option[(Entity, PropertyKey[P])] = {
        that match {
            case null ⇒ None
            case epk  ⇒ Some((epk.e, epk.pk))
        }
    }
}
