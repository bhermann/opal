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
package mutable

import scala.reflect.ClassTag
import java.util.Arrays

/**
 * Conceptually, a map where the keys are positive `Int` values and the values are
 * non-`null`; `null` values are not permitted!
 * The key values always have to be larger than or equal to 0 and are ideally continues
 * (0,1,2,3,...). The values are stored in a plain array to enable true O(1) retrieval.
 * Furthermore, the array is only as large as it has to be to keep the value associated
 * with the largest key.
 *
 * @note This data structure is not thread safe.
 *
 * @author Michael Eichberg
 */
class ArrayMap[T >: Null <: AnyRef: ClassTag] private (private var data: Array[T]) { self ⇒

    /**
     * Clears, but does not resize/shrink the map.
     */
    def clear(): Unit = { Arrays.fill(data.asInstanceOf[Array[Object]], null) }

    /**
     * Returns the value stored for the given key or `null` instead.
     *
     * @note If the key is not valid the result is not defined.
     */
    @throws[IndexOutOfBoundsException]("if the key is negative")
    def apply(key: Int): T = {
        val data = this.data
        if (key < data.length)
            data(key)
        else
            null
    }

    def get(key: Int): Option[T] = {
        val data = this.data
        if (key >= 0 && key < data.length) {
            Option(data(key))
        } else
            None
    }

    def remove(key: Int): Unit = {
        val data = this.data
        if (key >= 0 && key < data.length) {
            data(key) = null
        }
    }

    def getOrElse(key: Int, f: ⇒ T): T = {
        val data = this.data
        if (key >= 0 && key < data.length) {
            val entry = data(key)
            if (entry ne null)
                return entry;
        }

        f
    }

    def getOrElseUpdate(key: Int, f: ⇒ T): T = {
        val data = this.data
        if (key >= 0 && key < data.length) {
            val entry = data(key)
            if (entry ne null)
                return entry;
        }

        // orElseUpdate
        val v: T = f
        update(key, v)
        v
    }

    /**
     * Sets the value for the given key to the given value. If the key cannot be stored in
     * the currently used array, the underlying array is immediately resized to make
     * it possible to store the new value.
     */
    @throws[IndexOutOfBoundsException]("if the key is negative")
    final def update(key: Int, value: T): Unit = {
        assert(value ne null, "ArrayMap only supports non-null values")
        val data = this.data
        val max = data.length
        if (key < max) {
            data(key) = value
        } else {
            val newData = new Array[T](key + 2)
            System.arraycopy(data, 0, newData, 0, max)
            newData(key) = value
            this.data = newData
        }
    }

    def foreachValue(f: T ⇒ Unit): Unit = {
        val data = this.data
        var i = 0
        val max = data.length
        while (i < max) {
            val e = data(i)
            // recall that all values have to be non-null...
            if (e ne null) f(e)
            i += 1
        }
    }

    def foreach(f: (Int, T) ⇒ Unit): Unit = {
        val data = this.data
        var i = 0
        val max = data.length
        while (i < max) {
            val e = data(i)
            // Recall that all values have to be non-null...
            if (e ne null) f(i, e)
            i += 1
        }
    }

    def forall(f: T ⇒ Boolean): Boolean = {
        val data = this.data
        var i = 0
        val max = data.length
        while (i < max) {
            val e = data(i)
            // Recall that all values have to be non-null...
            if ((e ne null) && !f(e))
                return false;
            i += 1
        }
        true
    }

    def values: Iterator[T] = data.iterator.filter(_ ne null)

    def entries: Iterator[(Int, T)] = {

        new Iterator[(Int, T)] {

            private[this] def getNextIndex(startIndex: Int): Int = {
                val data = self.data
                val max = data.length
                var i = startIndex
                while (i + 1 < max) {
                    i = i + 1
                    if (data(i) ne null)
                        return i;
                }
                return max;
            }

            private[this] var i = getNextIndex(-1)

            def hasNext: Boolean = i < data.length

            def next(): (Int, T) = {
                val r = (i, data(i))
                i = getNextIndex(i)
                r
            }
        }
    }

    def map[X](f: (Int, T) ⇒ X): List[X] = {
        val data = this.data
        var rs = List.empty[X]
        var i = 0
        val max = data.length
        while (i < max) {
            val e = data(i)
            if (e != null) rs = f(i, e) :: rs
            i += 1
        }
        rs
    }

    override def equals(other: Any): Boolean = {
        other match {
            case that: ArrayMap[_] ⇒
                val thisData = this.data.asInstanceOf[Array[Object]]
                val thisLength = thisData.length
                val thatData = that.data.asInstanceOf[Array[Object]]
                val thatLength = thatData.length
                if (thisLength == thatLength) {
                    java.util.Arrays.equals(thisData, thatData)
                } else if (thisLength < thatLength) {
                    thatData.startsWith(thisData) &&
                        (thatData.view(thisLength, thatLength).forall { _ eq null })
                } else {
                    thisData.startsWith(thatData) &&
                        (thisData.view(thatLength, thisLength).forall { _ eq null })
                }
            case _ ⇒ false
        }
    }

    override def hashCode: Int = {
        var hc = 1
        foreachValue { e ⇒
            hc = hc * 41 + { if (e ne null) e.hashCode else 0 /* === identityHashCode(null) */ }
        }
        hc
    }

    def mkString(start: String, sep: String, end: String): String = {
        val data = this.data
        var s = start
        var i = 0
        val max = data.length
        while (i < max) {
            val e = data(i)
            if (e ne null) s += s"$i -> $e"
            i += 1
            while (i < max && (data(i) eq null)) i += 1
            if ((e ne null) && i < max) s += sep
        }
        s + end
    }

    override def toString: String = mkString("ArrayMap(", ", ", ")")

}
object ArrayMap {

    /**
     * Creates an empty map which initially can store 2 values.
     */
    def empty[T >: Null <: AnyRef: ClassTag]: ArrayMap[T] = new ArrayMap(new Array[T](2))

    /**
     * Creates an empty map which initially can store up to sizeHint values.
     */
    def apply[T >: Null <: AnyRef: ClassTag](sizeHint: Int): ArrayMap[T] = {
        new ArrayMap(new Array[T](sizeHint))
    }
}
