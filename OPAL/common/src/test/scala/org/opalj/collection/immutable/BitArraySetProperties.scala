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

import org.junit.runner.RunWith

import org.scalatest.junit.JUnitRunner
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.classify
import org.scalacheck.Prop.BooleanOperators
import org.scalacheck.Gen
import org.scalacheck.Arbitrary
import scala.collection.immutable.{BitSet ⇒ SBitSet}
import java.util.Random

/**
 * Tests `BitArraySet`.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
object BitArraySetProperties extends Properties("BitArraySetProperties") {

    val r = new Random()

    val smallListsGen = for { m ← Gen.listOfN(8, Arbitrary.arbitrary[Int]) } yield (m)

    val frequencies = List(
        (1, Gen.choose(1, 63)),
        (1, Gen.choose(64, 127)),
        (1, Gen.choose(128, 128000))
    )

    // generates an IntArraySet which only contains positive values
    implicit val arbIntArraySet: Arbitrary[IntArraySet] = Arbitrary {
        Gen.sized { s ⇒
            Gen.frequency(frequencies: _*).map { max ⇒
                (0 until s).foldLeft(IntArraySet.empty) { (c, n) ⇒ c + r.nextInt(max) }
            }
        }
    }

    implicit val arbIntBitSet: Arbitrary[SBitSet] = Arbitrary {
        Gen.sized { s ⇒
            Gen.frequency(frequencies: _*).map { max ⇒
                (0 until s).foldLeft(SBitSet.empty) { (c, n) ⇒ c + r.nextInt(max) }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //                             P R O P E R T I E S

    property("create BitArraySet based on empty set (implicitly intIterator & contains)") = forAll { s: IntArraySet ⇒
        val bas = s.foldLeft(BitArraySet.empty)(_ + _)
        classify(s.isEmpty, "empty set") {
            classify(s.nonEmpty && s.max < 64, "set with max value < 64") {
                (bas.isEmpty == s.isEmpty) :| "isEmpty" &&
                    bas.intIterator.forall(s.contains) :| "all values in the bit array set were added" &&
                    s.intIterator.forall(bas.contains) :| "the bit array set contains all values"
            }
        }
    }

    property("create BitArraySet based on initial set with one element (implicitly intIterator & contains)") = forAll { s: IntArraySet ⇒
        s.size > 0 ==> {
            classify(s.max < 64, "set with max value < 64") {
                val sIt = s.intIterator
                val bas = sIt.foldLeft(BitArraySet(sIt.next))(_ + _)
                bas.intIterator.forall(s.contains) :| "all values in the bit array set were added" &&
                    s.intIterator.forall(bas.contains) :| "the bit array set contains all values"
            }
        }
    }

    property("useless +") = forAll { s: IntArraySet ⇒
        val bas = s.foldLeft(BitArraySet.empty)(_ + _)
        s.forall(v ⇒ (bas + v) eq bas)
    }

    property("| and ++ (implicitly intIterator & contains)") = forAll { (s1: SBitSet, s2: SBitSet) ⇒
        val bas1 = s1.foldLeft(BitArraySet.empty)(_ + _)
        val bas2 = s2.foldLeft(BitArraySet.empty)(_ + _)
        val bas3 = bas1 | bas2
        val s3 = s1 | s2

        classify(s1.isEmpty && s2.isEmpty, "both sets are empty") {
            classify(s1.nonEmpty && s2.nonEmpty && s1.max < 64 && s2.max < 64, "both sets max value < 64") {
                classify(s1.size <= s2.size, "|s1| <= |s2|", "|s1| > |s2|") {
                    bas3.intIterator.forall(s3.contains) :| "all values in the bit array set were added" &&
                        s3.iterator.forall(bas3.contains) :| "the bit array set does not contain all values"
                }
            }
        }
    }

    property("useless | ++ => this") = forAll { s: IntArraySet ⇒
        val bas1 = s.foldLeft(BitArraySet.empty)(_ + _)
        val bas2 = s.foldLeft(BitArraySet.empty)(_ + _)
        (bas1 ++ bas2) eq bas1
    }

    property("useless | ++ => that") = forAll { (s1: IntArraySet, s2: IntArraySet) ⇒
        val sMerged = s1 ++ s2
        val bas1 = s1.foldLeft(BitArraySet.empty)(_ + _)
        val bas2 = s2.foldLeft(BitArraySet.empty)(_ + _)
        val basMerged = bas1 ++ bas2
        val basRemerged = (bas1 ++ basMerged /*use that...*/ )
        basMerged.intIterator.forall(sMerged.contains) :| "all values in the bit array set were added" &&
            sMerged.iterator.forall(basMerged.contains) :| "the bit array set does not contain all values" &&
            (basRemerged eq basMerged) :| "same instance"
    }

    property("iterator") = forAll { s: IntArraySet ⇒
        val bas = s.foldLeft(BitArraySet.empty)(_ + _)
        val basIt = bas.iterator
        val basIntIt = bas.intIterator
        basIt.forall(v ⇒ basIntIt.next == v) :| "the scala iterator iterates over the same values" &&
            basIntIt.isEmpty :| "the scala iterator does not miss any values"
    }

    property("- (in order)") = forAll { (s: IntArraySet, other: IntArraySet) ⇒
        var bas = s.foldLeft(BitArraySet.empty)(_ + _)
        other.forall({ v ⇒ bas -= v; !bas.contains(v) }) :| "when we delete a value it is no longer in the set" &&
            s.forall({ v ⇒ bas -= v; !bas.contains(v) }) :| "we successively delete the initial values" &&
            (bas == EmptyBitArraySet) :| "the empty set is always represented by the singleton instance"
    }

    property("- (in reverse order)") = forAll { (s: IntArraySet, other: IntArraySet) ⇒
        var bas = s.foldLeft(BitArraySet.empty)(_ + _)
        other.forall({ v ⇒ bas -= v; !bas.contains(v) }) :| "when we delete a value it is no longer in the set" &&
            s.toChain.reverse.forall({ v ⇒
                bas -= v
                !bas.contains(v)
            }) :| "we successively delete the initial values in reverse order" &&
            (bas == EmptyBitArraySet) :| "the empty set is always represented by the singleton instance"
    }

    property("equals And hashCode (expected equal)") = forAll { s: IntArraySet ⇒
        val bas1 = s.foldLeft(BitArraySet.empty)(_ + _)
        val bas2 = s.foldLeft(BitArraySet.empty)(_ + _)
        bas1 == bas2 && bas1.hashCode == bas2.hashCode
    }

    property("equals And hashCode") = forAll { (s1: IntArraySet, s2: IntArraySet) ⇒
        val bas1 = s1.foldLeft(BitArraySet.empty)(_ + _)
        val bas2 = s2.foldLeft(BitArraySet.empty)(_ + _)
        val sEquals = s1 == s2
        (bas1 == bas2) == sEquals && (!sEquals || bas1.hashCode == bas2.hashCode)
    }

    property("toString") = forAll { s: IntArraySet ⇒
        val bas = s.foldLeft(BitArraySet.empty)(_ + _)
        val basToString = bas.toString.substring(9)
        val sToString = s.toString.substring(9)
        val comparison = basToString == sToString
        if (!comparison) println(s""""$basToString" vs. "$sToString"""")
        comparison
    }
}
