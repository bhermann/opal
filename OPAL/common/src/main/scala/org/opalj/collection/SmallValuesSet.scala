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
package collection

/**
 * A memory-efficient, sorted set of small values that is highly tailored for small(er) sets of
 * small values.
 *
 * @author Michael Eichberg
 */
trait SmallValuesSet /* by purpose, we do not inherit from Traversable[Int] */ {

    type MutableSmallValuesSet = mutable.SmallValuesSet

    /**
     * Returns a set which can be mutated without affecting this set. The returned
     * set will be this set, if this set is not mutable.
     */
    def mutableCopy: MutableSmallValuesSet

    /**
     * Creates a new set that contains this set's values as well as the given one's values.
     */
    def ++(values: SmallValuesSet): MutableSmallValuesSet = {
        var newSet = this.mutableCopy
        values foreach { v ⇒ newSet = v +≈: newSet }
        newSet
    }

    /**
     * Returns a new set which does not contain the given value unless the set does
     * not contain the given value, then this set is returned.
     */
    def -(value: Int): SmallValuesSet

    /**
     * Returns `true` if this set contains the given value.
     *
     * If `value` is not in the range specified at creation time the result is
     * undefined.
     */
    def contains(value: Int): Boolean

    /**
     * Tests if a value exists for which the given function `f` returns `true`.
     */
    def exists(f: Int ⇒ Boolean): Boolean

    /**
     * Tests if this set is a subset of the given set.
     */
    def subsetOf(other: SmallValuesSet): Boolean

    /**
     * Executes the given function `f` for each value of this set, starting with
     * the smallest value.
     */
    def foreach[U](f: Int ⇒ U): Unit

    def foldLeft[B](z: B)(f: (B, Int) ⇒ B): B

    /**
     * Returns `true` if `f` is true for all values of this set.
     */
    def forall(f: Int ⇒ Boolean): Boolean

    /**
     * The number of elements of this set (the complexity is O(n)).
     *
     * @note The size is calculated on demand and requires a traversal of this
     *      data structure.
     */
    def size: Int

    /**
     * Tests if this collection has exactly one element. Guaranteed complexity: O(1).
     */
    def isSingletonSet: Boolean

    /**
     * @return `true` if this set is empty.
     */
    def isEmpty: Boolean

    def nonEmpty: Boolean = !isEmpty

    /**
     * Returns the maximum value stored in this set.
     */
    def max: Int

    /**
     * Returns the minimum value stored in this set.
     */
    def min: Int

    def last: Int = max

    def head: Int = min

    /**
     * Creates a string representation of the values of this set.
     *
     * @param start The start of the generated string.
     * @param sep The string that is used to separate two values.
     * @param end The end of the generated string.
     * @param offset A value that is added to all values when the string is created.
     */
    protected[collection] def mkString(
        start:  String,
        sep:    String,
        end:    String,
        offset: Int
    ): String

    def mkString(start: String, sep: String, end: String): String

    /**
     * Two SmallValuesSets are equal if they contain the same values.
     */
    override def equals(other: Any): Boolean = {
        other match {
            case that: SmallValuesSet ⇒ this.subsetOf(that) && that.subsetOf(this)
            case _                    ⇒ false
        }
    }

    /**
     * Calculates the `hashCode` based on the values in the set. This is a O(n)
     * operation.
     */
    override def hashCode(): Int = {
        var hashCode = -1
        foreach { v ⇒ hashCode = hashCode * 17 + v }
        hashCode
    }

}

