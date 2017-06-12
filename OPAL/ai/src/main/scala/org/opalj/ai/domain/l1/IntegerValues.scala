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
package ai
package domain
package l1

import org.opalj.br.ComputationalType
import org.opalj.br.ComputationalTypeInt
import org.opalj.br.CTIntType

/**
 * This domain enables the tracking of an integer value (a constant);
 * unknown integer values are represented using "AnIntegerValue". It basically provides support
 * for constant propagation and constant computations related to integer values.
 *
 * Given that it uses one instance to represent arbitrary integer values, '''constraint
 * propagation is not relevant'''.
 *
 * This domain may be appropriate, e.g., if you want to determine if a field/local is
 * always initialized to a specific value.
 *
 * @author Michael Eichberg
 * @author David Becker
 */
trait IntegerValues extends IntegerValuesDomain with ConcreteIntegerValues {
    domain: Configuration with ExceptionsFactory ⇒

    // -----------------------------------------------------------------------------------
    //
    // REPRESENTATION OF INTEGER LIKE VALUES
    //
    // -----------------------------------------------------------------------------------

    /**
     * Abstracts over all values with computational type `integer`.
     */
    sealed trait IntegerLikeValue
            extends TypedValue[CTIntType]
            with IsIntegerValue[IntegerLikeValue] {
        this: DomainTypedValue[CTIntType] ⇒

        final def computationalType: ComputationalType = ComputationalTypeInt

    }

    /**
     * Represents an (unknown) integer value.
     *
     * Models the top value of this domain's lattice.
     */
    trait AnIntegerValue extends IntegerLikeValue { this: DomainTypedValue[CTIntType] ⇒ }

    /**
     * Represents one, concrete integer value.
     */
    abstract class TheIntegerValue extends IntegerLikeValue { this: DomainTypedValue[CTIntType] ⇒
        val value: Int
    }

    object TheIntegerValue { def unapply(v: TheIntegerValue): Some[Int] = Some(v.value) }

    // -----------------------------------------------------------------------------------
    //
    // COMPUTATIONS RELATED TO INTEGER VALUES
    //
    // -----------------------------------------------------------------------------------

    //
    // QUESTIONS ABOUT VALUES
    //

    @inline final override def intValue[T](
        value: DomainValue
    )(
        f: Int ⇒ T
    )(orElse: ⇒ T): T =
        value match {
            case v: TheIntegerValue ⇒ f(v.value)
            case _                  ⇒ orElse
        }

    @inline final override def intValueOption(value: DomainValue): Option[Int] =
        value match {
            case v: TheIntegerValue ⇒ Some(v.value)
            case _                  ⇒ None
        }

    @inline protected final def intValues[T](
        value1: DomainValue, value2: DomainValue
    )(
        f: (Int, Int) ⇒ T
    )(
        orElse: ⇒ T
    ): T = {
        intValue(value1) { v1 ⇒ intValue(value2) { v2 ⇒ f(v1, v2) } { orElse } } { orElse }
    }

    override def intAreEqual(pc: PC, value1: DomainValue, value2: DomainValue): Answer = {
        intValues(value1, value2) { (v1, v2) ⇒ Answer(v1 == v2) } { Unknown }
    }

    override def intIsSomeValueInRange(
        pc:         PC,
        value:      DomainValue,
        lowerBound: Int,
        upperBound: Int
    ): Answer = {
        if (lowerBound == Int.MinValue && upperBound == Int.MaxValue)
            return Yes;

        value match {
            case TheIntegerValue(v) ⇒ Answer(v >= lowerBound && v <= upperBound)
            case _                  ⇒ Unknown
        }
    }

    override def intIsSomeValueNotInRange(
        pc:         PC,
        value:      DomainValue,
        lowerBound: Int,
        upperBound: Int
    ): Answer = {
        if (lowerBound == Int.MinValue && upperBound == Int.MaxValue)
            return No;

        value match {
            case TheIntegerValue(v) ⇒ Answer(v < lowerBound || v > upperBound)
            case _                  ⇒ Unknown
        }
    }

    override def intIsLessThan(pc: PC, left: DomainValue, right: DomainValue): Answer = {
        intValues(left, right) { (v1, v2) ⇒ Answer(v1 < v2) } { Unknown }
    }

    override def intIsLessThanOrEqualTo(pc: PC, left: DomainValue, right: DomainValue): Answer = {
        intValues(left, right) { (v1, v2) ⇒ Answer(v1 <= v2) } { Unknown }
    }

    // -----------------------------------------------------------------------------------
    //
    // HANDLING OF COMPUTATIONS
    //
    // -----------------------------------------------------------------------------------

    //
    // UNARY EXPRESSIONS
    //
    override def ineg(pc: PC, value: DomainValue): DomainValue = {
        value match {
            case TheIntegerValue(v) ⇒ IntegerValue(pc, -v)
            case v                  ⇒ v
        }
    }

    //
    // BINARY EXPRESSIONS
    //

    override def iinc(pc: PC, value: DomainValue, increment: Int): DomainValue = {
        value match {
            case TheIntegerValue(v) ⇒ IntegerValue(pc, v + increment)
            case v                  ⇒ v
        }
    }

