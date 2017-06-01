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
package tac

import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import org.opalj.br._
import org.opalj.br.TestSupport.biProject
import org.opalj.tac.TACNaive.SimpleVar

/**
 * @author Michael Eichberg
 * @author Roberts Kolosovs
 */
@RunWith(classOf[JUnitRunner])
class ArrayTest extends TACTest {

    val ArrayInstructionsType = ObjectType("tactest/ArrayCreationAndManipulation")

    val project = biProject("tactest-8-preserveAllLocals.jar")

    val ArrayInstructionsClassFile = project.classFile(ArrayInstructionsType).get

    val RefArrayMethod = ArrayInstructionsClassFile.findMethod("refArray").head
    val MultidimArrayMethod = ArrayInstructionsClassFile.findMethod("multidimArray").head
    val DoubleArrayMethod = ArrayInstructionsClassFile.findMethod("doubleArray").head
    val FloatArrayMethod = ArrayInstructionsClassFile.findMethod("floatArray").head
    val IntArrayMethod = ArrayInstructionsClassFile.findMethod("intArray").head
    val LongArrayMethod = ArrayInstructionsClassFile.findMethod("longArray").head
    val ShortArrayMethod = ArrayInstructionsClassFile.findMethod("shortArray").head
    val ByteArrayMethod = ArrayInstructionsClassFile.findMethod("byteArray").head
    val CharArrayMethod = ArrayInstructionsClassFile.findMethod("charArray").head

