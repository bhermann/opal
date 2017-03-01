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
package immutable

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder
import scala.collection.mutable.ArrayStack

/**
 * An '''unordered''' trie-set based on the unique ids of the stored [[UID]] objects.
 *
 * ==Implementation==
 * This trie set uses the least significant bit to decide whether the search is continued in the
 * right or left branch.
 *
 * Small sets are represented using a UIDSet0...2.
 */
sealed abstract class UIDSet[T <: UID]
        //extends scala.collection.AbstractSet[T]
        extends scala.collection.immutable.Set[T]
        with scala.collection.SetLike[T, UIDSet[T]] {

    final override def empty: UIDSet[T] = UIDSet0.asInstanceOf[UIDSet[T]]

    override def exists(p: T ⇒ Boolean): Boolean
    override def forall(p: T ⇒ Boolean): Boolean
    override def head: T
    override def last: T
    override def tail: UIDSet[T] = throw new UnknownError()
    override def +(e: T): UIDSet[T]
    override def -(e: T): UIDSet[T]
    override def foldLeft[B](z: B)(op: (B, T) ⇒ B): B

    //
    // METHODS DEFINED BY UIDSet
    //
    def isSingletonSet: Boolean
    def ++(es: UIDSet[T]): UIDSet[T]

    /**
     * Adds the given element to this set by mutating it!
     */
    private[opalj] def +!(e: T): UIDSet[T] = this + e

    // The following methods are basically there to "fix" some of the issues
    // caused by the forced invariance of the type parameter T.
    final def toUIDSet[X >: T <: UID]: UIDSet[X] = this.asInstanceOf[UIDSet[X]]
    final def includes[X >: T <: UID](e: X): Boolean = contains(e.asInstanceOf[T] /*pure fiction*/ )
    final def add[X >: T <: UID](e: X): UIDSet[X] = {
        (this + (e.asInstanceOf[T] /*pure fiction*/ )).asInstanceOf[UIDSet[X] /*pure fiction*/ ]
    }

    /**
     * Performs a qualified comparison of this set with the given set.
     */
    def compare(that: UIDSet[T]): SetRelation = {
        val thisSize = this.size
        val thatSize = that.size

        if (thisSize < thatSize) {
            if (this.forall(that.contains)) StrictSubset else UncomparableSets
        } else if (thisSize == thatSize) {
            if (this == that) EqualSets else UncomparableSets
        } else if (that.forall(this.contains)) {
            StrictSuperset
        } else
            UncomparableSets
    }

}

/**
 * Represents the empty UIDSet.
 */
object UIDSet0 extends UIDSet[UID] {

    override def isEmpty: Boolean = true
    override def nonEmpty: Boolean = false
    override def size: Int = 0

    override def find(p: UID ⇒ Boolean): Option[UID] = None
    override def exists(p: UID ⇒ Boolean): Boolean = false
    override def forall(p: UID ⇒ Boolean): Boolean = true
    override def foreach[U](f: UID ⇒ U): Unit = {}
    override def iterator: Iterator[UID] = Iterator.empty
    override def head: UID = throw new NoSuchElementException
    override def last: UID = throw new NoSuchElementException
    override def headOption: Option[UID] = None
    override def tail: UIDSet[UID] = throw new NoSuchElementException
    override def contains(e: UID): Boolean = false
    override def filter(p: UID ⇒ Boolean): UIDSet[UID] = this
    override def +(e: UID): UIDSet[UID] = new UIDSet1(e)
    override def -(e: UID): UIDSet[UID] = this
    override def foldLeft[B](z: B)(op: (B, UID) ⇒ B): B = z
    override def drop(n: Int): UIDSet[UID] = this

    //
    // METHODS DEFINED BY UIDSet
    //

    def isSingletonSet: Boolean = false
    def ++(es: UIDSet[UID]): UIDSet[UID] = es

    override def compare(that: UIDSet[UID]): SetRelation = {
        if (that.isEmpty) EqualSets else /* this is a */ StrictSubset
    }
}

sealed abstract class NonEmptyUIDSet[T <: UID] extends UIDSet[T] {

    override def isEmpty: Boolean = false
    override def nonEmpty: Boolean = true
    final override def headOption: Option[T] = Some(head)
}

