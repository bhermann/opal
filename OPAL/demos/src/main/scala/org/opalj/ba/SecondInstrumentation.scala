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
import java.io.File
import java.util.ArrayList

import org.opalj.io.writeAndOpen
import org.opalj.util.InMemoryClassLoader
import org.opalj.da.ClassFileReader.ClassFile
import org.opalj.ai.BaseAI
import org.opalj.ai.domain.l0.TypeCheckingDomain
import org.opalj.bc.Assembler
import org.opalj.br.ObjectType
import org.opalj.br.ClassHierarchy
import org.opalj.br.MethodDescriptor.JustTakes
import org.opalj.br.MethodDescriptor.JustReturnsString
import org.opalj.br.analyses.Project
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.instructions.DUP
import org.opalj.br.instructions.GETSTATIC
import org.opalj.br.instructions.SWAP

/**
 * Demonstrates how to perform an instrumentation where we need more information about the code
 * (here, the (static) type of a value given to a method.
 *
 * @author Michael Eichberg
 */
object SecondInstrumentation extends App {

    val PrintStreamType = ObjectType("java/io/PrintStream")
    val SystemType = ObjectType("java/lang/System")
    val CollectionType = ObjectType("java/util/Collection")
    val PrintlnDescriptor = JustTakes(ObjectType.Object)

    val TheType = ObjectType("org/opalj/ba/SimpleInstrumentationDemo")

    // let's load the class
    val f = new File(this.getClass.getResource("SimpleInstrumentationDemo.class").getFile)
    val p = Project(f.getParentFile, org.opalj.bytecode.RTJar) // TODO: turn off LambdaExpressionsRewriting
    implicit val ch: ClassHierarchy = p.classHierarchy
    val cf = p.classFile(TheType).get
    // let's transform the methods
    val newVersion = bi.Java8Version
    val newMethods = for (m ← cf.methods) yield {
        m.body match {
            case None ⇒
                m.copy() // these are native and abstract methods

            case Some(code) ⇒
                // let's search all "println" calls where the parameter has a specific
                // type (which is statically known, and which is NOT the parameter type)
                lazy val aiResult = BaseAI(m, new TypeCheckingDomain(p, m))
                val operandsArray = aiResult.operandsArray
                val lCode = LabeledCode(code)
                var modified = false
                for {
                    (pc, INVOKEVIRTUAL(_, "println", PrintlnDescriptor)) ← code
                    param = operandsArray(pc).head
                    // if param.asDomainReferenceValue.valueType.get == CollectionType
                    if param.asDomainReferenceValue.isValueSubtypeOf(CollectionType).isYes
                } {
                    modified = true
                    lCode.insert(
                        pc, InsertionPosition.Before,
                        Seq(
                            DUP,
                            INVOKEVIRTUAL(ObjectType.Object, "toString", JustReturnsString),
                            GETSTATIC(SystemType, "out", PrintStreamType),
                            SWAP,
                            INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(ObjectType.String))
                        )
                    )
                }
                if (modified) {
                    val (newCode, _) = lCode.result(newVersion, m)
                    m.copy(body = Some(newCode))
                } else {
                    m.copy()
                }
        }
    }
    val newCF = cf.copy(methods = newMethods)
    val newRawCF = Assembler(toDA(newCF))

    //
    // THE FOLLOWING IS NOT RELATED TO BYTECODE MANIPULATION, BUT SHOWS ASPECTS OF OPAL WHICH ARE
    // HELPFUL WHEN DOING BYTECODE INSTRUMENTATION.
    //

    // Let's see the old class file...
    val oldDACF = ClassFile(() ⇒ p.source(TheType).get.openConnection().getInputStream).head
    println("original: "+writeAndOpen(oldDACF.toXHTML(None), "SimpleInstrumentationDemo", ".html"))

    // Let's see the new class file...
    val newDACF = ClassFile(() ⇒ new ByteArrayInputStream(newRawCF)).head
    val newCFHTML = writeAndOpen(newDACF.toXHTML(None), "NewSimpleInstrumentationDemo", ".html")
    println("instrumented: "+newCFHTML)

    // Let's test that the new class does what it is expected to do... (we execute the
    // instrumented method)
    val cl = new InMemoryClassLoader(Map((TheType.toJava, newRawCF)))
    val newClass = cl.findClass(TheType.toJava)
    val instance = newClass.newInstance()
    println("1. Example")
    newClass.getMethod("playingWithTypes", classOf[Object]).invoke(instance, new ArrayList[AnyRef]())
    println("2. Example")
    newClass.getMethod("playingWithTypes", classOf[Object]).invoke(instance, "data")

    println("End")
}
