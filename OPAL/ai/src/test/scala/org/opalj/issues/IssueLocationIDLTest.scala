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

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner

import play.api.libs.json.JsNull
import play.api.libs.json.Json

import org.opalj.bi.ACC_PUBLIC
import org.opalj.bi.ACC_STATIC
import org.opalj.br.IntegerType
import org.opalj.br.Method
import org.opalj.br.ObjectType

/**
 * Tests toIDL method of IssueLocation
 *
 * @author Lukas Berg
 */
@RunWith(classOf[JUnitRunner])
class IssueLocationIDLTest extends FlatSpec with Matchers {

    import IDLTestsFixtures._

    behavior of "the toIDL method"

    it should "return a valid issue description for a basic PackageLocation" in {
        simplePackageLocation.toIDL should be(simplePackageLocationIDL)
    }

    it should "return a valid issue description for a PackageLocation with details" in {
        val packageLocation = new PackageLocation(
            Option("bar"), null, "baz", Seq(simpleOperands, simpleLocalVariables)
        )

        packageLocation.toIDL should be(Json.obj(
            "description" → "bar",
            "location" → Json.obj("package" → "baz"),
            "details" → Json.arr(simpleOperandsIDL, simpleLocalVariablesIDL)
        ))
    }

    it should "return a valid issue description for a basic ClassLocation" in {
        val classLocation = new ClassLocation(Option("baz"), null, classFile)

        classLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for a ClassLocation with details" in {
        val classLocation = new ClassLocation(
            Option("baz"), null, classFile, Seq(simpleOperands, simpleLocalVariables)
        )

        classLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL
            ),
            "details" → Json.arr(simpleOperandsIDL, simpleLocalVariablesIDL)
        ))
    }

    it should "return a valid issue description for a method with no parameters and no return value" in {
        val methodLocation = new MethodLocation(
            Option("baz"), null, classFile, methodReturnVoidNoParameters
        )

        methodLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnVoidNoParametersIDL
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for a method with two int parameters and which returns int values" in {
        val methodLocation = new MethodLocation(
            Option("baz"), null, classFile, methodReturnIntTwoParameters
        )

        methodLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnIntTwoParametersIDL
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for a method which returns ints and declares one parameter" in {
        val method = Method(ACC_PUBLIC.mask | ACC_STATIC.mask, "test", IndexedSeq(ObjectType("foo/Bar")), IntegerType)
        val methodLocation = new MethodLocation(Option("baz"), null, classFile, method)

        methodLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → Json.obj(
                    "accessFlags" → "public static",
                    "name" → "test",
                    "returnType" → Json.obj(
                        "bt" → "int"
                    ),
                    "parameters" → Json.arr(Json.obj(
                        "ot" → "foo.Bar",
                        "simpleName" → "Bar"
                    )),
                    "signature" → "test(Lfoo/Bar;)I",
                    "firstLine" → JsNull
                )
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for a method with two int parameters and which returns int values and which has further details" in {
        val methodLocation = new MethodLocation(
            Option("baz"),
            null,
            classFile,
            methodReturnIntTwoParameters,
            Seq(simpleOperands, simpleLocalVariables)
        )

        methodLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnIntTwoParametersIDL
            ),
            "details" → Json.arr(simpleOperandsIDL, simpleLocalVariablesIDL)
        ))
    }

    it should "return a valid issue description for an InstructionLocation in a method without parameters which returns nothing" in {
        val instructionLocation = new InstructionLocation(
            Option("baz"), null, classFile, methodReturnVoidNoParameters, 42
        )

        instructionLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnVoidNoParametersIDL,
                "instruction" → Json.obj(
                    "pc" → 42
                )
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for InstructionLocation with int return and 2 parameters" in {
        val instructionLocation = new InstructionLocation(
            Some("baz"), null, classFile, methodReturnIntTwoParameters, 42
        )

        instructionLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnIntTwoParametersIDL,
                "instruction" → Json.obj(
                    "pc" → 42,
                    "line" → 10
                )
            ),
            "details" → Json.arr()
        ))
    }

    it should "return a valid issue description for InstructionLocation with int return, 2 parameters and details" in {
        val instructionLocation = new InstructionLocation(
            Some("baz"),
            null,
            classFile,
            methodReturnIntTwoParameters,
            42,
            Seq(simpleOperands, simpleLocalVariables)
        )

        instructionLocation.toIDL should be(Json.obj(
            "description" → "baz",
            "location" → Json.obj(
                "package" → "foo",
                "class" → classFileIDL,
                "method" → methodReturnIntTwoParametersIDL,
                "instruction" → Json.obj(
                    "pc" → 42,
                    "line" → 10
                )
            ),
            "details" → Json.arr(simpleOperandsIDL, simpleLocalVariablesIDL)
        ))
    }
}