case class UIDSet1[T <: UID](value: T) extends NonEmptyUIDSet[T] {

    override def size: Int = 1
    override def find(p: T ⇒ Boolean): Option[T] = if (p(value)) Some(value) else None
    override def exists(p: T ⇒ Boolean): Boolean = p(value)
    override def forall(p: T ⇒ Boolean): Boolean = p(value)
    override def foreach[U](f: T ⇒ U): Unit = f(value)
    override def contains(value: T): Boolean = value.id == this.value.id
    override def head: T = value
    override def last: T = value
    override def tail: UIDSet[T] = empty
    override def iterator: Iterator[T] = Iterator.single(value)
    override def filter(p: T ⇒ Boolean): UIDSet[T] = if (p(value)) this else empty

    override def +(e: T): UIDSet[T] = if (value.id == e.id) this else new UIDSet2(value, e)
    override def -(e: T): UIDSet[T] = if (value.id == e.id) empty else this
    override def foldLeft[B](z: B)(op: (B, T) ⇒ B): B = op(z, value)
    override def drop(n: Int): UIDSet[T] = if (n == 0) this else empty

    //
    // METHODS DEFINED BY UIDTrieSet
    //
    def isSingletonSet: Boolean = true

    def ++(es: UIDSet[T]): UIDSet[T] = {
        es.size match {
            case 0 ⇒ this
            case 1 ⇒ this + es.head
            case _ ⇒ es + value
        }
    }

    override def compare(that: UIDSet[T]): SetRelation = {
        if (that.isEmpty) {
            StrictSuperset
        } else if (that.isSingletonSet) {
            if (this.value.id == that.head.id) EqualSets else UncomparableSets
        } else if (that.contains(this.value))
            StrictSubset
        else
            UncomparableSets
    }
}

case class UIDSet2[T <: UID](value1: T, value2: T) extends NonEmptyUIDSet[T] {

    override def size: Int = 2
    override def find(p: T ⇒ Boolean): Option[T] = {
        if (p(value1)) Some(value1) else if (p(value2)) Some(value2) else None
    }
    override def exists(p: T ⇒ Boolean): Boolean = p(value1) || p(value2)
    override def forall(p: T ⇒ Boolean): Boolean = p(value1) && p(value2)
    override def foreach[U](f: T ⇒ U): Unit = { f(value1); f(value2) }
    override def iterator: Iterator[T] = Iterator(value1, value2)
    override def head: T = value1
    override def last: T = value2
    override def tail: UIDSet[T] = new UIDSet1(value2)
    override def contains(e: T): Boolean = { val eId = e.id; value1.id == eId || value2.id == eId }
    override def filter(p: T ⇒ Boolean): UIDSet[T] = {
        if (p(value1)) {
            if (p(value2)) this else new UIDSet1(value1)
        } else if (p(value2)) {
            new UIDSet1(value2)
        } else {
            empty
        }
    }
    override def foldLeft[B](z: B)(op: (B, T) ⇒ B): B = op(op(z, value1), value2)
    override def drop(n: Int): UIDSet[T] = {
        if (n == 0) this else if (n == 1) new UIDSet1(value2) else empty
    }

    override def +(e: T): UIDSet[T] = {
        val eId = e.id
        val value1 = this.value1
        if (eId == value1.id)
            return this;
        val value2 = this.value2
        if (eId == value2.id)
            return this;

        // we only use the trie for sets with more than two elements
        new UIDTrieSetInnerNode(1, e, null, null) +! value1 +! value2
    }

    override def -(e: T): UIDSet[T] = {
        val eId = e.id
        if (value1.id == eId)
            new UIDSet1(value2)
        else if (value2.id == eId)
            new UIDSet1(value1)
        else
            this
    }

    //
    // METHODS DEFINED BY UIDSet
    //

    def isSingletonSet: Boolean = false

    def ++(es: UIDSet[T]): UIDSet[T] = {
        es.size match {
            case 0 ⇒ this
            case 1 ⇒ this + es.head
            case _ ⇒ es.foldLeft(this: UIDSet[T])(_ + _)
        }
    }
}
// ------------------------------------------------------------------------------------------------
//
//
// If we have more than two values we always create a trie.
//
//
// ------------------------------------------------------------------------------------------------