    override def iadd(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue = {
        intValues(value1, value2) { (v1, v2) ⇒
            IntegerValue(pc, v1 + v2)
        } {
            IntegerValue(origin = pc)
        }
    }

    override def isub(pc: PC, left: DomainValue, right: DomainValue): DomainValue = {
        intValues(left, right) { (l, r) ⇒
            IntegerValue(pc, l - r)
        } {
            IntegerValue(origin = pc)
        }
    }

    override def imul(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue = {
        (value1, value2) match {
            case (_, TheIntegerValue(0))                  ⇒ value2
            case (_, TheIntegerValue(1))                  ⇒ value1
            case (TheIntegerValue(0), _)                  ⇒ value1
            case (TheIntegerValue(1), _)                  ⇒ value2

            case (TheIntegerValue(l), TheIntegerValue(r)) ⇒ IntegerValue(pc, l * r)

            case _                                        ⇒ IntegerValue(origin = pc)
        }
    }

    override def idiv(
        pc:          PC,
        numerator:   DomainValue,
        denominator: DomainValue
    ): IntegerValueOrArithmeticException = {

        (numerator, denominator) match {
            case (_, TheIntegerValue(0))                  ⇒ ThrowsException(VMArithmeticException(pc))
            case (TheIntegerValue(n), TheIntegerValue(d)) ⇒ ComputedValue(IntegerValue(pc, n / d))
            case (_, TheIntegerValue(_ /*<=> not 0*/ ))   ⇒ ComputedValue(IntegerValue(origin = pc))
            case _ ⇒
                if (throwArithmeticExceptions)
                    ComputedValueOrException(IntegerValue(origin = pc), VMArithmeticException(pc))
                else
                    ComputedValue(IntegerValue(origin = pc))
        }
    }

    override def irem(
        pc:    PC,
        left:  DomainValue,
        right: DomainValue
    ): IntegerValueOrArithmeticException = {

        (left, right) match {
            case (_, TheIntegerValue(0))                  ⇒ ThrowsException(VMArithmeticException(pc))
            case (TheIntegerValue(n), TheIntegerValue(d)) ⇒ ComputedValue(IntegerValue(pc, n % d))
            case (_, TheIntegerValue(_ /*<=> not 0*/ ))   ⇒ ComputedValue(IntegerValue(origin = pc))
            case _ ⇒
                if (throwArithmeticExceptions)
                    ComputedValueOrException(IntegerValue(origin = pc), VMArithmeticException(pc))
                else
                    ComputedValue(IntegerValue(origin = pc))
        }
    }

    override def iand(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        (value1, value2) match {
            case (_, TheIntegerValue(-1))                 ⇒ value1
            case (_, TheIntegerValue(0))                  ⇒ value2
            case (TheIntegerValue(-1), _)                 ⇒ value2
            case (TheIntegerValue(0), _)                  ⇒ value1

            case (TheIntegerValue(l), TheIntegerValue(r)) ⇒ IntegerValue(pc, l & r)

            case _                                        ⇒ IntegerValue(origin = pc)
        }

    override def ior(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        (value1, value2) match {
            case (_, TheIntegerValue(-1))                 ⇒ value2
            case (_, TheIntegerValue(0))                  ⇒ value1
            case (TheIntegerValue(-1), _)                 ⇒ value1
            case (TheIntegerValue(0), _)                  ⇒ value2

            case (TheIntegerValue(l), TheIntegerValue(r)) ⇒ IntegerValue(pc, l | r)

            case _                                        ⇒ IntegerValue(origin = pc)
        }

    override def ishl(pc: PC, value: DomainValue, shift: DomainValue): DomainValue = {
        intValues(value, shift) { (v1, v2) ⇒
            IntegerValue(pc, v1 << v2)
        } {
            IntegerValue(origin = pc)
        }
    }

    override def ishr(pc: PC, value: DomainValue, shift: DomainValue): DomainValue = {
        intValues(value, shift) { (v1, v2) ⇒
            IntegerValue(pc, v1 >> v2)
        } {
            IntegerValue(origin = pc)
        }
    }

    override def iushr(pc: PC, value: DomainValue, shift: DomainValue): DomainValue = {
        intValues(value, shift) { (v1, v2) ⇒
            IntegerValue(pc, v1 >>> v2)
        } {
            IntegerValue(origin = pc)
        }
    }

    override def ixor(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue = {
        intValues(value1, value2) { (v1, v2) ⇒
            IntegerValue(pc, v1 ^ v2)
        } {
            IntegerValue(origin = pc)
        }
    }

    //
    // TYPE CONVERSION INSTRUCTIONS
    //

    override def i2b(pc: PC, value: DomainValue): DomainValue =
        value match {
            case TheIntegerValue(v) ⇒ IntegerValue(pc, v.toByte.toInt)
            case v                  ⇒ v
        }

    override def i2c(pc: PC, value: DomainValue): DomainValue =
        value match {
            case TheIntegerValue(v) ⇒ IntegerValue(pc, v.toChar.toInt)
            case v                  ⇒ v
        }

    override def i2s(pc: PC, value: DomainValue): DomainValue =
        value match {
            case TheIntegerValue(v) ⇒ IntegerValue(pc, v.toShort.toInt)
            case v                  ⇒ v
        }

}
