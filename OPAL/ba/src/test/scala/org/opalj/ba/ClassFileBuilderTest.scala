/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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

import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.junit.runner.RunWith

import org.opalj.util.InMemoryClassLoader
import org.opalj.bi.ACC_FINAL
import org.opalj.bi.ACC_SYNTHETIC
import org.opalj.bi.ACC_SUPER
import org.opalj.bi.ACC_PUBLIC
import org.opalj.br.MethodDescriptor
import org.opalj.br.reader.Java8Framework.{ClassFile ⇒ ClassFileReader}
import org.opalj.bc.Assembler

/**
 * Tests general properties of a classes build with the BytecodeAssembler DSL by loading and
 * instantiating them with the JVM and doing a round trip `BRClassFile` -> `DAClassFile` ->
 * `Serialized Class File` -> `BRClassFile`.
 *
 * @author Malte Limmeroth
 */
@RunWith(classOf[JUnitRunner])
class ClassFileBuilderTest extends FlatSpec {

    behavior of "the ClassFileBuilder"

    val markerInterface1 = ABSTRACT + INTERFACE CLASS "MarkerInterface1"
    val markerInterface2 = ABSTRACT + INTERFACE CLASS "MarkerInterface2"

    val abstractClass = ABSTRACT + PUBLIC CLASS "org/opalj/bc/AbstractClass" EXTENDS "java/lang/Object"

    val simpleConcreteClass = (
        PUBLIC + SUPER + FINAL + SYNTHETIC CLASS "ConcreteClass"
        EXTENDS "org/opalj/bc/AbstractClass"
        IMPLEMENTS ("MarkerInterface1", "MarkerInterface2")
    ) Version (minorVersion = 2, majorVersion = 49)

    val abstractAsm = Assembler(abstractClass.buildDAClassFile._1)
    val concreteAsm = Assembler(simpleConcreteClass.buildDAClassFile._1)
    val abstractBRClassFile = ClassFileReader(() ⇒ new ByteArrayInputStream(abstractAsm)).head
    val concreteBRClassFile = ClassFileReader(() ⇒ new ByteArrayInputStream(concreteAsm)).head

    val loader = new InMemoryClassLoader(
        Map(
            "MarkerInterface1" → Assembler(markerInterface1.buildDAClassFile._1),
            "MarkerInterface2" → Assembler(markerInterface2.buildDAClassFile._1),
            "org.opalj.bc.AbstractClass" → abstractAsm,
            "ConcreteClass" → concreteAsm
        ),
        this.getClass.getClassLoader
    )
    import loader.loadClass

    "the generated classes" should "load correctly" in {
        assert("MarkerInterface1" == loadClass("MarkerInterface1").getSimpleName)
        assert("MarkerInterface2" == loadClass("MarkerInterface2").getSimpleName)
        assert("org.opalj.bc.AbstractClass" == loadClass("org.opalj.bc.AbstractClass").getName)
        assert("ConcreteClass" == loadClass("ConcreteClass").getSimpleName)
    }

    "the generated class 'ConcreteClass'" should "have only the generated default Constructor" in {
        val methods = concreteBRClassFile.methods
        assert(methods.size == 1)
        assert(methods.head.name == "<init>")
        assert(methods.head.descriptor == MethodDescriptor("()V"))
    }

    it should "be possible to create an instance" in {
        assert(loader.loadClass("ConcreteClass").newInstance() != null)
    }

    it should "extend org/opalj/bc/AbstractClass" in {
        assert(concreteBRClassFile.superclassType.get.fqn == "org/opalj/bc/AbstractClass")
    }

    it should "implement MarkerInterface1 and MarkerInterface2" in {
        assert(concreteBRClassFile.interfaceTypes.map(i ⇒ i.fqn).contains("MarkerInterface1"))
        assert(concreteBRClassFile.interfaceTypes.map(i ⇒ i.fqn).contains("MarkerInterface2"))
    }

    it should "be public final synthetic (super)" in {
        assert(concreteBRClassFile.accessFlags ==
            (ACC_PUBLIC.mask | ACC_FINAL.mask | ACC_SYNTHETIC.mask | ACC_SUPER.mask))
    }

    it should "have the specified minor version: 2" in {
        assert(concreteBRClassFile.minorVersion == 2)
    }

    it should "have the specified major version: 49" in {
        assert(concreteBRClassFile.majorVersion == 49)
    }

    "the generated class 'AbstractClass'" should "have the default minor version" in {
        assert(abstractBRClassFile.minorVersion == ClassFileBuilder.DefaultMinorVersion)
    }

    it should "have the default major version" in {
        assert(abstractBRClassFile.majorVersion == ClassFileBuilder.DefaultMajorVersion)
    }
}