sealed abstract class UIDTrieSetNodeLike[T <: UID] extends NonEmptyUIDSet[T] { self ⇒
    protected def value: T
    // the following two methods return are either a UIDTrieSetNode, UIDTrieSetLeaf or null:
    protected def left: UIDTrieSetNodeLike[T]
    protected def right: UIDTrieSetNodeLike[T]

    override def find(p: T ⇒ Boolean): Option[T] = {
        if (p(value))
            return Some(value);

        var result: Option[T] = if (left ne null) left.find(p) else None
        if (result.isEmpty && (right ne null)) result = right.find(p)
        result
    }

    override def exists(p: T ⇒ Boolean): Boolean = {
        p(value) || (left != null && left.exists(p)) || (right != null && right.exists(p))
    }

    override def forall(p: T ⇒ Boolean): Boolean = {
        p(value) && {
            val left = this.left
            left == null || left.forall(p)
        } && {
            val right = this.right
            (right == null || right.forall(p))
        }
    }

    override def foreach[U](f: T ⇒ U): Unit = {
        f(value)
        val left = this.left; if (left ne null) left.foreach(f)
        val right = this.right; if (right ne null) right.foreach(f)
    }

    def iterator: Iterator[T] = {

        new Iterator[T] {

            private val nextNodes = ArrayStack[UIDTrieSetNodeLike[T]](self)

            def hasNext: Boolean = nextNodes.nonEmpty

            def next: T = {
                val currentNode = nextNodes.pop
                val nextRight = currentNode.right
                val nextLeft = currentNode.left
                if (nextRight ne null) nextNodes.push(nextRight)
                if (nextLeft ne null) nextNodes.push(nextLeft)
                currentNode.value
            }
        }
    }

    override def head: T = value

    override def tail: UIDSet[T] = {
        /*current...*/ size match {
            case 1 ⇒ empty
            case 2 ⇒
                val left = this.left
                new UIDSet1(if (left ne null) left.value else right.value)
            case 3 ⇒
                val left = this.left
                val right = this.right
                if (left eq null)
                    new UIDSet2(right.head, right.last)
                else if (right eq null)
                    new UIDSet2(left.head, left.last)
                else
                    new UIDSet2(left.head, right.head)
            case _ ⇒
                dropHead
        }
    }

    def contains(e: T): Boolean = { val eId = e.id; contains(eId, eId) }

    override def foldLeft[B](z: B)(op: (B, T) ⇒ B): B = {
        val left = this.left
        val right = this.right
        var result = op(z, value)
        if (left ne null) result = left.foldLeft(result)(op)
        if (right ne null) result = right.foldLeft(result)(op)
        result
    }

    final def +(e: T): UIDSet[T] = { val eId = e.id; this + (e, eId, eId, 0) }

    final def -(e: T): UIDSet[T] = {
        size match {
            case 1 ⇒ throw new UnknownError
            case 2 ⇒
                val value = this.value
                val eId = e.id
                if (value.id == eId)
                    UIDSet1(if (left ne null) left.head else right.head)
                else {
                    val value1 = value
                    val value2Candidate = if (left ne null) left.head else right.head
                    if (value2Candidate.id == eId)
                        UIDSet1(value)
                    else
                        UIDSet2(value1, value2Candidate)
                }
            case 3 ⇒
                val value = this.value
                val eId = e.id
                if (value.id == eId) {
                    // let's remove this value
                    if (left ne null) {
                        if (right ne null)
                            UIDSet2(left.head, right.head)
                        else
                            UIDSet2(left.head, left.last)
                    } else {
                        UIDSet2(right.head, right.last)
                    }
                } else {
                    // we have to keep this value...
                    var value2Candidate: T = null.asInstanceOf[T]
                    var value3Candidate: T = null.asInstanceOf[T]
                    if (left ne null) {
                        if (right ne null) {
                            value2Candidate = left.head
                            value3Candidate = right.head
                        } else {
                            value2Candidate = left.head
                            value3Candidate = left.last
                        }
                    } else {
                        value2Candidate = right.head
                        value3Candidate = right.last
                    }
                    if (value2Candidate.id == eId)
                        UIDSet2(value, value3Candidate)
                    else if (value3Candidate.id == eId)
                        UIDSet2(value, value2Candidate)
                    else
                        this
                }
            case _ ⇒
                val eId = e.id
                this.-(eId, eId)
        }
    }

