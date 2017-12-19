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

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

/**
 * Defines common control abstractions.
 *
 * @author Michael Eichberg
 */
package object control {

    /**
     * Iterates over a given array `a` and calls the given function `f` for
     * each non-null value in the array.
     *
     * @note '''This is a macro.'''
     */
    final def foreachNonNullValue[T <: AnyRef](
        a: Array[T]
    )(
        f: (Int, T) ⇒ Unit
    ): Unit = macro ControlAbstractionsImplementation.foreachNonNullValue[T]

    /**
     * Executes the given function `f` for the first `n` values of the given list.
     * The behavior is undefined if the given list does not have at least `n` elements.
     *
     * @note '''This is a macro.'''
     */
    final def forFirstN[T <: AnyRef](
        l: List[T], n: Int
    )(
        f: (T) ⇒ Unit
    ): Unit = macro ControlAbstractionsImplementation.forFirstN[T]

    /**
     * Evaluates the given expression `f` with type `T` the given number of
     * `times` and stores the result in an `IndexedSeq[T]`.
     *
     * ==Example Usage==
     * {{{
     * val result = repeat(15) {
     *      System.in.read()
     * }
     * }}}
     *
     * @note '''This is a macro.'''
     *
     * @param times The number of times the expression `f` is evaluated. The `times`
     *      expression is evaluated exactly once.
     * @param f An expression that is evaluated the given number of times unless an
     *      exception is thrown. Hence, even though `f` is not a by-name parameter,
     *      it behaves in the same way.
     * @return The result of the evaluation of the expression `f` the given number of
     *      times stored in an `IndexedSeq`. If `times` is zero an empty sequence is
     *      returned.
     */
    def repeat[T](
        times: Int
    )(
        f: ⇒ T
    ): IndexedSeq[T] = macro ControlAbstractionsImplementation.repeat[T]
    // OLD IMPLEMENTATION USING HIGHER-ORDER FUNCTIONS
    // (DO NOT DELETE - TO DOCUMENT THE DESIGN DECISION FOR MACROS)
    //        def repeat[T](times: Int)(f: ⇒ T): IndexedSeq[T] = {
    //            val array = new scala.collection.mutable.ArrayBuffer[T](times)
    //            var i = 0
    //            while (i < times) {
    //                array += f
    //                i += 1
    //            }
    //            array
    //        }
    // The macro-based implementation has proven to be approx. 1,3 to 1,4 times faster
    // when the number of times that we repeat an operation is small (e.g., 1 to 15 times)
    // (which is very often the case when we read in Java class files)

    /**
     * Iterates over the given range of integer values `[from,to]` and calls the given
     * function f for each value.
     *
     * If `from` is smaller or equal to `to`, `f` will not be called.
     *
     * @note '''This is a macro.'''
     */
    def iterateTo(
        from: Int, to: Int
    )(
        f: Int ⇒ Unit
    ): Unit = macro ControlAbstractionsImplementation.iterateTo

    /**
     * Iterates over the given range of integer values `[from,until)` and calls the given
     * function f for each value.
     *
     * If `from` is smaller than `until`, `f` will not be called.
     */
    def iterateUntil(
        from: Int, until: Int
    )(
        f: Int ⇒ Unit
    ): Unit = macro ControlAbstractionsImplementation.iterateUntil

    /**
     * Runs the given function f the given number of times.
     */
    def rerun(times: Int)(f: Unit): Unit = macro ControlAbstractionsImplementation.rerun

    /**
     * Finds the value identified by the given comparator, if any.
     *
     * @note    The comparator has to be able to handle `null` values if the given array may
     *          contain null values.
     *
     * @note    The array must contain less than Int.MaxValue/2 values.
     *
     * @param   data An array sorted in ascending order according to the test done by the
     *          comparator.
     * @param   comparator A comparator which is used to search for the matching value.
     *          If the comparator matches multiple values, the returned value is not
     *          precisely specified.
     */
    def find[T <: AnyRef](data: Array[T], comparator: Comparator[T]): Option[T] = {
        find(data)(comparator.evaluate)
    }

    def find[T <: AnyRef](data: Array[T])(evaluate: T ⇒ Int): Option[T] = {
        @tailrec @inline def find(low: Int, high: Int): Option[T] = {
            if (high < low)
                return None;

            val mid = (low + high) / 2 // <= will never overflow...(by constraint...)
            val e = data(mid)
            val eComparison = evaluate(e)
            if (eComparison == 0) {
                Some(e)
            } else if (eComparison < 0) {
                find(mid + 1, high)
            } else {
                find(low, mid - 1)
            }
        }

        find(0, data.length - 1)
    }
}

