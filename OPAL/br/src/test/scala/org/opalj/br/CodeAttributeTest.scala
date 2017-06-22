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
package br

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.opalj.collection.immutable.IntSet
import org.opalj.collection.immutable.Naught

import org.opalj.br.reader.Java8Framework.ClassFiles
import org.opalj.br.analyses.Project
import org.opalj.br.instructions._

import org.opalj.bi.TestSupport.locateTestResources

/**
 * Tests some of the core methods of the Code attribute.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class CodeAttributeTest extends FlatSpec with Matchers {

    import CodeAttributeTest._

    behavior of "the \"Code\" attribute handlersFor method"

    it should "only report the most specific handler and not all handers" in {

        nestedCatch.handlersFor(5).toList should be(Iterable(nestedCatch.exceptionHandlers(0)))
        nestedCatch.handlersFor(14).toList should be(Iterable(nestedCatch.exceptionHandlers(1)))
        nestedCatch.handlersFor(10).toList should be(Iterable(nestedCatch.exceptionHandlers(2)))

        // the last instruction
        nestedCatch.handlersFor(37) should be(empty)
    }

    behavior of "the \"Code\" attribute's collect method"

    it should "be able to correctly collect all matching instructions" in {
        codeOfPut collect { case DUP ⇒ DUP } should equal(Seq((31, DUP)))

        codeOfPut collect {
            case ICONST_1 ⇒ ICONST_1
        } should equal(Seq((20, ICONST_1), (35, ICONST_1)))

        codeOfPut collect {
            case GETFIELD(declaringClass, "last", _) ⇒ declaringClass
        } should equal(Seq((17, boundedBufferClass), (45, boundedBufferClass)))

        codeOfPut collect {
            case RETURN ⇒ "The very last instruction."
        } should equal(Seq((54, "The very last instruction.")))

    }

    it should "be able to correctly handle the case if no instruction is matched" in {
        codeOfPut collect { case DUP2_X2 ⇒ DUP2_X2 } should equal(Seq())
    }

    behavior of "the \"Code\" attribute's collectWithIndex method"

    it should "be able to collect all jump targets" in {
        codeOfPut.collectWithIndex({
            case (pc, cbi: SimpleConditionalBranchInstruction) ⇒
                Seq(cbi.indexOfNextInstruction(pc)(codeOfPut), pc + cbi.branchoffset)
        }).flatten should equal(Seq(11, 15))
    }

    it should "be able to handle the case where no instruction is found" in {
        codeOfPut.collectWithIndex({ case (pc, IMUL) ⇒ (pc, IMUL) }) should equal(Naught)
    }

    behavior of "the \"Code\" attribute's collectFirstWithIndex method"

    it should "be able to correctly identify the first matching instruction" in {
        codeOfPut collectFirstWithIndex {
            case (pc, ICONST_1) ⇒ (pc, ICONST_1)
        } should equal(Some((20, ICONST_1)))
    }

    it should "be able to handle the case where no instruction is found" in {
        codeOfPut.collectFirstWithIndex({
            case (pc, IMUL) ⇒ (pc, IMUL)
        }) should be(None)
    }

    behavior of "the \"Code\" attribute's slidingCollect method"

    it should "be able to handle the case where the sliding window is too large compared to the number of instructions" in {
        codeOfPut.slidingCollect(500)({ case (pc, instrs) ⇒ (pc, instrs) }) should be(Seq())
    }

    it should "be able to find some consecutive instructions" in {
        codeOfPut.slidingCollect(2)({
            case (pc, Seq(ALOAD_0, ALOAD_0)) ⇒ (pc)
        }) should be(Seq(15))
    }

    it should "be able to find the last instructions" in {
        codeOfPut.slidingCollect(2)({
            case (pc, Seq(INVOKEVIRTUAL(_, _, _), RETURN)) ⇒ (pc)
        }) should be(Seq(51))
    }

    it should "be able to find multiple sequences of matching instructions" in {
        codeOfPut.slidingCollect(2)({
            case (pc, Seq(PUTFIELD(_, _, _), ALOAD_0)) ⇒ (pc)
        }) should be(Seq(27, 37))
    }

    behavior of "the \"Code\" attribute's associateWithIndex method"

    it should "be able to associate all instructions with the correct index" in {
        codeOfGet.associateWithIndex() should be(
            Seq(
                (0, ALOAD_0),
                (1, GETFIELD(immutbleListClass, "e", ObjectType.Object)),
                (4, ARETURN)
            )
        )
    }

    behavior of "the \"Code\" attribute's lookupLineNumber method"

    it should "be able to correctly extract the line number for the first instruction" in {
        codeOfConstructor.lineNumberTable.get.lookupLineNumber(0) should be(Some(47))
    }

    it should "be able to correctly extract the line number of some intermediate instruction" in {
        codeOfConstructor.lineNumberTable.get.lookupLineNumber(14) should be(Some(50))
    }

    it should "be able to correctly extract the line number of an instruction that is not directly associated with a line number" in {
        codeOfConstructor.lineNumberTable.get.lookupLineNumber(5) should be(Some(45))
    }

    it should "be able to correctly extract the line number of the last instruction" in {
        codeOfConstructor.lineNumberTable.get.lookupLineNumber(34) should be(Some(52))
    }

    behavior of "the \"Code\" attribute's firstLineNumber method"

    it should "be able to correctly extract the line number for the first instruction of aconstructor" in {
        codeOfConstructor.firstLineNumber should be(Some(45))
    }
    it should "be able to correctly extract the line number for the first instruction" in {
        codeOfPut.firstLineNumber should be(Some(57))
    }

    behavior of "the \"Code\" attribute's cfJoins method"

    it should "be able to correctly identify the instructions where multiple paths join" in {
        codeOfPut.cfJoins.size should be(1)
        codeOfPut.cfJoins should contain(15)
    }

    it should "be able to correctly identify the instructions where multiple paths join or fork" in {
        val (cfJoins, cfForks, forkTargetPCs) = codeOfPut.cfPCs()
        cfJoins.size should be(1)
        cfJoins should contain(15)
        cfForks.size should be(1)
        cfForks should contain(8)
        forkTargetPCs.size should be(1)
        forkTargetPCs(8) should be(IntSet(15, 11))
    }

    behavior of "the \"Code\" attribute's localVariableTable method"

    it should "return the local variable table" in {
        codeOfPut.localVariableTable should be('defined)
    }

    behavior of "the \"Code\" attribute's localVariableAt method"

    it should "return the local variables defined at the respective pc" in {
        val lvs = codeOfPut.localVariablesAt(32).map(e ⇒ (e._1, e._2.name))
        lvs should be(Map(0 → "this", 1 → "item"))
    }

    behavior of "the \"Code\" attribute's localVariable method"

    it should "be able to return the correct local variable" in {
        val thisVariable = LocalVariable(0, 55, "this", ObjectType("code/BoundedBuffer"), 0)
        codeOfPut.localVariable(32, 0) should be(Some(thisVariable))
        codeOfPut.localVariable(0, 0) should be(Some(thisVariable))
        codeOfPut.localVariable(54, 0) should be(Some(thisVariable))

        codeOfPut.localVariable(54, 1) should be(Some(LocalVariable(0, 55, "item", IntegerType, 1)))
    }

    it should "not crash if the index/pc does not map to a local variable definition" in {
        codeOfPut.localVariable(32, 2) should be(None)
    }

    behavior of "the \"Code\" attribute's pcOfPreviousInstruction method"

    it should "return the current pc - 1 if the previous instruction only occupies one slot in the code array" in {
        codeOfGet.pcOfPreviousInstruction(1) should be(0)
    }

    it should "return the pc of the previous instruction even if the previous instruction occupies multiple slots" in {
        codeOfGet.pcOfPreviousInstruction(4) should be(1)
    }

    it should "return a negative pc (which is invalid if the given pc is 0)" in {
        codeOfGet.pcOfPreviousInstruction(0) should be(-1)
    }

}
private object CodeAttributeTest {

    //
    //
    // Setup
    //
    //

    val project =
        Project(
            ClassFiles(locateTestResources("code.jar", "bi")) ++
                ClassFiles(locateTestResources("controlflow.jar", "bi")),
            Traversable.empty,
            true
        )

    val nestedCatch =
        project.
            classFile(ObjectType("controlflow/ExceptionCode")).get.
            methods.find(_.name == "nestedCatch").get.body.get

    val boundedBufferClass = ObjectType("code/BoundedBuffer")
    val immutbleListClass = ObjectType("code/ImmutableList")
    val quickSortClass = ObjectType("code/Quicksort")

    //
    //
    // Verify
    //
    //

    //PC  Line    Instruction
    //0   41  aload_0
    //1   |   invokespecial java.lang.Object{ <init> }
    //4   39  aload_0
    //5   |   iconst_0
    //6   |   putfield code.BoundedBuffer{ numberInBuffer : int }
    //9   43  aload_0
    //10  |   iload_1
    //11  |   putfield code.BoundedBuffer{ size : int }
    //14  44  aload_0
    //15  |   aload_0
    //16  |   getfield code.BoundedBuffer{ size : int }
    //19  |   newarray 10
    //21  |   putfield code.BoundedBuffer{ buffer : int[] }
    //24  45  aload_0
    //25  |   aload_0
    //26  |   iconst_0
    //27  |   dup_x1
    //28  |   putfield code.BoundedBuffer{ last : int }
    //31  |   putfield code.BoundedBuffer{ first : int }
    //44  46  return
    val codeOfConstructor =
        project.classFile(boundedBufferClass).get.methods.find(_.name == "<init>").get.body.get

    val codeOfPut =
        project.classFile(boundedBufferClass).get.methods.find(_.name == "put").get.body.get
    // The code of the "put" method is excepted to have the following bytecode:
    // Method descriptor #13 (I)V
    // Stack: 3, Locals: 2
    //  public void put(int item) throws java.lang.InterruptedException;
    //     0  aload_0 [this]
    //     1  getfield code.BoundedBuffer.numberInBuffer : int [18]
    //     4  aload_0 [this]
    //     5  getfield code.BoundedBuffer.size : int [20]
    //     8  if_icmpne 15
    //    11  aload_0 [this]
    //    12  invokevirtual java.lang.Object.wait() : void [37]
    //    15  aload_0 [this]
    //    16  aload_0 [this]
    //    17  getfield code.BoundedBuffer.last : int [24]
    //    20  iconst_1
    //    21  iadd
    //    22  aload_0 [this]
    //    23  getfield code.BoundedBuffer.size : int [20]
    //    26  irem
    //    27  putfield code.BoundedBuffer.last : int [24]
    //    30  aload_0 [this]
    //    31  dup
    //    32  getfield code.BoundedBuffer.numberInBuffer : int [18]
    //    35  iconst_1
    //    36  iadd
    //    37  putfield code.BoundedBuffer.numberInBuffer : int [18]
    //    40  aload_0 [this]
    //    41  getfield code.BoundedBuffer.buffer : int[] [22]
    //    44  aload_0 [this]
    //    45  getfield code.BoundedBuffer.last : int [24]
    //    48  iload_1 [item]
    //    49  iastore
    //    50  aload_0 [this]
    //    51  invokevirtual java.lang.Object.notifyAll() : void [40]
    //    54  return
    //      Line numbers:
    //        [pc: 0, line: 51]
    //        [pc: 11, line: 52]
    //        [pc: 15, line: 54]
    //        [pc: 30, line: 56]
    //        [pc: 40, line: 58]
    //        [pc: 50, line: 60]
    //        [pc: 54, line: 62]
    //      Local variable table:
    //        [pc: 0, pc: 55] local: this index: 0 type: code.BoundedBuffer
    //        [pc: 0, pc: 55] local: item index: 1 type: int
    //      Stack map table: number of frames 1
    //        [pc: 15, same]
    //}

    val codeOfGet = project.classFile(immutbleListClass).get.methods.find(_.name == "get").get.body.get
    // The code of get is as follows:
    // Method descriptor #30 ()Ljava/lang/Object;
    // Signature: ()TT;
    // Stack: 1, Locals: 1
    //  public java.lang.Object get();
    //    0  aload_0 [this]
    //    1  getfield code.ImmutableList.e : java.lang.Object [19]
    //    4  areturn
    //      Line numbers:
    //        [pc: 0, line: 58]
    //      Local variable table:
    //        [pc: 0, pc: 5] local: this index: 0 type: code.ImmutableList
    //      Local variable type table:
    //        [pc: 0, pc: 5] local: this index: 0 type: code.ImmutableList<T>
}