    override def filter(p: T ⇒ Boolean): UIDSet[T] = {
        val result = filter0(p)
        if (result == null)
            return empty;

        result.size match {
            case 1 ⇒ new UIDSet1(result.head)
            case 2 ⇒ new UIDSet2(result.head, result.last)
            case _ ⇒ result
        }
    }

    private def filter0(p: T ⇒ Boolean): UIDTrieSetNodeLike[T] = {
        val left = this.left
        val right = this.right
        val newLeft = if (left != null) left.filter0(p) else null
        val newRight = if (right != null) right.filter0(p) else null
        if (p(value)) {
            if ((newLeft ne left) || (newRight ne right)) {
                var newSize = 1
                if (newLeft ne null) newSize += newLeft.size
                if (newRight ne null) newSize += newRight.size
                if (newSize == 1)
                    new UIDTrieSetLeaf(value)
                else
                    new UIDTrieSetInnerNode(newSize, value, newLeft, newRight)
            } else
                this
        } else {
            selectHead(newLeft, newRight)
        }
    }

    //
    // METHODS DEFINED BY UIDTrieSet
    //

    def isSingletonSet: Boolean = false

    def ++(es: UIDSet[T]): UIDSet[T] = {
        es.size match {
            case 0 ⇒ this
            case 1 ⇒ this + es.head
            case _ ⇒ es.foldLeft(this: UIDSet[T])(_ + _)
        }
    }

    private def selectHead(
        left:  UIDTrieSetNodeLike[T],
        right: UIDTrieSetNodeLike[T]
    ): UIDTrieSetNodeLike[T] = {
        val rightSize = if (right ne null) right.size else 0
        var newSize = rightSize
        if (left ne null) {
            val leftSize = left.size
            newSize += leftSize
            val leftValue = left.head
            if (leftSize == 1) {
                if (right eq null)
                    new UIDTrieSetLeaf(leftValue)
                else
                    new UIDTrieSetInnerNode(newSize, leftValue, null, right)
            } else {
                new UIDTrieSetInnerNode(newSize, leftValue, left.dropHead, right)
            }
        } else if (right ne null) {
            val rightValue = right.head
            if (rightSize == 1) {
                if (left eq null)
                    new UIDTrieSetLeaf(rightValue)
                else
                    new UIDTrieSetInnerNode(newSize, rightValue, left, null)
            } else {
                new UIDTrieSetInnerNode(newSize, rightValue, left, right.dropHead)
            }
        } else {
            null
        }
    }

    private def dropHead: UIDTrieSetNodeLike[T] = {
        if (left ne null) {
            val leftValue = left.head
            if (left.size == 1) {
                if (right eq null)
                    new UIDTrieSetLeaf(leftValue)
                else
                    new UIDTrieSetInnerNode(size - 1, leftValue, null, right)
            } else { //left.size >= 2... but maybe we can pull the right value...
                if ((right ne null) && right.size == 1)
                    new UIDTrieSetInnerNode(size - 1, right.head, left, null)
                else
                    new UIDTrieSetInnerNode(size - 1, leftValue, left.dropHead, right)
            }
        } else if (right ne null) {
            val rightValue = right.head
            if (right.size == 1) {
                if (left eq null)
                    new UIDTrieSetLeaf(rightValue)
                else
                    new UIDTrieSetInnerNode(size - 1, rightValue, left, null)
            } else { //right.size >= 2... but maybe we can pull the left value...
                if ((left ne null) && left.size == 1)
                    new UIDTrieSetInnerNode(size - 1, left.head, null, right)
                else
                    new UIDTrieSetInnerNode(size - 1, rightValue, left, right.dropHead)
            }
        } else {
            null
        }
    }

    private[immutable] def contains(eId: Int, shiftedEId: Int): Boolean = {
        /* The recursive version is roughly 5% slower...
        this.value.id == eId || {
            if ((shiftedEId & 1) == 1)
                right != null && right.contains(eId, shiftedEId >>> 1)
            else
                left != null && left.contains(eId, shiftedEId >>> 1)
        }
        */

        var currentNode: UIDTrieSetNodeLike[T] = this
        var currentShiftedEId = shiftedEId
        do {
            if (currentNode.value.id == eId)
                return true;

            if ((currentShiftedEId & 1) == 1)
                currentNode = currentNode.right
            else
                currentNode = currentNode.left

            currentShiftedEId = currentShiftedEId >>> 1

        } while (currentNode ne null)
        false
    }

