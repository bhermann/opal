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
package org.opalj.fpcf.analysis

import org.opalj.br._
import org.opalj.br.analyses.{PropertyStoreKey, SomeProject}
import org.opalj.br.instructions.NEW
import org.opalj.collection.immutable.Chain
import org.opalj.fpcf.PropertyKey
import org.opalj.fpcf.properties.{EscapeProperty, NoEscape}

class SimpleEscapeAnalysisTest extends AbstractFixpointAnalysisTest {

    def analysisName = "SimpleEscapeAnalysis"

    override def testFileName = "escape-1.8-g-parameters-genericsignature.jar"

    override def testFilePath = "bi"

    override def analysisRunner = SimpleEscapeAnalysis

    override def propertyKey: PropertyKey[EscapeProperty] = EscapeProperty.key

    override def propertyAnnotation: ObjectType = {
        ObjectType("annotations/target/EscapeProperty")
    }

    /**
      * Add all AllocationsSites found in the project to the entities in the property
      * stores created with the PropertyStoreKey.
      */
    override def init(): Unit = {
        PropertyStoreKey.addEntityDerivationFunction[Map[Method, Map[PC, AllocationSite]]](
            (p: SomeProject) ⇒ {
                var allAs: List[Chain[AllocationSite]] = Nil

                // Associate every new instruction in a method with an allocation site object
                val as = p.allMethods.flatMap { m ⇒
                    m.body match {
                        case None ⇒ Nil
                        case Some(code) ⇒
                            val as = code.collectWithIndex {
                                case (pc, instructions.NEW(_)) ⇒ new AllocationSite(m, pc)
                            }
                            if (as.nonEmpty) allAs ::= as
                            as
                    }
                }

                // In the context we store a map which makes the set of allocation sites
                // easily accessible
                val mToPCToAs = allAs.map { asPerMethod ⇒
                    val pcToAs = asPerMethod.map(as ⇒ as.pc → as).toMap
                    val m = pcToAs.head._2.method
                    m → pcToAs
                }.toMap

                (as, mToPCToAs)
            }
        )
    }

    def defaultValue = NoEscape.toString

    def propertyExtraction(annotation: TypeAnnotation): Option[String] = {
        annotation.elementValuePairs collectFirst { case ElementValuePair("value", EnumValue(_, property)) ⇒ property }
    }

    def validateProperyByTypeAnnotation(method: Method, annotation: TypeAnnotation): Unit = {
        val instructions = method.body.get.instructions
        val annotatedOProperty = propertyExtraction(annotation)
        val annotatedProperty = annotatedOProperty getOrElse defaultValue

        assert(method ne null, "method is empty")

        val expr = annotation.target match {
            case TAOfNew(pc) ⇒ {
                instructions(pc) match {
                    case NEW(_) ⇒ Some(AllocationSite(method, pc))
                }
            }
            case TAOfFormalParameter(index) ⇒
                throw new RuntimeException("Not yet implemented")
            case _ ⇒ None
        }

        expr.foreach(entity => {
            val computedOProperty = propertyStore(entity, propertyKey)

            if (computedOProperty.hasNoProperty) {
                val className = project.classFile(method).fqn
                val message =
                    "Entity has no property: " + s"$className $method $entity  for: $propertyKey;" +
                        s"\nexpected property: $annotatedProperty"
                fail(message)
            }

            val computedProperty = computedOProperty.p.toString

            if (computedProperty != annotatedProperty) {
                val className = project.classFile(method).fqn
                val message =
                    "Wrong property computed: " +
                        s"$className $method $entity" +
                        s"has the property $computedProperty for $propertyKey;" +
                        s"\n\tactual property:   $computedProperty" +
                        s"\n\texpected property: $annotatedProperty"
                fail(message)
            }

        })
    }

    // TEST

    for {
        classFile ← project.allClassFiles
        method@MethodWithBody(code) ← classFile.methods
        annotation ← code.runtimeVisibleTypeAnnotations
        if annotation.annotationType == propertyAnnotation
    } {
        analysisName should ("correctly calculate the property of the expression " + annotation.target + "in method " + method + " in class " + classFile.fqn) in {
            validateProperyByTypeAnnotation(method, annotation)
        }
    }

}
