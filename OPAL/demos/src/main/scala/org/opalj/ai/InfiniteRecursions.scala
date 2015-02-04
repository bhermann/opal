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

import java.net.URL
import scala.Console.BLUE
import scala.Console.BOLD
import scala.Console.RESET
import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION
import scala.language.existentials
import org.opalj.br.ClassFile
import org.opalj.br.Method
import org.opalj.br.MethodWithBody
import org.opalj.br.analyses.AnalysisExecutor
import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.OneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.SomeProject
import org.opalj.br.instructions.INVOKEINTERFACE
import org.opalj.br.instructions.INVOKESPECIAL
import org.opalj.br.instructions.INVOKESTATIC
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.instructions.NEW

/**
 * An analysis that finds self-recursive calls with unchanged parameters.
 *
 * @author Marco Jacobasch
 * @author Michael Eichberg
 */
object InfiniteRecursions extends AnalysisExecutor with OneStepAnalysis[URL, BasicReport] {

    val analysis = this

    override def title: String =
        "infinite recursions analysis"

    override def description: String =
        "identifies method which calls themselves using infinite recursion"

    override def doAnalyze(
        project: Project[URL],
        parameters: Seq[String] = List.empty,
        isInterrupted: () ⇒ Boolean) = {

        // TODO read from parameter
        val maxRecursionDepth = 3

        val result =
            // for every method that calls itself ...
            for {
                classFile ← project.allClassFiles.par
                method @ MethodWithBody(body) ← classFile.methods
                descriptor = method.descriptor
                if descriptor.parameterTypes.forall { t ⇒
                    // we don't have (as of Jan 1st 2015) a domain that enables a meaningful
                    // tracking of Float and Double values
                    t.isReferenceType || t.isLongType || t.isIntegerType
                }
                classType = classFile.thisType
                name = method.name
                pcs = body.collectWithIndex {
                    case (pc, INVOKEVIRTUAL(`classType`, `name`, `descriptor`))   ⇒ pc
                    case (pc, INVOKESTATIC(`classType`, `name`, `descriptor`))    ⇒ pc
                    case (pc, INVOKESPECIAL(`classType`, `name`, `descriptor`))   ⇒ pc
                    case (pc, INVOKEINTERFACE(`classType`, `name`, `descriptor`)) ⇒ pc
                }
                if pcs.nonEmpty
                result ← inifiniteRecursions(
                    maxRecursionDepth,
                    project, classFile, method,
                    pcs)
            } yield { result }

        BasicReport(result.map(_.toString).mkString("\n"))
    }