    private[immutable] def +!(e: T, eId: Int, shiftedEId: Int, level: Int): UIDTrieSetNodeLike[T]

    private def +(e: T, eId: Int, shiftedEId: Int, level: Int): UIDTrieSetNodeLike[T] = {
        val valueId = this.value.id
        // In the following we try to minimize the high of the tree.
        if (valueId == eId)
            return this;

        val right = this.right
        val left = this.left
        var newRight = right
        var newLeft = left
        if ((shiftedEId & 1) == 1) {
            // we have to add the value "here" or on the right branch
            if (newRight eq null)
                newRight = new UIDTrieSetLeaf(e)
            else {
                val newShiftedEId = shiftedEId >>> 1
                if ((newLeft eq null) &&
                    (valueId >>> level & 1) == 0 &&
                    !newRight.contains(eId, newShiftedEId)) {
                    // we can move the current value to the empty left branch...
                    return new UIDTrieSetInnerNode(size + 1, e, new UIDTrieSetLeaf(value), newRight)
                } else {
                    newRight += (e, eId, newShiftedEId, level + 1)
                    if (newRight eq right)
                        return this;
                }
            }
        } else {
            if (newLeft eq null)
                newLeft = new UIDTrieSetLeaf(e)
            else {
                val newShiftedEId = shiftedEId >>> 1
                if ((newRight eq null) &&
                    (valueId >>> level & 1) == 1 &&
                    !newLeft.contains(eId, newShiftedEId)) {
                    // we can move the current value to the empty right branch...
                    return new UIDTrieSetInnerNode(size + 1, e, newLeft, new UIDTrieSetLeaf(value))
                } else {
                    newLeft += (e, eId, newShiftedEId, level + 1)
                    if (newLeft eq left)
                        return this;
                }
            }
        }
        new UIDTrieSetInnerNode(size + 1, value, newLeft, newRight)
    }

    private def -(eId: Int, shiftedEId: Int): UIDTrieSetNodeLike[T] = {
        // assert( size > 3) // i.e., after removal we still have a tree
        val value = this.value
        if (value.id == eId) {
            dropHead
        } else { // we don't delete this value ...
            val left = this.left
            val right = this.right
            var newLeft = left
            var newRight = right
            if ((shiftedEId & 1) == 1) {
                if (right eq null)
                    return this;
                // we have to search for the value in the right tree
                newRight = right - (eId, shiftedEId >>> 1)
                if (newRight eq right)
                    return this;
            } else {
                if (left eq null)
                    return this;
                newLeft = left - (eId, shiftedEId >>> 1)
                if (newLeft eq left)
                    return this;
            }
            new UIDTrieSetInnerNode(size - 1, value, newLeft, newRight)
        }
    }

    def showTree(level: Int = 0): String = {
        val indent = "  " * level
        indent + value.id.toBinaryString + s" #$size("+
            (if (left ne null) s"\n$indent left ="+left.showTree(level + 1)+"\n" else "") +
            (if (right ne null) s"\n$indent right="+right.showTree(level + 1)+"\n)" else ")")
    }
}

private[immutable] object UIDTrieSetNode {

    def apply[T <: UID](
        size:  Int,
        value: T,
        left:  UIDTrieSetNodeLike[T],
        right: UIDTrieSetNodeLike[T]
    ): UIDTrieSetNodeLike[T] = {
        if (size == 1)
            new UIDTrieSetLeaf(value)
        else
            new UIDTrieSetInnerNode(size, value, left, right)
    }

}

case class UIDTrieSetLeaf[T <: UID] private[immutable] (
        value: T
) extends UIDTrieSetNodeLike[T] {
    final override def size: Int = 1
    final def left: UIDTrieSetNodeLike[T] = null
    final def right: UIDTrieSetNodeLike[T] = null
    override def last: T = value
    override def filter(p: T ⇒ Boolean): UIDSet[T] = if (p(value)) this else null
    override def contains(e: T): Boolean = e.id == value.id

    override private[opalj] def +!(e: T): UIDSet[T] = throw new UnknownError

    private[immutable] def +!(e: T, eId: Int, shiftedEId: Int, level: Int): UIDTrieSetNodeLike[T] = {
        if (value.id == eId)
            return this;

        if ((shiftedEId & 1) == 1)
            new UIDTrieSetInnerNode(2, value, null, new UIDTrieSetLeaf(e))
        else
            new UIDTrieSetInnerNode(2, value, new UIDTrieSetLeaf(e), null)
    }

}

