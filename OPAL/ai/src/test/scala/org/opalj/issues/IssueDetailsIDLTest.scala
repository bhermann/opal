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
package issues

import scala.reflect.ClassTag

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner

import play.api.libs.json.Json

import org.opalj.collection.immutable.Chain
import org.opalj.collection.mutable.Locals
import org.opalj.br.Code
import org.opalj.br.FieldType
import org.opalj.br.LocalVariable
import org.opalj.br.LocalVariableTable
import org.opalj.br.instructions.ATHROW
import org.opalj.br.instructions.DADD
import org.opalj.br.instructions.DUP
import org.opalj.br.instructions.DUP2
import org.opalj.br.instructions.IF_ICMPEQ
import org.opalj.br.instructions.IINC
import org.opalj.br.instructions.LOOKUPSWITCH
import org.opalj.br.instructions.NOP

/**
 * Tests the toIDL method of IssueDetails
 *
 * @author Lukas Berg
 */
@RunWith(classOf[JUnitRunner])
class IssueDetailsIDLTest extends FlatSpec with Matchers {

    import IDLTestsFixtures._

    behavior of "the toIDL method"

    it should "return a valid issue description if there are no LocalVariables" in {
        simpleLocalVariables.toIDL should be(simpleLocalVariablesIDL)
    }

    it should "return a valid issue description if we have a single int typed LocalVariable" in {
        val localVariable = LocalVariable(0, 1, "foo", FieldType("I"), 0)
        val code = Code(0, 0, null, IndexedSeq.empty, Array(LocalVariableTable(Array(localVariable))))
        val localVariables = new LocalVariables(code, 0, Locals(IndexedSeq(ClassTag.Int)))

        localVariables.toIDL should be(Json.obj(
            "type" → "LocalVariables",
            "values" → Json.arr(
                Json.obj(
                    "name" → "foo",
                    "value" → "Int"
                )
            )
        ))
    }

    it should "return a valid issue description if we have an int and double LocalVariable" in {
        val localVariable = LocalVariable(0, 1, "foo", FieldType("I"), 0)
        val localVariable2 = LocalVariable(0, 1, "bar", FieldType("I"), 1)
        val arrLocalVariable = Array(localVariable2, localVariable)
        val code = Code(0, 0, null, IndexedSeq.empty, Array(LocalVariableTable(arrLocalVariable)))
        val localVariables = new LocalVariables(code, 0, Locals(IndexedSeq(ClassTag.Int, ClassTag.Double)))

        localVariables.toIDL should be(Json.obj(
            "type" → "LocalVariables",
            "values" → Json.arr(
                Json.obj(
                    "name" → "foo",
                    "value" → "Int"
                ), Json.obj(
                    "name" → "bar",
                    "value" → "Double"
                )
            )
        ))
    }

    it should "return a valid issue description for the Operand of a SimpleConditionalBranchInstruction with one operand" in {
        simpleOperands.toIDL should be(simpleOperandsIDL)
    }

    it should "return a valid issue description for the Operands of a SimpleConditionalBranchInstruction with two operands" in {
        val instruction = new IF_ICMPEQ(0)
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("1", "2"), null)

        operands.toIDL should be(Json.obj(
            "type" → "SimpleConditionalBranchInstruction",
            "operator" → "==",
            "value" → "2",
            "value2" → "1"
        ))
    }

    it should "return a valid issue description for the Operands of a CompoundConditionalBranchInstruction with a single case" in {
        val instruction = LOOKUPSWITCH(0, IndexedSeq((0, 1)))
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo"), null)

        operands.toIDL should be(Json.obj(
            "type" → "CompoundConditionalBranchInstruction",
            "value" → "foo",
            "caseValues" → "0"
        ))
    }

    it should "return a valid issue description for the Operands of a CompoundConditionalBranchInstruction with two cases" in {
        val instruction = LOOKUPSWITCH(0, IndexedSeq((0, 1), (2, 3)))
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo", "bar"), null)

        operands.toIDL should be(Json.obj(
            "type" → "CompoundConditionalBranchInstruction",
            "value" → "foo",
            "caseValues" → "0, 2"
        ))
    }

    it should "return a valid issue description for the Operands of a StackManagementInstruction with a single operand" in {
        val instruction = DUP
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo"), null)

        operands.toIDL should be(Json.obj(
            "type" → "StackManagementInstruction",
            "mnemonic" → DUP.mnemonic,
            "values" → Json.arr("foo")
        ))
    }

    it should "return a valid issue description for the Operands of a StackManagementInstruction with 2 operands" in {
        val instruction = DUP2
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo", "bar"), null)

        operands.toIDL should be(Json.obj(
            "type" → "StackManagementInstruction",
            "mnemonic" → DUP2.mnemonic,
            "values" → Json.arr("foo", "bar")
        ))
    }

    it should "return a valid issue description for the Locals accessed by an IINC(0)" in {
        val instruction = IINC(0, 1)
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, null, Locals(IndexedSeq(ClassTag.Int)))

        operands.toIDL should be(Json.obj(
            "type" → "IINC",
            "value" → ClassTag.Int.toString,
            "constValue" → 1
        ))
    }

    it should "return a valid issue description for the Locals accessed by an IINC(1)" in {
        val instruction = IINC(1, 0)
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, null, Locals(IndexedSeq(ClassTag.Short, ClassTag.Int)))

        operands.toIDL should be(Json.obj(
            "type" → "IINC",
            "value" → ClassTag.Int.toString,
            "constValue" → 0
        ))
    }

    it should "return a valid issue description for an instruction without operands" in {
        val code = Code(0, 0, Array(NOP))
        val operands = new Operands(code, 0, Chain("foo"), null)

        operands.toIDL should be(Json.obj(
            "type" → NOP.getClass.getSimpleName,
            "mnemonic" → NOP.mnemonic,
            "parameters" → Json.arr()
        ))
    }

    it should "return a valid issue description for the operands of an athrow instruction" in {
        val instruction = ATHROW
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo", "bar"), null)

        operands.toIDL should be(Json.obj(
            "type" → ATHROW.getClass.getSimpleName,
            "mnemonic" → ATHROW.mnemonic,
            "parameters" → Json.arr("foo")
        ))
    }

    it should "return a valid issue description for the operands of some arithmetic instruction with 2 operands" in {
        val instruction = DADD
        val code = Code(0, 0, Array(instruction))
        val operands = new Operands(code, 0, Chain("foo", "bar", "baz"), null)

        operands.toIDL should be(Json.obj(
            "type" → DADD.getClass.getSimpleName,
            "mnemonic" → DADD.mnemonic,
            "parameters" → Json.arr("bar", "foo")
        ))
    }

    //TODO implement tests for FieldValues

    //TODO implement tests for MethodReturnValues
}