    describe("the naive TAC of array creation and manipulation instructions") {

        def expectedAST(cTpe: ComputationalType, arrayType: ArrayType, const: Expr) = Array[Stmt](
            Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
            Assignment(0, SimpleVar(0, ComputationalTypeInt), IntConst(0, 5)),
            Assignment(1, SimpleVar(0, ComputationalTypeReference), NewArray(1, List(SimpleVar(0, ComputationalTypeInt)), arrayType)),
            Assignment(3, SimpleVar(-2, ComputationalTypeReference), SimpleVar(0, ComputationalTypeReference)),
            Assignment(4, SimpleVar(0, ComputationalTypeReference), SimpleVar(-2, ComputationalTypeReference)),
            Assignment(5, SimpleVar(1, ComputationalTypeInt), IntConst(5, 4)),
            Assignment(6, SimpleVar(2, cTpe), const),
            ArrayStore(7, SimpleVar(0, ComputationalTypeReference), SimpleVar(1, ComputationalTypeInt), SimpleVar(2, cTpe)),
            Assignment(8, SimpleVar(0, ComputationalTypeReference), SimpleVar(-2, ComputationalTypeReference)),
            Assignment(9, SimpleVar(1, ComputationalTypeInt), IntConst(9, 4)),
            Assignment(10, SimpleVar(0, cTpe), ArrayLoad(10, SimpleVar(1, ComputationalTypeInt), SimpleVar(0, ComputationalTypeReference))),
            Assignment(11, SimpleVar(-3, cTpe), SimpleVar(0, cTpe)),
            Return(12)
        )

        def expectedJLC(tpe: String, value: String) = Array[String](
            "0: r_0 = this;",
            "1: op_0 = 5;",
            "2: op_0 = new "+tpe+"[op_0];",
            "3: r_1 = op_0;",
            "4: op_0 = r_1;",
            "5: op_1 = 4;",
            "6: op_2 = "+value+";",
            "7: op_0[op_1] = op_2;",
            "8: op_0 = r_1;",
            "9: op_1 = 4;",
            "10: op_0 = op_0[op_1];",
            "11: r_2 = op_0;",
            "12: return;"
        )

        it("should correctly reflect reference array instructions") {
            val statements = TACNaive(method = RefArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(Array(
                Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
                Assignment(0, SimpleVar(0, ComputationalTypeInt), IntConst(0, 5)),
                Assignment(1, SimpleVar(0, ComputationalTypeReference), NewArray(1, List(SimpleVar(0, ComputationalTypeInt)), ArrayType.ArrayOfObjects)),
                Assignment(4, SimpleVar(-2, ComputationalTypeReference), SimpleVar(0, ComputationalTypeReference)),
                Assignment(5, SimpleVar(0, ComputationalTypeReference), SimpleVar(-2, ComputationalTypeReference)),
                Assignment(6, SimpleVar(1, ComputationalTypeInt), IntConst(6, 4)),
                Assignment(7, SimpleVar(2, ComputationalTypeReference), New(7, ObjectType.Object)),
                Nop(10),
                NonVirtualMethodCall(11, ObjectType.Object, false, "<init>", MethodDescriptor(IndexedSeq[FieldType](), VoidType), SimpleVar(2, ComputationalTypeReference), List()),
                ArrayStore(14, SimpleVar(0, ComputationalTypeReference), SimpleVar(1, ComputationalTypeInt), SimpleVar(2, ComputationalTypeReference)),
                Assignment(15, SimpleVar(0, ComputationalTypeReference), SimpleVar(-2, ComputationalTypeReference)),
                Assignment(16, SimpleVar(1, ComputationalTypeInt), IntConst(16, 4)),
                Assignment(17, SimpleVar(0, ComputationalTypeReference), ArrayLoad(17, SimpleVar(1, ComputationalTypeInt), SimpleVar(0, ComputationalTypeReference))),
                Assignment(18, SimpleVar(-3, ComputationalTypeReference), SimpleVar(0, ComputationalTypeReference)),
                Return(19)
            ))
            javaLikeCode.shouldEqual(Array(
                "0: r_0 = this;",
                "1: op_0 = 5;",
                "2: op_0 = new java.lang.Object[op_0];",
                "3: r_1 = op_0;",
                "4: op_0 = r_1;",
                "5: op_1 = 4;",
                "6: op_2 = new Object;",
                "7: ;",
                "8: op_2/* (Non-Virtual) java.lang.Object*/.<init>();",
                "9: op_0[op_1] = op_2;",
                "10: op_0 = r_1;",
                "11: op_1 = 4;",
                "12: op_0 = op_0[op_1];",
                "13: r_2 = op_0;",
                "14: return;"
            ))
        }

        it("should correctly reflect multidimensional array instructions") {
            val statements = TACNaive(method = MultidimArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(Array(
                Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
                Assignment(0, SimpleVar(0, ComputationalTypeInt), IntConst(0, 4)),
                Assignment(1, SimpleVar(1, ComputationalTypeInt), IntConst(1, 2)),
                Assignment(2, SimpleVar(0, ComputationalTypeReference),
                    NewArray(2, List(SimpleVar(1, ComputationalTypeInt), SimpleVar(0, ComputationalTypeInt)), ArrayType(ArrayType(IntegerType)))),
                Assignment(6, SimpleVar(-2, ComputationalTypeReference), SimpleVar(0, ComputationalTypeReference)),
                Assignment(7, SimpleVar(0, ComputationalTypeReference), SimpleVar(-2, ComputationalTypeReference)),
                Assignment(8, SimpleVar(0, ComputationalTypeInt), ArrayLength(8, SimpleVar(0, ComputationalTypeReference))),
                Assignment(9, SimpleVar(-3, ComputationalTypeInt), SimpleVar(0, ComputationalTypeInt)),
                Return(10)
            ))
            javaLikeCode.shouldEqual(Array(
                "0: r_0 = this;",
                "1: op_0 = 4;",
                "2: op_1 = 2;",
                "3: op_0 = new int[op_0][op_1];",
                "4: r_1 = op_0;",
                "5: op_0 = r_1;",
                "6: op_0 = op_0.length;",
                "7: r_2 = op_0;",
                "8: return;"
            ))
        }

        it("should correctly reflect double array instructions") {
            val statements = TACNaive(method = DoubleArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeDouble, ArrayType(DoubleType), DoubleConst(6, 1.0d)))
            javaLikeCode.shouldEqual(expectedJLC("double", "1.0d"))
        }

        it("should correctly reflect float array instructions") {
            val statements = TACNaive(method = FloatArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeFloat, ArrayType(FloatType), FloatConst(6, 2.0f)))
            javaLikeCode.shouldEqual(expectedJLC("float", "2.0f"))
        }

        it("should correctly reflect int array instructions") {
            val statements = TACNaive(method = IntArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeInt, ArrayType(IntegerType), IntConst(6, 2)))
            javaLikeCode.shouldEqual(expectedJLC("int", "2"))
        }

        it("should correctly reflect long array instructions") {
            val statements = TACNaive(method = LongArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeLong, ArrayType(LongType), LongConst(6, 1)))
            javaLikeCode.shouldEqual(expectedJLC("long", "1l"))
        }

        it("should correctly reflect short array instructions") {
            val statements = TACNaive(method = ShortArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeInt, ArrayType(ShortType), IntConst(6, 2)))
            javaLikeCode.shouldEqual(expectedJLC("short", "2"))
        }

        it("should correctly reflect byte array instructions") {
            val statements = TACNaive(method = ByteArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeInt, ArrayType(ByteType), IntConst(6, 2)))
            javaLikeCode.shouldEqual(expectedJLC("byte", "2"))
        }

        it("should correctly reflect char array instructions") {
            val statements = TACNaive(method = CharArrayMethod, classHierarchy = Code.BasicClassHierarchy)._1
            val javaLikeCode = ToJavaLike(statements, false)

            assert(statements.nonEmpty)
            assert(javaLikeCode.length > 0)
            statements.shouldEqual(expectedAST(ComputationalTypeInt, ArrayType(CharType), IntConst(6, 2)))
            javaLikeCode.shouldEqual(expectedJLC("char", "2"))
        }
    }
}