// we wan't to be able to adapt the case class...
case class UIDTrieSetInnerNode[T <: UID] private[immutable] (
        protected var theSize: Int,
        protected var value:   T,
        protected var left:    UIDTrieSetNodeLike[T],
        protected var right:   UIDTrieSetNodeLike[T]
) extends UIDTrieSetNodeLike[T] {

    final override def size = theSize

    override def last: T = {
        if (right ne null)
            right.last
        else if (left ne null)
            left.last
        else
            value
    }

    override private[opalj] def +!(e: T): UIDSet[T] = {
        val eId = e.id
        this +! (e, eId, eId, 0)
        this.theSize = 1 +
            (if (left ne null) left.size else 0) +
            (if (right ne null) right.size else 0)
        this
    }

    /** @return `Some(Node)` if the value was added, None otherwise! */
    private[immutable] def +!(e: T, eId: Int, shiftedEId: Int, level: Int): UIDTrieSetNodeLike[T] = {
        val value = this.value
        val valueId = value.id
        if (eId == valueId)
            return this;

        val left = this.left
        val right = this.right
        if ((shiftedEId & 1) == 1) {
            // we have to add the new value here or to the right branch
            if (right eq null) {
                this.right = new UIDTrieSetLeaf(e)
                this.theSize += 1
            } else if ((left eq null) &&
                (valueId >>> level & 1) == 0 &&
                !right.contains(eId, shiftedEId >>> 1)) {
                this.left = new UIDTrieSetLeaf(value)
                this.value = e
                this.theSize += 1
            } else {
                val newRight = (right +! (e, eId, shiftedEId >>> 1, level + 1))
                this.right = newRight
                this.theSize = (if (left ne null) left.size else 0) + newRight.size + 1
            }
        } else {
            // we have to add the new value here or to the left branch
            if (left eq null) {
                this.left = new UIDTrieSetLeaf(e)
                this.theSize += 1
            } else if ((right eq null) &&
                (valueId >>> level & 1) == 1 &&
                !left.contains(eId, shiftedEId >>> 1)) {
                this.right = new UIDTrieSetLeaf(value)
                this.value = e
                this.theSize += 1
            } else {
                val newLeft = (left +! (e, eId, shiftedEId >>> 1, level + 1))
                this.left = newLeft
                this.theSize = newLeft.size + (if (right ne null) right.size else 0) + 1
            }
        }
        this
    }

}

object UIDSet {

    class UIDSetBuilder[T <: UID] extends Builder[T, UIDSet[T]] {
        private var s: UIDSet[T] = empty[T]
        def +=(elem: T): this.type = {
            s +!= elem
            this
        }
        def clear(): Unit = s = empty
        def result(): UIDSet[T] = s
    }

    implicit def canBuildFrom[T <: UID]: CanBuildFrom[UIDSet[_], T, UIDSet[T]] = {
        new CanBuildFrom[UIDSet[_], T, UIDSet[T]] {
            def apply(from: UIDSet[_]) = newBuilder[T]
            def apply() = newBuilder[T]
        }
    }

    def canBuildUIDSet[T <: UID]: CanBuildFrom[Any, T, UIDSet[T]] = {
        new CanBuildFrom[Any, T, UIDSet[T]] {
            def apply(from: Any) = newBuilder[T]
            def apply() = newBuilder[T]
        }
    }

    def newBuilder[T <: UID]: UIDSetBuilder[T] = new UIDSetBuilder[T]

    def empty[T <: UID]: UIDSet[T] = UIDSet0.asInstanceOf[UIDSet[T]]

    def apply[T <: UID](vs: T*): UIDSet[T] = {
        if (vs.isEmpty)
            empty[T]
        else {
            vs.tail.foldLeft(new UIDSet1(vs.head): UIDSet[T])(_ +! _)
        }
    }

}
