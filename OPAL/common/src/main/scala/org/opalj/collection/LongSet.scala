/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.collection

import scala.collection.mutable.Builder

/**
 * A set of long values.
 *
 * @author Michael Eichberg
 */
trait LongSet[T <: LongSet[T]] extends GrowableLongSet[T] { longSet: T ⇒

    /**
     * Tests if this set has more than one element (complexity: O(1)).
     */
    def hasMultipleElements: Boolean
    /**
     * The size of the set; may not be a constant operation; if possible use isEmpty, nonEmpty,
     * etc.; or lookup the complexity in the concrete data structures.
     */
    def size: Int

    def filter(p: Long ⇒ Boolean): T

    def map(f: Long ⇒ Long): T
    def map[S <: IntSet[S]](start: S)(f: Long ⇒ Int): S = foldLeft(start)(_ + f(_))
    def map[A <: AnyRef](f: Long ⇒ A): Set[A] = foldLeft(Set.empty[A])(_ + f(_))
    final def transform[B, To](f: Long ⇒ B, b: Builder[B, To]): To = {
        foreach(i ⇒ b += f(i))
        b.result()
    }

    def flatMap(f: Long ⇒ T): T

    def foldLeft[B](z: B)(f: (B, Long) ⇒ B): B

    def head: Long

    def exists(p: Long ⇒ Boolean): Boolean
    def forall(f: Long ⇒ Boolean): Boolean

    def -(i: Long): T

    final def --(is: LongSet[_]): T = {
        var r = this
        is.foreach { i ⇒ r -= i }
        r
    }

    def ++(that: T): T

    final def ++(that: LongIterator): T = that.foldLeft(this)(_ + _)

    final def mkString(pre: String, in: String, post: String): String = {
        val sb = new StringBuilder(pre)
        val it = iterator
        var hasNext = it.hasNext
        while (hasNext) {
            sb.append(it.next().toString)
            hasNext = it.hasNext
            if (hasNext) sb.append(in)
        }
        sb.append(post)
        sb.toString()
    }

    final def mkString(in: String): String = mkString("", in, "")

}

object LongSet {

    @inline def bitMask(length: Int): Long = {
        (1L << length) - 1
    }

    final val BitMasks: Array[Long] = {
        val bitMasks = new Array[Long](64)
        var i = 1
        while (i < 64) {
            bitMasks(i) = (1L << i) - 1L
            i += 1
        }
        bitMasks
    }

}
