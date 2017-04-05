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
package ba

import java.io.ByteArrayInputStream

import reflect.runtime.universe._
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

import org.opalj.bc.Assembler
import org.opalj.bi.ACC_FINAL
import org.opalj.bi.ACC_PRIVATE
import org.opalj.bi.ACC_PUBLIC
import org.opalj.br.instructions._
import org.opalj.br.reader.Java8Framework
import org.opalj.util.InMemoryClassLoader

/**
 * Tests the properties of fields build with the BytecodeAssembler DSL. The class is build,
 * assembled as a [[org.opalj.da.ClassFile]] and read again as a [[org.opalj.br.ClassFile]]. It is
 * also loaded, instantiated and executed with the JVM.
 *
 * @author Malte Limmeroth
 */
@RunWith(classOf[JUnitRunner])
class FieldBuilderTest extends FlatSpec {
    behavior of "Fields"
    val cf = (PUBLIC CLASS "org/opalj/ba/FieldClass" EXTENDS "java/lang/Object")(
        FINAL + PUBLIC("publicField", "I") CONSTANTVALUE 3,
        PRIVATE("privateField", "Z") DEPRECATED () SYNTHETIC (),
        PUBLIC("<init>", "()", "V")(
            CODE(
                ALOAD_0,
                INVOKESPECIAL("java/lang/Object", false, "<init>", "()V"),
                ALOAD_0,
                ICONST_3,
                PUTFIELD("org/opalj/ba/FieldClass", "publicField", "I"),
                ALOAD_0,
                ICONST_1,
                PUTFIELD("org/opalj/ba/FieldClass", "privateField", "Z"),
                RETURN
            )
        ),
        PUBLIC("packageField", "()", "Z")(
            CODE(
                ALOAD_0,
                GETFIELD("org/opalj/ba/FieldClass", "privateField", "Z"),
                IRETURN
            )
        ),
        PUBLIC("publicField", "()", "I")(
            CODE(
                ALOAD_0,
                GETFIELD("org/opalj/ba/FieldClass", "publicField", "I"),
                IRETURN
            )
        )
    )

    val assembledCF = Assembler(cf.buildDAClassFile._1)

    val loader = new InMemoryClassLoader(
        Map("org.opalj.ba.FieldClass" → assembledCF),
        this.getClass.getClassLoader
    )

    val fieldInstance = loader.loadClass("org.opalj.ba.FieldClass").newInstance()
    val mirror = runtimeMirror(loader).reflect(fieldInstance)

    val brClassFile = Java8Framework.ClassFile(() ⇒ new ByteArrayInputStream(assembledCF)).head

    def getField(name: String) = brClassFile.fields.find(f ⇒ f.name == name).get

    "the fields in `FieldClass`" should "have the correct visibility modifiers" in {
        assert(getField("privateField").accessFlags == ACC_PRIVATE.mask)
        assert(getField("publicField").accessFlags == (ACC_PUBLIC.mask | ACC_FINAL.mask))
    }

    it should "have the ConstantValue attribute set to: 3" in {
        val constant = getField("publicField").attributes.collect { case c: br.ConstantInteger ⇒ c }
        assert(constant.head.value == 3)
    }

    "the field `FieldClass.privateField`" should "be initialized as true" in {
        val field = mirror.symbol.typeSignature.member(TermName("privateField")).asTerm
        assert(mirror.reflectField(field).get == true)
    }

    it should "have the Deprecated Attribute set" in {
        assert(getField("privateField").attributes.exists(a ⇒ a.kindId == 22))
    }

    it should "have the Synthetic attribute" in {
        assert(getField("privateField").attributes.exists(a ⇒ a.kindId == 11))
    }

    "FieldClass.publicField" should "be initialized as 3" in {
        val field = mirror.symbol.typeSignature.member(TermName("publicField")).asTerm
        assert(mirror.reflectField(field).get == 3)
    }

}
