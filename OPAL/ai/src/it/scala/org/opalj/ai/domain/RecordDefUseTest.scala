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

import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.junit.runner.RunWith

import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._

import org.opalj.util.PerformanceEvaluation
import org.opalj.util.PerformanceEvaluation.time
import org.opalj.br.analyses.Project
import org.opalj.br.TestSupport.createJREProject
import org.opalj.br.Method
import org.opalj.br.reader.{BytecodeInstructionsCache, Java8FrameworkWithCaching}

/**
 * Tests if we are able to usefull self-consistent collect def/use information for the entire
 * test suite.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class RecordDefUseTest extends FunSpec with Matchers {

    object DominatorsPerformanceEvaluation extends PerformanceEvaluation

    private[this] class DefUseDomain[I](
            val method:  Method,
            val project: Project[URL]
    ) extends CorrelationalDomain
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
        with RecordDefUse // <=== we are going to test!

    def analyzeProject(name: String, project: Project[URL]): Unit = {
        info(s"$name contains ${project.methodsCount} methods")

        val identicalOrigins = new AtomicLong(0)
        val failures = new ConcurrentLinkedQueue[(String, Throwable)]

        val exceptions = project.parForeachMethodWithBody() { methodInfo ⇒
            val m = methodInfo.method
            try {
                val d = new DefUseDomain(m, project)
                val r = BaseAI(m, d)
                val dt = DominatorsPerformanceEvaluation.time('Dominators) { d.dominatorTree }
                val liveInstructions = r.evaluatedInstructions

                val code = m.body.get
                val instructions = code.instructions
                val codeSize = code.codeSize
                val ehs = code.exceptionHandlers

                // (1) TEST
                // Tests if the dominator tree information is consistent
                //
                liveInstructions.foreach(pc ⇒ if (pc != 0) dt.dom(pc) should be < codeSize)

                for {
                    (ops, pc) ← r.operandsArray.zipWithIndex
                    if ops ne null
                    instruction = instructions(pc)
                    if !instruction.isStackManagementInstruction
                } {
                    val usedOperands = instruction.numberOfPoppedOperands(NotRequired)

                    // (2) TEST
                    // Tests if the def => use information is consistent; i.e., a use lists
                    // the def site
                    //
                    val usedBy = d.usedBy(pc)
                    if (usedBy != null) {
                        // An instruction which pushes a value, is not necessarily a "valid"
                        // def-site which creates a new value.
                        // E.g. StackManagementInstructions, Checkcasts,
                        // LoadLocalVariableInstructions, but also INVOKE instructions
                        // of functions whose return value is ignored are not "def-sites".
                        usedBy foreach { useSite ⇒
                            // let's see if we have a corresponding use...
                            val useInstruction = instructions(useSite)
                            val poppedOperands = useInstruction.numberOfPoppedOperands(NotRequired)
                            val hasDefSite =
                                (0 until poppedOperands).exists { poIndex ⇒
                                    d.operandOrigin(useSite, poIndex).contains(pc)
                                } || {
                                    useInstruction.readsLocal &&
                                        d.localOrigin(useSite, useInstruction.indexOfReadLocal).contains(pc)
                                }
                            if (!hasDefSite) {
                                fail(s"the use site $useSite does not reference the def site $pc ($instruction)")
                            }
                        }
                    }

                    // Note that in case of handlers, the def/use information
                    // is slightly different when compared with the information recorded
                    // by the reference values domain.
                    if (ehs.forall(eh ⇒ eh.handlerPC != pc)) {
                        for { (op, opIndex) ← ops.toIterator.zipWithIndex } {
                            // (3) TEST
                            // Tests if the def/use information for reference values corresponds to the
                            // def/use information (implicitly) collected by the corresponding domain.
                            //
                            val domainOrigins = d.origin(op).toSet
                            val defUseOrigins =
                                try {
                                    d.operandOrigin(pc, opIndex)
                                } catch {
                                    case t: Throwable ⇒
                                        fail(s"pc=$pc[operand=$opIndex] no def/use info", t)
                                }
                            domainOrigins foreach { o ⇒
                                if (!(
                                    defUseOrigins.contains(o) ||
                                    defUseOrigins.exists(duo ⇒ ehs.exists(_.handlerPC == duo))
                                )) {
                                    val message =
                                        s"{pc=$pc[operand=$opIndex] deviating def/use info: "+
                                            s"domain=$domainOrigins vs defUse=$defUseOrigins}"
                                    fail(message)
                                }
                            }
                            identicalOrigins.incrementAndGet

                            // (4) TEST
                            // Tests if the use => def information is consistent; i.e., a def lists
                            // the (current) use site
                            //
                            // Only the operands that are used by the current instruction are
                            // expected to pop-up in the def-sites... and only if the instruction
                            // is a relevant use-site
                            if (opIndex < usedOperands &&
                                // we already tested: !instruction.isStackManagementInstruciton
                                !instruction.isStoreLocalVariableInstruction) {
                                defUseOrigins foreach { duo ⇒
                                    val useSites = d.usedBy(duo)
                                    if (!useSites.contains(pc)) {
                                        fail(
                                            s"a def site $duo(${instructions(duo)}) does not "+
                                                s"contain use site: $pc(${instructions(pc)})"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch { case t: Throwable ⇒ failures.add((m.toJava, t)) }
        }
        failures.addAll(exceptions.map(ex ⇒ ("additional exception", ex)).asJavaCollection)

        val baseMessage = s"origin information of ${identicalOrigins.get} values is identical"
        if (failures.size > 0) {
            val failureMessages = for { (failure, exception) ← failures.asScala } yield {
                var root: Throwable = exception
                while (root.getCause != null) root = root.getCause
                val location = {
                    val st = root.getStackTrace
                    if (st != null && st.length > 0) {
                        st.take(5).map { ste ⇒
                            s"${ste.getClassName}{ ${ste.getMethodName}:${ste.getLineNumber}}"
                        }.mkString("; ")
                    } else {
                        "<location unavailable>"
                    }
                }
                s"$failure[${root.getClass.getSimpleName}: ${root.getMessage}; location: $location]"
            }

            val errorMessageHeader = s"${failures.size} exceptions occured ($baseMessage) in:\n"
            fail(failureMessages.mkString(errorMessageHeader, "\n", "\n"))
        } else {
            info(baseMessage)
        }
    }

    //
    // TEST DRIVER
    //

    describe("computing def/use information") {

        val reader = new Java8FrameworkWithCaching(new BytecodeInstructionsCache)

        def evaluateProject(projectName: String, projectFactory: () ⇒ Project[URL]): Unit = {
            it(s"should be possible for all methods of $projectName") {
                DominatorsPerformanceEvaluation.resetAll()
                val project = projectFactory()
                time {
                    analyzeProject(projectName, project)
                } { t ⇒ info("the analysis took (real time): "+t.toSeconds) }
                val effort = DominatorsPerformanceEvaluation.getTime('Dominators).toSeconds
                info(s"computing dominator information took (CPU time): $effort")
            }
        }

        evaluateProject("the JDK", () ⇒ createJREProject)

        br.TestSupport.allBIProjects(reader, None) foreach { biProject ⇒
            val (projectName, projectFactory) = biProject
            evaluateProject(projectName, projectFactory)
        }
    }
}
