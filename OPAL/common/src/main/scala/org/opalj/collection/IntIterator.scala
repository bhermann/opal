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
package collection

import org.opalj.collection.immutable.Chain

/**
 * Iterator over a collection of ints; guaranteed to avoid (un)boxing.
 *
 * Compared to a standard Java/Scala iterator, users of IntIterator cannot rely
 * on an exception if the iterator has reached its end.
 *
 * @author Michael Eichberg
 */
trait IntIterator { self ⇒

    /**
     * Returns the next value if `hasNext` has returned `true`; if hasNext has returned `false`
     * and `next` is called, the result is undefined. The method may throw an
     * `UnsupportedOperationException` or just return the last value; however, the behavior
     * is undefined and subject to change without notice!
     */
    def next(): Int
    def hasNext: Boolean

    def exists(p: Int ⇒ Boolean): Boolean = {
        while (this.hasNext) { if (p(this.next)) return true; }
        false
    }
    def forall(p: Int ⇒ Boolean): Boolean = {
        while (this.hasNext) { if (!p(this.next)) return false; }
        true
    }
    def contains(i: Int): Boolean = {
        while (this.hasNext) { if (i == this.next) return true; }
        false
    }
    def foldLeft[B](start: B)(f: (B, Int) ⇒ B): B = {
        var c = start
        while (this.hasNext) { c = f(c, next()) }
        c
    }

    def map(f: Int ⇒ Int): IntIterator = {
        new IntIterator {
            def hasNext: Boolean = self.hasNext
            def next(): Int = f(self.next)
        }
    }

    def foreach[U](f: Int ⇒ U): Unit = while (hasNext) f(next)

    def flatMap(f: Int ⇒ IntIterator): IntIterator = {
        new IntIterator {
            private[this] var it: IntIterator = null
            private[this] def nextIt(): Unit = {
                do {
                    it = f(self.next)
                } while (!it.hasNext && self.hasNext)
            }

            def hasNext: Boolean = (it != null && it.hasNext) || { nextIt(); it.hasNext }
            def next(): Int = { if (it == null || !it.hasNext) nextIt(); it.next }
        }
    }

    def withFilter(p: Int ⇒ Boolean): IntIterator = {
        new IntIterator {
            private[this] var hasNextValue: Boolean = true
            private[this] var v: Int = 0
            private[this] def goToNextValue(): Unit = {
                while (self.hasNext) {
                    v = next
                    if (p(v)) return ;
                }
                hasNextValue = false
            }

            goToNextValue()

            def hasNext: Boolean = hasNextValue
            def next(): Int = { val v = this.v; goToNextValue(); v }
        }
    }

    def filter(p: Int ⇒ Boolean): IntIterator = withFilter(p)

    def toArray: Array[Int] = {
        var asLength = 32
        var as = new Array[Int](asLength)

        var i = -1
        while (hasNext) {
            i += 1
            if (i == asLength) {
                val newAS = new Array[Int](Math.min(asLength * 2, asLength + 512))
                Array.copy(as, 0, newAS, 0, asLength)
                as = newAS
                asLength = as.length
            }
            val v = next
            as(i) = v
        }
        as
    }

    def toChain: Chain[Int] = {
        val b = Chain.newBuilder[Int]
        while (hasNext) b += next()
        b.result
    }

    def mkString(pre: String, in: String, post: String): String = {
        val sb = new StringBuilder(pre)
        var hasNext = this.hasNext
        while (hasNext) {
            sb.append(this.next().toString)
            hasNext = this.hasNext
            if (hasNext) sb.append(in)
        }
        sb.append(post)
        sb.toString()
    }

}

object IntIterator {

    final val empty: IntIterator = new IntIterator {
        def hasNext: Boolean = false
        def next(): Nothing = throw new UnsupportedOperationException
        override def toArray: Array[Int] = new Array[Int](0)
    }

    def apply(i: Int): IntIterator = new IntIterator {
        private[this] var returned = false
        def hasNext: Boolean = !returned
        def next(): Int = { returned = true; i }
        override def toArray: Array[Int] = { val as = new Array[Int](1); as(0) = i; as }
    }

    def apply(i1: Int, i2: Int): IntIterator = new IntIterator {
        private[this] var next = 0
        def hasNext: Boolean = next < 2
        def next(): Int = { next += 1; if (next == 1) i1 else i2 }
        override def toArray: Array[Int] = {
            val as = new Array[Int](2)
            as(0) = i1
            as(1) = i2
            as
        }
    }

    def apply(i1: Int, i2: Int, i3: Int): IntIterator = new IntIterator {
        private[this] var next = 0
        def hasNext: Boolean = next < 3
        def next(): Int = { next += 1; if (next == 1) i1 else if (next == 2) i2 else i3 }
        override def toArray: Array[Int] = {
            val as = new Array[Int](3)
            as(0) = i1
            as(1) = i2
            as(2) = i3
            as
        }
    }
}