    /**
     * Perform an abstract interpretation and check if (after some stabilization time)
     * the parameters to the recursive call are unchanged.
     *
     * `maxRecursionDepth` determines after how many non-recursive calls the analysis
     * is aborted.
     */
    def inifiniteRecursions(
        maxRecursionDepth: Int,
        project: SomeProject,
        classFile: ClassFile,
        method: Method,
        pcs: Seq[PC]): Option[InfiniteRecursion] = {

        assert(maxRecursionDepth > 1)
        assert(pcs.toSet.size == pcs.size, s"the seq $pcs contains duplicates")

        val strictfp = method.isStrict
        val body = method.body.get
        val parametersCount =
            method.descriptor.parametersCount + (if (method.isStatic) 0 else 1)

        // we are always analyzing the same method, hence, we can reuse the same domain
        // for all "Abstract Interpretations"
        val domain = new InfiniteRecursionsDomain(project, method)
        import domain.Operands

        var previousCallOperandsList: Seq[Operands] = Seq.empty

        def reduceCallOperands(operandsArray: domain.OperandsArray): Seq[Operands] = {
            var callOperandsList: List[Operands] = List.empty
            for {
                pc ← pcs
                if operandsArray(pc) ne null
                nextCallOperands: domain.Operands = operandsArray(pc).take(parametersCount)
            } {
                // IntegerRangeValues and ReferenceValues have useable equals semantics
                if (!callOperandsList.exists { _ == nextCallOperands })
                    callOperandsList = nextCallOperands :: callOperandsList
            }
            callOperandsList
        }

        // initialize callOperandsList by doing a first abstract interpretation
        val initialOperandsArray = BaseAI(classFile, method, domain).operandsArray
        previousCallOperandsList = reduceCallOperands(initialOperandsArray)

        def analyze(depth: Int, callOperands: Operands): Option[InfiniteRecursion] = {
            if (depth > maxRecursionDepth)
                return None;

            val parameters = mapOperandsToParameters(callOperands, method, domain)
            val aiResult =
                BaseAI.performInterpretation(
                    strictfp, body, domain)(
                        List.empty, parameters)
            val operandsArray = aiResult.operandsArray
            val localsArray = aiResult.localsArray
            val callOperandsList =
                reduceCallOperands(operandsArray) filter { callOperands ⇒
                    if (previousCallOperandsList.contains(callOperands)) {

                        // let's check if we have a potential recursive call...
                        // i.e., if we can track back the operands to parameters
                        // concrete (fixed) values or values that are always created
                        // in the same manner; the idea is to reduce false positives
                        // due to non-infinite recursions due to side effects
                        if (callOperands.forall {
                            case domain.DomainSingleOriginReferenceValue(v) ⇒
                                if (v.origin < 0 /* === the value is a parameter*/ ||
                                    // the value is always created anew (no sideeffect)
                                    body.instructions(v.origin).opcode == NEW.opcode)
                                    true
                                else
                                    false
                            case v: domain.AnIntegerValue ⇒
                                if (localsArray(0).exists(_ eq v))
                                    true // the value is parameter
                                else
                                    false
                            case v: domain.ALongValue ⇒
                                if (localsArray(0).exists(_ eq v))
                                    true // the value is parameter
                                else
                                    false
                            case _: domain.LongSet      ⇒ true
                            case _: domain.IntegerRange ⇒ true
                            case _                      ⇒ false
                        })
                            return Some(InfiniteRecursion(classFile, method, callOperands));

                        // these operands are not relevant...
                        false
                    } else {
                        true
                    }
                }

            callOperandsList foreach { callOperands ⇒
                val result = analyze(depth + 1, callOperands)
                if (result.nonEmpty)
                    return result;
            }
            None
        }

        previousCallOperandsList foreach { callOperands ⇒
            val result = analyze(0, callOperands)
            if (result.nonEmpty)
                return result;
        }
        // no recursion...
        None
    }

}

class InfiniteRecursionsDomain(val project: SomeProject, val method: Method)
    extends Domain
    with domain.DefaultDomainValueBinding
    with domain.ThrowAllPotentialExceptionsConfiguration
    with domain.l0.DefaultTypeLevelFloatValues
    with domain.l0.DefaultTypeLevelDoubleValues
    with domain.l0.TypeLevelFieldAccessInstructions
    with domain.l0.TypeLevelInvokeInstructions
    with domain.l1.DefaultReferenceValuesBinding
    with domain.l1.DefaultIntegerRangeValues
    with domain.l1.MaxArrayLengthRefinement
    with domain.l1.ConstraintsBetweenIntegerValues
    //with domain.l1.DefaultIntegerSetValues
    with domain.l1.DefaultLongSetValues
    with domain.l1.LongSetValuesShiftOperators
    with domain.l1.ConcretePrimitiveValuesConversions
    with domain.DefaultHandlingOfMethodResults
    with domain.IgnoreSynchronization
    with domain.TheProject
    with domain.TheMethod

case class InfiniteRecursion(
        classFile: ClassFile,
        method: Method,
        operands: Iterable[_]) {

    override def toString: String = {
        val declaringClassOfMethod = classFile.thisType.toJava

        "infinite recursion in "+BOLD + BLUE +
            declaringClassOfMethod + RESET+
            "{ "+method.toJava+"{ "+operands.mkString(", ")+" }}"
    }
}

