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

/**
 * Models a three state answer ([[Yes]], [[No]], [[Unknown]]).
 *
 * @author Michael Eichberg
 */
sealed trait Answer {

    /**
     * Returns `true` if and only if this answer is `Yes`.
     *
     * Effectively the same as a comparison with [[Yes]].
     */
    def isYes: Boolean

    /**
     * Returns `true` if and only if this answer is `No`.
     *
     * Effectively the same as a comparison with [[No]].
     */
    def isNo: Boolean

    /**
     * Returns `true` if and only if this answer is `Unknown`.
     *
     * Effectively the same as a comparison with [[Unknown]].
     */
    def isUnknown: Boolean

    /**
     * Returns `true` if this answer is `Yes` or `Unknown`, `false` otherwise.
     */
    def isNotNo: Boolean

    /**
     * Returns `true` if this answer is `Yes` or `Unknown`, `false` otherwise.
     */
    final def isYesOrUnknown: Boolean = isNotNo

    /**
     * Returns `true` if this answer is `No` or `Unknown`, `false` otherwise.
     */
    def isNotYes: Boolean

    /**
     * Returns `true` if this answer is `No` or `Unknown`, `false` otherwise.
     */
    final def isNoOrUnknown: Boolean = isNotYes

    /**
     * Returns `true` if this answer is either `Yes` or `No`; false if this answer
     * is `Unknown`.
     */
    def isYesOrNo: Boolean

    /**
     * Joins this answer and the given answer. In this case `Unknown` will
     * represent the case that we have both answers; that is we have
     * a set based view w.r.t. `Answer`s. Hence,
     * `this join Unknown` is considered as `this join {Yes, No}` where
     * the set `{Yes, No}` is represented by `Unknown`.
     *
     * If the other `Answer` is identical to `this` answer `this` is returned,
     * otherwise `Unknown` is returned.
     *
     */
    def join(other: Answer): Answer

    /**
     * The negation of this `Answer`. If the answer is `Unknown` the negation is
     * still `Unknown`.
     */
    def negate: Answer

    /**
     * @see [[Answer#negate]]
     */
    final def unary_! : Answer = this.negate

    /**
     * The logical conjunction of this answer and the given answer.
     * In this case Unknown is considered to either represent the
     * answer Yes or No; hence, `this && other` is treated as
     * `this && (Yes || No)`.
     */
    def &&(other: Answer): Answer

    /**
     * The logical disjunction of this answer and the given answer.
     * In this case Unknown is considered to either represent the
     * answer Yes or No; hence, `this || other` is treated as
     * `this || (Yes || No)`.
     */
    def ||(other: Answer): Answer

    final def ||(other: Boolean): Answer = this || Answer(other)

    final def &&(other: Boolean): Answer = this && Answer(other)

    /**
     * If this answer is unknown the given function is evaluated and that
     * result is returned, otherwise `this` answer is returned.
     */
    def ifUnknown(f: ⇒ Answer): Answer = this
}

/**
 * Factory for `Answer`s.
 *
 * @author Michael Eichberg
 */
object Answer {

    /**
     * Returns [[org.opalj.Yes]] if `value` is `true` and
     * [[org.opalj.No]] otherwise.
     */
    def apply(value: Boolean): Answer = if (value) Yes else No

    def apply(result: Option[_]): Answer = if (result.isDefined) Yes else No
}

/**
 * Represents the answer to a question where the answer is `Yes`.
 *
 * @author Michael Eichberg
 */
final case object Yes extends Answer {
    override def isYes: Boolean = true
    override def isNo: Boolean = false
    override def isUnknown: Boolean = false

    override def isNotNo: Boolean = true
    override def isNotYes: Boolean = false
    override def isYesOrNo: Boolean = true

    override def join(other: Answer): Answer = if (other eq this) this else Unknown

    override def negate: No.type = No
    override def &&(other: Answer): Answer = {
        other match {
            case Yes ⇒ this
            case No  ⇒ No
            case _   ⇒ Unknown
        }
    }
    override def ||(other: Answer): Yes.type = this
}

/**
 * Represents the answer to a question where the answer is `No`.
 *
 * @author Michael Eichberg
 */
final case object No extends Answer {
    override def isYes: Boolean = false
    override def isNo: Boolean = true
    override def isUnknown: Boolean = false

    override def isNotNo: Boolean = false
    override def isNotYes: Boolean = true
    override def isYesOrNo: Boolean = true

    override def join(other: Answer): Answer = if (other eq this) this else Unknown

    override def negate: Yes.type = Yes
    override def &&(other: Answer): No.type = this
    override def ||(other: Answer): Answer = {
        other match {
            case Yes ⇒ Yes
            case No  ⇒ this
            case _   ⇒ Unknown
        }
    }
}

/**
 * Represents the answer to a question where the answer is either `Unknown`
 * or is actuaclly both; that is, `Yes` and `No`.
 *
 * @author Michael Eichberg
 */
final case object Unknown extends Answer {
    override def isYes: Boolean = false
    override def isNo: Boolean = false
    override def isUnknown: Boolean = true

    override def isNotNo: Boolean = true
    override def isNotYes: Boolean = true
    override def isYesOrNo: Boolean = false

    override def join(other: Answer): Unknown.type = this

    override def negate: Unknown.type = this
    override def &&(other: Answer): Answer = if (other eq No) No else this
    override def ||(other: Answer): Answer = if (other eq Yes) Yes else this

    override def ifUnknown(f: ⇒ Answer): Answer = f
}