package control {

    /**
     * Implementation of the macros.
     *
     * @author Michael Eichberg
     */
    private object ControlAbstractionsImplementation {

        def foreachNonNullValue[T <: AnyRef: c.WeakTypeTag](
            c: Context
        )(
            a: c.Expr[Array[T]]
        )(
            f: c.Expr[(Int, T) ⇒ Unit]
        ): c.Expr[Unit] = {
            import c.universe._

            reify {
                val array = a.splice // evaluate only once!
                val arrayLength = array.length
                var i = 0
                while (i < arrayLength) {
                    val arrayEntry = array(i)
                    if (arrayEntry ne null) f.splice(i, arrayEntry)
                    i += 1
                }
            }
        }

        def forFirstN[T <: AnyRef: c.WeakTypeTag](
            c: Context
        )(
            l: c.Expr[List[T]], n: c.Expr[Int]
        )(
            f: c.Expr[T ⇒ Unit]
        ): c.Expr[Unit] = {
            import c.universe._

            reify {
                var remainingList = l.splice
                val max = n.splice
                var i = 0
                while (i < max) {
                    val head = remainingList.head
                    remainingList = remainingList.tail
                    f.splice(head)
                    i += 1
                }
            }
        }

        def repeat[T: c.WeakTypeTag](
            c: Context
        )(
            times: c.Expr[Int]
        )(
            f: c.Expr[T]
        ): c.Expr[IndexedSeq[T]] = {
            import c.universe._

            reify {
                val size = times.splice // => times is evaluated only once
                if (size == 0) {
                    IndexedSeq.empty
                } else {
                    val array = new scala.collection.mutable.ArrayBuffer[T](size)
                    var i = 0
                    while (i < size) {
                        val value = f.splice // => we evaluate f the given number of times
                        array += value
                        i += 1
                    }
                    array
                }
            }
        }

        def iterateTo(
            c: Context
        )(
            from: c.Expr[Int],
            to:   c.Expr[Int]
        )(
            f: c.Expr[(Int) ⇒ Unit]
        ): c.Expr[Unit] = {
            import c.universe._

            reify {
                var i = from.splice
                val max = to.splice // => to is evaluated only once
                while (i <= max) {
                    f.splice(i) // => we evaluate f the given number of times
                    i += 1
                }
            }
        }

        def iterateUntil(
            c: Context
        )(
            from:  c.Expr[Int],
            until: c.Expr[Int]
        )(
            f: c.Expr[(Int) ⇒ Unit]
        ): c.Expr[Unit] = {
            import c.universe._

            reify {
                var i = from.splice
                val max = until.splice // => until is evaluated only once
                while (i < max) {
                    f.splice(i) // => we evaluate f the given number of times
                    i += 1
                }
            }
        }

        def rerun(
            c: Context
        )(
            times: c.Expr[Int]
        )(
            f: c.Expr[Unit]
        ): c.Expr[Unit] = {
            import c.universe._

            reify {
                var i = times.splice
                while (i > 0) {
                    f.splice // => we evaluate f the given number of times
                    i -= 1
                }
            }
        }
    }
}
