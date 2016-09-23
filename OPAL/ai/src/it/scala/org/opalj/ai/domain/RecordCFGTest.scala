/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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

import java.net.URL
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FunSpec

import scala.collection.JavaConverters._
import scala.collection.mutable

import org.opalj.br.reader.{BytecodeInstructionsCache, Java8FrameworkWithCaching}
import org.opalj.br.analyses.Project
import org.opalj.br.Method
import org.opalj.util.PerformanceEvaluation
import org.opalj.util.PerformanceEvaluation.time
import org.opalj.br.analyses.MethodInfo
import org.opalj.graphs.ControlDependencies
import org.opalj.br.cfg.CFGFactory
import org.opalj.br.cfg.BasicBlock

/**
 * Tests if we are able to computed the CFG as well as the dominator/post-dominator tree for
 * a larger number of classes.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class RecordCFGTest extends FunSpec with Matchers {

    private object DominatorsPerformanceEvaluation extends PerformanceEvaluation

    import DominatorsPerformanceEvaluation.{time ⇒ dTime}

    class RecordCFGDomain[I](val method: Method, val project: Project[URL])
        extends CorrelationalDomain
        with TheProject
        with TheMethod
        with ThrowAllPotentialExceptionsConfiguration
        with DefaultHandlingOfMethodResults
        with IgnoreSynchronization
        with l1.DefaultReferenceValuesBinding
        with l1.NullPropertyRefinement
        with l0.DefaultTypeLevelIntegerValues
        with l0.DefaultTypeLevelLongValues
        with l0.DefaultTypeLevelFloatValues
        with l0.DefaultTypeLevelDoubleValues
        with l0.TypeLevelPrimitiveValuesConversions
        with l0.TypeLevelInvokeInstructions
        with l0.TypeLevelFieldAccessInstructions
        with l0.TypeLevelLongValuesShiftOperators
        with RecordCFG // <=== the domain we are going to test!

    def terminateAfter[T >: Null <: AnyRef](millis: Long)(f: ⇒ T): T = {
        @volatile var result: T = null
        val t = new Thread(new Runnable { def run(): Unit = { result = f } })
        t.start
        t.join(millis)
        t.interrupt()
        t.join(10)
        if (t.isAlive()) {
            // this way it is no longer deprecated...
            t.getClass.getMethod("stop").invoke(t)
            throw new InterruptedException(s"didn't terminate in $millis milliseconds")
        }
        result
    }

    private def analyzeProject(name: String, project: Project[java.net.URL]): Unit = {
        info(s"the loaded project ($name) contains ${project.methodsCount} methods")

        val failures = new java.util.concurrent.ConcurrentLinkedQueue[(String, Throwable)]

        project.parForeachMethodWithBody() { methodInfo ⇒
            val MethodInfo(_, classFile, method) = methodInfo

            try {
                val domain = new RecordCFGDomain(method, project)
                val evaluatedInstructions = dTime('AI) {
                    BaseAI(classFile, method, domain).evaluatedInstructions
                }

                val bbBRCFG = dTime('BasicBlocksBasedBRCFG) { CFGFactory(method.body.get, project.classHierarchy) }
                val bbAICFG = dTime('BasicBlocksBasedAICFG) { domain.bbCFG }

                val pcs = new mutable.BitSet(method.body.size)
                bbAICFG.allBBs.foreach { bbAI ⇒
                    assert(bbAI.startPC <= bbAI.endPC,s"${bbAI.startPC}> ${bbAI.endPC}")
                    if (!pcs.add(bbAI.startPC))
                        fail(s"the (start) pc ${bbAI.startPC} was already used by some other basic block")
                    if (bbAI.endPC != bbAI.startPC) {
                        if (!pcs.add(bbAI.endPC))
                            fail(s"the bb's (end) pc ${bbAI.endPC} ($bbAI) was already used by some other basic block")
                    }

                    val bbBR = bbBRCFG.bb(bbAI.startPC)
                    if (bbBR.isStartOfSubroutine != bbAI.isStartOfSubroutine) {
                        fail(
                            s"inconsistent: bbBR.isStartOfSubroutine(${bbBR.isStartOfSubroutine}) and "+
                                s"bbAI.isStartOfSubroutine (${bbAI.isStartOfSubroutine})"
                        )
                    }
                    val allBRPredecessors = bbBR.predecessors.collect { case bb: BasicBlock ⇒ bb }
                    val allAIPredecessors = bbAI.predecessors.collect { case bb: BasicBlock ⇒ bb }
                    allAIPredecessors.foreach { predecessorBB ⇒
                        if (!allBRPredecessors.exists { p ⇒ p.endPC == predecessorBB.endPC })
                            fail(
                                s"the aibb ($bbAI) has different predecessors than the brbb ($bbBR):"+
                                    allAIPredecessors.mkString("ai:{", ",", "} vs. ") +
                                    allBRPredecessors.mkString("br:{", ",", "}")
                            )
                    }
                }

                evaluatedInstructions.foreach { pc ⇒

                    domain.foreachSuccessorOf(pc) { succPC ⇒
                        domain.predecessorsOf(succPC).contains(pc) should be(true)
                    }

                    domain.foreachPredecessorOf(pc) { predPC ⇒
                        domain.allSuccessorsOf(predPC).contains(pc) should be(true)
                    }

                    val bb = bbAICFG.bb(pc)
                    if (bb eq null) {
                        fail(s"the evaluated instruction $pc is not associated with a basic block")
                    }
                    bb.startPC should be <= (pc)
                    bb.endPC should be >= (pc)
                }

                val dt = dTime('Dominators) { domain.dominatorTree }

                val postDT = dTime('PostDominators) { domain.postDominatorTree }

                val cdg =
                    terminateAfter[ControlDependencies](1000l) {
                        dTime('ControlDependencies) { domain.controlDependencies }
                    }

                evaluatedInstructions foreach { pc ⇒
                    if (pc != dt.startNode &&
                        (dt.dom(pc) != dt.startNode) &&
                        !evaluatedInstructions.contains(dt.dom(pc))) {
                        fail(
                            s"the dominator instruction ${dt.dom(pc)} of instruction $pc "+
                                s"was not evaluated (dominator tree start node: ${dt.startNode}); "+
                                s"code size=${method.body.get.instructions.length}."
                        )
                    }
                    if (pc != postDT.startNode && // this should be always if we have an artificial start node
                        postDT.dom(pc) != postDT.startNode &&
                        !evaluatedInstructions.contains(postDT.dom(pc))) {
                        fail(s"the post-dominator ${postDT.dom(pc)} of $pc was not evaluated")
                    }
                    try {
                        dTime('QueryingControlDependencies) {
                            cdg.xIsControlDependentOn(pc)(x ⇒ { /* "somke test" */ })
                        }
                    } catch {
                        case t: Throwable ⇒
                            t.printStackTrace
                            fail(s"getting the control dependency information for $pc failed", t)
                    }
                }
            } catch {
                case t: Throwable ⇒
                    t.printStackTrace()
                    failures.add((method.toJava(classFile), t))
            }
        }

        if (failures.size > 0) {
            val failureMessages = for { (failure, exception) ← failures.asScala } yield {
                var root: Throwable = exception
                while (root.getCause != null) root = root.getCause
                val location =
                    if (root.getStackTrace() != null && root.getStackTrace().length > 0) {
                        root.getStackTrace().take(5).map { stackTraceElement ⇒
                            stackTraceElement.getClassName+
                                " { "+
                                stackTraceElement.getMethodName+":"+stackTraceElement.getLineNumber+
                                " }"
                        }.mkString("; ")
                    } else {
                        "<location unavailable>"
                    }
                failure+" ["+root.getClass.getSimpleName+": "+root.getMessage+"; location: "+location+"] "
            }

            val failuresHeader = s"${failures.size} exceptions occured in: \n"
            val failuresInfo = failureMessages.mkString(failuresHeader, "\n", "\n")
            fail(failuresInfo)
        }
    }

    describe("calculating the (post)dominator trees and the control dependence information") {

        def printPerformanceData(): Unit = {
            import DominatorsPerformanceEvaluation.getTime

            info("performing AI took (CPU time) "+getTime('AI).toSeconds)
            info("computing dominator information took (CPU time)"+getTime('Dominators).toSeconds)

            val postDominatorsTime = getTime('PostDominators).toSeconds
            info("computing post-dominator information took (CPU time) "+postDominatorsTime)

            val cdgTime = getTime('ControlDependencies).toSeconds
            info("computing control dependency information took (CPU time) "+cdgTime)
            val cdgQueryTime = getTime('QueryingControlDependencies).toSeconds
            info("querying control dependency information took (CPU time) "+cdgQueryTime)

            val bbAICFGTime = getTime('BasicBlocksBasedAICFG).toSeconds
            info("constructing the AI based CFGs took (CPU time) "+bbAICFGTime)

            val bbBRCFGTime = getTime('BasicBlocksBasedBRCFG).toSeconds
            info("constructing the BR based CFGs took (CPU time) "+bbBRCFGTime)

        }

        val reader = new Java8FrameworkWithCaching(new BytecodeInstructionsCache)
        import reader.AllClassFiles

        it("should be possible to calculate the information for all methods of the JDK") {
            DominatorsPerformanceEvaluation.resetAll()

            val project = org.opalj.br.TestSupport.createJREProject

            time { analyzeProject("JDK", project) } { t ⇒ info("the analysis took (real time)"+t.toSeconds) }

            printPerformanceData()
        }

        it("should be possible to calculate the information for all methods of the OPAL 0.3 snapshot") {
            DominatorsPerformanceEvaluation.resetAll()

            val classFiles = org.opalj.bi.TestSupport.locateTestResources("classfiles/OPAL-SNAPSHOT-0.3.jar", "bi")
            val project = Project(reader.ClassFiles(classFiles), Traversable.empty, true)

            time { analyzeProject("OPAL-0.3", project) } { t ⇒ info("the analysis took (real time)"+t.toSeconds) }

            printPerformanceData()
        }

        it("should be possible to calculate the information for all methods of the OPAL-08-14-2014 snapshot") {
            DominatorsPerformanceEvaluation.resetAll()

            val classFilesFolder = org.opalj.bi.TestSupport.locateTestResources("classfiles", "bi")
            val opalJARs = classFilesFolder.listFiles(new java.io.FilenameFilter() {
                def accept(dir: java.io.File, name: String) =
                    name.startsWith("OPAL-") && name.contains("SNAPSHOT-08-14-2014")
            })
            info(opalJARs.mkString("analyzing the following jars: ", ", ", ""))
            opalJARs.size should not be (0)
            val project = Project(AllClassFiles(opalJARs), Traversable.empty, true)

            time {
                analyzeProject("OPAL-08-14-2014 snapshot", project)
            } { t ⇒ info("the analysis took (real time) "+t.toSeconds) }

            printPerformanceData()
        }

    }
}
