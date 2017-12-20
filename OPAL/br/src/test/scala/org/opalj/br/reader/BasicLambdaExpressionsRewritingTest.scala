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
package reader

import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.scalactic.Equality

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

import org.opalj.log.{GlobalLogContext, LogContext}
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.SomeProject
import org.opalj.bi.TestResources.{locateTestResources ⇒ locate}
import org.opalj.br.instructions.INVOKESTATIC
import org.opalj.br.instructions.MethodInvocationInstruction

/**
 * Tests the rewriting of lambda expressions/method references using Java 8's infrastructure. I.e.,
 * tests rewritinh of [[org.opalj.br.instructions.INVOKEDYNAMIC]] instruction using
 * `LambdaMetafactory`s.
 *
 * @author Arne Lottmann
 * @author Michael Eichberg
 * @author Andreas Muttscheller
 */
@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class BasicLambdaExpressionsRewritingTest extends FunSpec with Matchers {

    val InvokedMethod = ObjectType("annotations/target/InvokedMethod")

    val InvokedMethods = ObjectType("annotations/target/InvokedMethods")

    val lambda18TestResources = locate("lambdas-1.8-g-parameters-genericsignature.jar", "bi")

    private def testMethod(project: SomeProject, classFile: ClassFile, name: String): Unit = {
        info(s"Testing $name")
        var successFull = false
        for {
            method ← classFile.findMethod(name)
            body ← method.body
            factoryCall ← body.iterator.collect { case i: INVOKESTATIC ⇒ i }
            if factoryCall.declaringClass.fqn.matches(LambdaExpressionsRewriting.LambdaNameRegEx)
            annotations = method.runtimeVisibleAnnotations
        } {
            successFull = true
            implicit val MethodDeclarationEquality = new Equality[Method] {
                def areEqual(a: Method, b: Any): Boolean =
                    b match {
                        case m: Method ⇒
                            a.compare(m) == 0 /* <=> same name and descriptor */ &&
                                a.visibilityModifier == m.visibilityModifier &&
                                a.isStatic == m.isStatic
                        case _ ⇒ false
                    }
            }

            if (annotations.exists(_.annotationType == InvokedMethods)) {
                val invokedTarget = annotations
                    .filter(_.annotationType == InvokedMethods)
                    .flatMap(_.elementValuePairs)
                    .flatMap(_.value.asInstanceOf[ArrayValue].values)
                    .filter { invokeMethod ⇒
                        val innerAnnotation = IndexedSeq(invokeMethod.asInstanceOf[AnnotationValue].annotation)
                        val expectedTarget = getInvokedMethod(project, classFile, innerAnnotation)
                        val actualTarget = getCallTarget(project, factoryCall, expectedTarget.get.name)
                        MethodDeclarationEquality.areEqual(expectedTarget.get, actualTarget.get)
                    }

                assert(
                    invokedTarget.nonEmpty,
                    s"failed to resolve $factoryCall in ${method.toJava}"
                )

            } else {
                val expectedTarget = getInvokedMethod(project, classFile, annotations)
                val actualTarget = getCallTarget(project, factoryCall, expectedTarget.get.name)

                withClue { s"failed to resolve $factoryCall in ${method.toJava}" }(
                    actualTarget.get should ===(expectedTarget.get)
                )
            }

        }
        assert(successFull, s"couldn't find factory method call in $name")
    }

    private def getCallTarget(
        project:            SomeProject,
        factoryCall:        INVOKESTATIC,
        expectedTargetName: String
    ): Option[Method] = {
        val proxy = project.classFile(factoryCall.declaringClass).get
        val forwardingMethod = proxy.methods.find { m ⇒
            !m.isConstructor && m.name != factoryCall.name && !m.isBridge
        }.get
        val invocationInstructions = forwardingMethod.body.get.instructions.collect {
            case i: MethodInvocationInstruction ⇒ i
        }

        // Make sure to get the correct instruction, Integer::compareUnsigned has 3
        // MethodInvokations in the proxy class, 2x intValue for getting the value of the int and
        // compareUnsigned for the actual comparison. This method must return the last one, which
        // is compareUnsigned
        val invocationInstruction = invocationInstructions
            .find(_.name == expectedTargetName)
            .orElse(Some(invocationInstructions.head)).get

        // declaringClass must be an ObjectType, since lambdas cannot be created on
        // array types, nor do arrays have methods that could be referenced
        val declaringType = invocationInstruction.declaringClass.asObjectType
        val targetMethodName = invocationInstruction.name
        val targetMethodDescriptor: MethodDescriptor =
            if (targetMethodName == "<init>") {
                MethodDescriptor(invocationInstruction.methodDescriptor.parameterTypes, VoidType)
            } else {
                invocationInstruction.methodDescriptor
            }

        if (project.classHierarchy.isInterface(declaringType.asObjectType).isYes)
            project.resolveInterfaceMethodReference(
                declaringType.asObjectType,
                targetMethodName,
                targetMethodDescriptor
            )
        else
            project.resolveMethodReference(
                declaringType.asObjectType,
                targetMethodName,
                targetMethodDescriptor
            )
    }

    /**
     * This method retrieves '''the first''' "invoked method" that is specified by an
     * [[InvokedMethod]] annotation present in the given annotations.
     *
     * This assumes that in the test cases, there is never more than one [[InvokedMethod]]
     * annotation on a single test method.
     *
     * The `InvokedMethod` annotation might have to be revised for use with Java 8 lambdas,
     * or used multiple times (the first time referring to the actual generated
     * invokedynamic instruction, while all other times would refer to invocations of the
     * generated object's single method).
     */
    private def getInvokedMethod(
        project:     SomeProject,
        classFile:   ClassFile,
        annotations: Annotations
    ): Option[Method] = {
        val method = for {
            invokedMethod ← annotations.filter(_.annotationType == InvokedMethod)
            pairs = invokedMethod.elementValuePairs
            ElementValuePair("receiverType", StringValue(receiverType)) ← pairs
            ElementValuePair("name", StringValue(methodName)) ← pairs
            classFileOpt = project.classFile(ObjectType(receiverType))
        } yield {
            if (classFileOpt.isEmpty) {
                throw new IllegalStateException(s"the class file $receiverType cannot be found")
            }
            val parameterTypes = getParameterTypes(pairs)
            findMethodRecursive(project, classFileOpt.get, methodName, receiverType, parameterTypes)
        }

        if (method.isEmpty) {
            val message =
                annotations.
                    filter(_.annotationType == InvokedMethod).
                    mkString("\n\t", "\n\t", "\n")
            fail(
                s"the specified invoked method ${message} is not defined "+
                    classFile.methods.map(_.name).mkString("; defined methods = {", ",", "}")
            )
        }

        Some(method.head)
    }

    /**
     * Get the method definition recursively -> if the method isn't implemented in `classFile`, check if
     * the super class has an implementation.
     *
     * @param project The project where to look for the classfile
     * @param classFile The classfile to check the method
     * @param methodName The name of the method to find
     * @param receiverType The type of the receiver, which was defined in the fixture annotation
     * @return The `Method` with the name `methodName`
     */
    def findMethodRecursive(
        project:        SomeProject,
        classFile:      ClassFile,
        methodName:     String,
        receiverType:   String,
        parameterTypes: Option[IndexedSeq[FieldType]]
    ): Method = {
        /**
         * Get the method definition recursively -> if the method isn't implemented in `classFile`, check if
         * the super class has an implementation.
         *
         * @param classFile The classfile to check the method
         * @return An Option of the `Method`
         */
        def findMethodRecursiveInner(classFile: ClassFile): Method = {
            var methodOpt = classFile.findMethod(methodName)
            if (parameterTypes.isDefined) {
                methodOpt = methodOpt.filter(_.parameterTypes == parameterTypes.get)
            }
            if (methodOpt.isEmpty) {
                classFile.superclassType match {
                    case Some(superType) ⇒ findMethodRecursiveInner(project.classFile(superType).get)
                    case None ⇒ throw new IllegalStateException(
                        s"$receiverType does not define $methodName"
                    )
                }
            } else {
                methodOpt.head
            }
        }
        findMethodRecursiveInner(classFile)
    }

    private def getParameterTypes(pairs: ElementValuePairs): Option[IndexedSeq[FieldType]] = {
        pairs.find(_.name == "parameterTypes").map { p ⇒
            p.value.asInstanceOf[ArrayValue].values.map {
                case ClassValue(x: ArrayType)  ⇒ x
                case ClassValue(x: ObjectType) ⇒ x
                case ClassValue(x: BaseType)   ⇒ x
                case x: ElementValue           ⇒ x.valueType
            }
        }
    }

    def testProject(project: SomeProject): Unit = {
        def testAllMethodsWithInvokedMethodAnnotation(ot: String): Unit = {
            val classFile = project.classFile(ObjectType(ot)).get
            classFile
                .methods
                .filter(_.runtimeVisibleAnnotations.exists { a ⇒
                    a.annotationType == InvokedMethod || a.annotationType == InvokedMethods
                })
                .foreach(m ⇒ testMethod(project, classFile, m.name))
        }

        it("should resolve all references in Lambdas") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/Lambdas")
        }

        // --- Method References ---

        it("should resolve all references in DefaultMethod") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/DefaultMethod")
        }

        it("should resolve all references in InvokeSpecial") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/InvokeSpecial")
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/InvokeSpecial$Superclass")
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/InvokeSpecial$Subclass")
        }

        it("should resolve all references in MethodReferencePrimitives") {
            testAllMethodsWithInvokedMethodAnnotation(
                "lambdas/methodreferences/MethodReferencePrimitives"
            )
        }

        it("should resolve all references in MethodReferences") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/MethodReferences")
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/MethodReferences$Child")
        }

        it("should resolve all references in ReceiverInheritance") {
            testAllMethodsWithInvokedMethodAnnotation(
                "lambdas/methodreferences/ReceiverInheritance"
            )
        }

        it("should resolve all references in SinkTest") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/SinkTest")
        }

        it("should resolve all references in StaticInheritance") {
            testAllMethodsWithInvokedMethodAnnotation("lambdas/methodreferences/StaticInheritance")
        }
    }

    describe("rewriting of lambda expressions") {
        val cache = new BytecodeInstructionsCache
        implicit val logContext: LogContext = GlobalLogContext
        val baseConfig: Config = ConfigFactory.load()
        val rewritingConfigKey = LambdaExpressionsRewriting.LambdaExpressionsRewritingConfigKey
        val logRewritingsConfigKey = LambdaExpressionsRewriting.LambdaExpressionsLogRewritingsConfigKey
        val testConfig = baseConfig.
            withValue(rewritingConfigKey, ConfigValueFactory.fromAnyRef(java.lang.Boolean.TRUE)).
            withValue(logRewritingsConfigKey, ConfigValueFactory.fromAnyRef(java.lang.Boolean.FALSE))
        class Framework extends {
            override val config = testConfig
        } with Java8FrameworkWithLambdaExpressionsSupportAndCaching(cache)
        val framework = new Framework()
        val project = Project(
            framework.ClassFiles(lambda18TestResources),
            Java8LibraryFramework.ClassFiles(org.opalj.bytecode.JRELibraryFolder),
            true,
            Traversable.empty,
            Project.defaultHandlerForInconsistentProjects,
            testConfig,
            logContext
        )
        testProject(project)
    }
}
