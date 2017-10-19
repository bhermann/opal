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
package graphs

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.Matchers

/**
 * Tests the SCC algorithm.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class ClosedSCCTest extends FlatSpec with Matchers {

    "an empty graph" should "not contain any cSCCs" in {
        val g = Graph.empty[String]
        closedSCCs(g) should be(List.empty)
    }

    "a graph with just one node" should "not contain any cSCCs" in {
        val g = Graph.empty[String] += ("a")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with a single path of three elements" should "not contain any cSCCs" in {
        val g = Graph.empty[String] += ("a" → "b") += ("b" → "c")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with a single path of 5 elements" should "not contain any cSCCs" in {
        val g = Graph.empty[String] += ("a" → "b") += ("b" → "c") += ("c" → "d") += ("d" → "e")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with a single path of 5 elements, specified in mixed order" should "not contain any cSCCs" in {
        val g = Graph.empty[String] += ("d" → "e") += ("a" → "b") += ("c" → "d") += ("b" → "c")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with multiple nodes, but no edges" should "not contain any cSCCs" in {
        val g = Graph.empty[String] += ("a")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with one node with a self dependency" should "contain one cSCC with the node" in {
        val g = Graph.empty[String] += ("a", "a")
        closedSCCs(g).map(_.toList) should be(List(List("a")))
    }

    "a graph with four nodes with two nodes with a self dependency" should
        "contain two cSCCs with the respective nodes" in {
            val g = Graph.empty[String] += ("a", "a") += ("b") += ("c" → "c") += ("d")
            closedSCCs(g).map(_.toList.sorted).toSet should be(Set(List("a"), List("c")))
        }

    "a graph with two nodes which form a cSCCs" should "contain the cSCCs" in {
        val g = Graph.empty[String] += ("a" → "b") += ("b" → "a")
        closedSCCs(g).map(_.toList.sorted) should be(List(List("a", "b")))
    }

    "a graph with four nodes which form a cSCCs" should "contain the cSCCs" in {
        val g = Graph.empty[String] += ("a" → "b") += ("b" → "c") += ("c" → "d") += ("d" → "a")
        closedSCCs(g).map(_.toList.sorted) should be(List(List("a", "b", "c", "d")))
    }

    "a graph with four nodes which form a cSCCs with multiple edges between pairs of nodes" should
        "contain the cSCCs" in {
            val g = Graph.empty[String] +=
                ("a" → "b") += ("b" → "c") += ("c" → "d") += ("d" → "a") +=
                ("a" → "b") += ("b" → "c") += ("c" → "d") += ("d" → "a")
            assert(g.successors("a").size == 2)
            closedSCCs(g).map(_.toList.sorted) should be(List(List("a", "b", "c", "d")))
        }

    "a large tree-like graph " should "not contain a cSCC" in {
        val g = Graph.empty[String] +=
            ("a", "b") += ("a" → "c") += ("a" → "d") +=
            ("c" → "e") += ("d" → "e") += ("e" → "f")
        closedSCCs(g) should be(List.empty)
    }

    "a graph with three nodes with a cSCC and an incoming dependency" should "contain one cSCC" in {
        val g = Graph.empty[String] += ("a" → "b") += ("b" → "a") += ("c" → "a")
        g.successors.size should be(3)
        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("a", "b"))
        if (cSCCs != expected) {
            fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
        }
    }

    "a graph with three nodes with a connected component but an outgoing dependency" should
        "not contain a cSCC" in {
            val g = Graph.empty[String] += ("a" → "b") += ("b" → "a") += ("b" → "c")
            g("a").size should be(1)
            g("b").size should be(2)
            val cSCCs = closedSCCs(g)
            if (cSCCs.nonEmpty) {
                fail(s"the graph $g contains no closed SCCs, but found $cSCCs")
            }
        }

    "a graph with five nodes with two cSCCs and an incoming dependency" should
        "contain two cSCCs" in {
            val data = List(
                ("a" → "b"),
                ("b" → "a"),
                ("c" → "a"), ("c" → "d"),
                ("d" → "e"),
                ("e" → "d")
            )
            data.permutations.foreach { aPermutation ⇒
                val g = aPermutation.foldLeft(Graph.empty[String])(_ += _)
                g.vertices.size should be(5)

                val cSCCs = closedSCCs(g).map(_.toList.sorted).toSet
                val expected = Set(List("a", "b"), List("d", "e"))
                if (cSCCs != expected) {
                    fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
                }

            }
        }

    "a graph with one SCC and once cSCCs" should "contain one cSCCs" in {
        val g = Graph.empty[String] +=
            ("a" → "b") += ("f" → "b") += ("f" → "a") += ("b" → "f") +=
            ("a" → "e") += ("e" → "d") += ("d" → "c") += ("c" → "e")
        g.vertices.size should be(6)
        g.successors.map(_._2.size).sum should be(8)
        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("c", "d", "e"))
        if (cSCCs != expected) {
            fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
        }
    }

    "a large totally connected graph" should "contain one cSCCs" in {
        val g = Graph.empty[String] +=
            ("a" → "b") += ("a" → "c") += ("a" → "d") += ("a" → "e") +=
            ("b" → "a") += ("b" → "c") += ("b" → "d") += ("b" → "e") +=
            ("c" → "b") += ("c" → "a") += ("c" → "d") += ("c" → "e") +=
            ("d" → "a") += ("d" → "b") += ("d" → "c") += ("d" → "e") +=
            ("e" → "a") += ("e" → "b") += ("e" → "c") += ("e" → "d")
        g.vertices.size should be(5)
        g.successors.map(_._2.size).sum should be(20)
        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("a", "b", "c", "d", "e"))
        if (cSCCs != expected) {
            fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
        }
    }

    "a large totally connected graph with self dependencies" should "contain one cSCCs" in {
        val g = Graph.empty[String] +=
            ("a" → "a") += ("a" → "b") += ("a" → "c") += ("a" → "d") += ("a" → "e") +=
            ("b" → "a") += ("b" → "b") += ("b" → "c") += ("b" → "d") += ("b" → "e") +=
            ("c" → "b") += ("c" → "a") += ("c" → "c") += ("c" → "d") += ("c" → "e") +=
            ("d" → "a") += ("d" → "b") += ("d" → "c") += ("d" → "d") += ("d" → "e") +=
            ("e" → "a") += ("e" → "b") += ("e" → "c") += ("e" → "d") += ("e" → "e")
        g.vertices.size should be(5)
        g.successors.map(_._2.size).sum should be(25)
        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("a", "b", "c", "d", "e"))
        if (cSCCs != expected) {
            fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
        }
    }

    "a complex graph with six nodes which creates one cSCC" should "contain one cSCCs" in {
        val data = List(
            ("a" → "b"), ("f" → "b"), ("f" → "a"), ("b" → "f"), ("a" → "e"),
            ("e" → "d"), ("d" → "c"), ("c" → "e"), ("c" → "b")
        )
        var permutationCount = 0
        var testedCount = 0
        val random = new java.util.Random // testing all permutations takes too long...
        data.permutations.foreach { aPermutation ⇒
            permutationCount += 1
            if (random.nextInt(2500) == 1) {
                testedCount += 1
                info(s"tested permutation: $aPermutation")
                val g = aPermutation.foldLeft(Graph.empty[String])(_ += _)
                g.vertices.size should be(6)

                val cSCCs = closedSCCs(g).map(_.toList.sorted)
                val expected = List(List("a", "b", "c", "d", "e", "f"))
                if (cSCCs != expected) {
                    fail(s"the graph $g (created with permutation $permutationCount: $aPermutation) "+
                        s"contains one closed SCCs $expected, but found $cSCCs")
                }
            }
        }
        info(s"tested $testedCount permutations")
    }

    "a complex graph with several cSCCs and connected components" should "contain all cSCCs" in {
        val data = List(
            ("a" → "b"), ("b" → "c"), ("c" → "a"),
            ("g" → "f"),
            ("b" → "d"),
            ("a" → "h"), ("h" → "j"), ("j" → "i"), ("i" → "j"), ("i" → "k"), ("k" → "h")
        )
        var permutationCount = 0
        var testedCount = 0
        val random = new java.util.Random // testing all permutations takes FAR too long...
        data.permutations.foreach { aPermutation ⇒
            permutationCount += 1
            if (random.nextInt(250000) == 1) {
                val data = List(
                    ("f" → "c"), ("f" → "g"), ("d" → "e"), ("e" → "d"), ("l" → "m"), ("m" → "l")
                ) ::: aPermutation
                testedCount += 1
                info(s"tested permutation: $data")
                val g = data.foldLeft(Graph.empty[String])(_ += _)
                g.vertices.size should be(13)

                val cSCCs = closedSCCs(g).map(_.toList.sorted).toSet
                val expected = Set(List("l", "m"), List("h", "i", "j", "k"), List("d", "e"))
                if (cSCCs != expected) {
                    fail(s"the graph $g (created with permutation $permutationCount: $data) "+
                        s"contains three closed SCCs $expected, but found $cSCCs")
                }
            }
        }
        info(s"tested $testedCount permutations")
    }

    "a graph with four nodes with a path which has a connection to one cSCCs" should
        "contain one cSCCs" in {
            val data = List(("a" → "b"), ("b" → "c"), ("c" → "a"), ("b" → "d"), ("d" → "d"))
            data.permutations.foreach { aPermutation ⇒
                val g = aPermutation.foldLeft(Graph.empty[String])(_ += _)
                g.vertices.size should be(4)
                val cSCCs = closedSCCs(g)
                val expected = List(List("d"))
                if (cSCCs != expected) {
                    fail(s"the graph $g contains one closed SCCs $expected, but found $cSCCs")
                }

            }
        }

    "a graph with two connected sccs which are connected to one cSCC (requires the correct setting of the non-cSCC node)" should "contain one cSCCs" in {
        val g = Graph.empty[String] +=
            ("a", "b") += ("b", "c") += ("c", "d") += ("d", "e") += ("c", "g") += ("g", "a") +=
            ("e", "d") += ("g", "h") += ("h", "j") += ("j", "g")

        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("d", "e"))
        if (cSCCs != expected) {
            fail(s"$g: cscc with $expected expected, but found $cSCCs")
        }
    }

    "a graph with two cSCCs which are connected by one SCC" should "contain two cSCCs" in {
        val g = Graph.empty[String] += ("a", "b") += ("b", "c") +=
            ("c", "d") += ("d", "e") += ("c", "g") += ("g", "a") += ("e", "d") +=
            ("g", "h") += ("h", "j") += ("j", "g") += ("b", "x") += ("y", "x") +=
            ("x", "z") += ("z", "y")
        val cSCCs = closedSCCs(g).map(_.toList.sorted).toSet
        val expected = Set(List("d", "e"), List("x", "y", "z"))
        if (cSCCs != expected) {
            fail(s"$g: cscc with $expected expected, but found $cSCCs")
        }
    }

    "a graph with one cSCC which has multiple incoming edges" should "contain one cSCCs" in {
        val g = Graph.empty[String] +=
            ("u", "v") += ("v", "a") += ("w", "e") += ("x", "c") += ("x", "b") += ("u", "d") +=
            ("a", "b") += ("b", "c") += ("c", "a") +=
            ("a", "e") += ("e", "d") += ("d", "c")
        val cSCCs = closedSCCs(g).map(_.toList.sorted)
        val expected = List(List("a", "b", "c", "d", "e"))
        if (cSCCs != expected) {
            fail(s"$g: cscc with $expected expected, but found $cSCCs")
        }
    }

    "a graph with three cSCC and a SCC which has multiple incoming edges" should
        "contain three cSCCs" in {
            val g = Graph.empty[String] +=
                ("u", "a") += ("v", "c") += ("w", "c") += ("w", "e") += ("w", "g") += ("x", "g") +=
                ("h", "z") += ("y", "b") += ("y", "d") += ("y", "f") += ("y", "h") +=
                ("a", "b") += ("b", "a") +=
                ("c", "d") += ("d", "c") +=
                ("e", "f") += ("f", "e") +=
                ("g", "h") += ("h", "g")
            val cSCCs = closedSCCs(g).map(_.toList.sorted).toSet
            val expected = Set(List("a", "b"), List("c", "d"), List("e", "f"))
            if (cSCCs != expected) {
                fail(s"$g: cscc with $expected expected, but found $cSCCs")
            }
        }

    "a multi-graph with cSCCs and a SCCs" should "contain the correct number of cSCCs" in {
        val seed = System.currentTimeMillis
        val random = new java.util.Random(seed) // testing all permutations takes FAR too long...

        val edges = Array(
            ("c" -> "b"),
            ("d" -> "e"),
            ("e" -> "e"),
            ("f" -> "g"),
            ("g" -> "f"),
            ("h" -> "i"),
            ("j" -> "k"),
            ("k" -> "l"),
            ("l" -> "k"), ("l" -> "n"),
            ("m" -> "l"),
            ("o" -> "b"), ("o" -> "e"), ("o" -> "g"),
            ("p" -> "r"), ("p" -> "q"),
            ("q" -> "p"),
            ("r" -> "q"), ("r" -> "s"),
            ("s" -> "t"), ("s" -> "q"),
            ("t" -> "p"),
            ("u" -> "p"), ("u" -> "v"), ("u" -> "w"),
            ("v" -> "w"),
            ("w" -> "v"),
            ("x" -> "w"),
            ("y" -> "t")
        )
        val expectedCSCCs = Set(
            List("e"),
            List("v", "w"),
            List("f", "g"),
            List("p", "q", "r", "s", "t")
        )

        var run = 0

        do {
            val g = {
                val g = Graph.empty[String] += ("a") += ("b") += ("i") += ("n") += ("z")
                // permutate the edges
                var swaps = 10
                while (swaps > 0) {
                    val cellA = random.nextInt(edges.length)
                    val cellB = random.nextInt(edges.length)
                    val temp = edges(cellA)
                    edges(cellA) = edges(cellB)
                    edges(cellB) = temp
                    swaps -= 1
                }
                // add the adges
                edges.foreach(g.+=)
                g
            }
            val cSCCs = closedSCCs(g).map(_.toList.sorted).toSet
            if (cSCCs != expectedCSCCs) {
                fail(s"$g: cscc with $expectedCSCCs expected, but found $cSCCs (run=$run)")
            }
            run += 1
        } while (System.currentTimeMillis - seed < 1000)
        info(s"tested $run permutations of the graph (initial seed: $seed)")
    }

}
