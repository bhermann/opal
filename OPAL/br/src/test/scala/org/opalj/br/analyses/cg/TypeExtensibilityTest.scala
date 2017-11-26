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
package analyses
package cg

import com.typesafe.config.ConfigFactory
import org.opalj.br.TestSupport.biProject
import org.scalatest.FunSpec
import org.scalatest.Matchers

/**
 * This tests the basic functionality of the [[TypeExtensibilityKey]] and determines whether the
 * computed information is correct. Beneath the basics, the test also contains an integration test
 * that ensures that all directly extensible types (see [[ClassExtensibilityTest]]) are also
 * transitively extensible.
 *
 * @author Michael Reif
 */
@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TypeExtensibilityTest extends FunSpec with Matchers {

    val testProject = biProject("extensible_classes.jar")

    /*
     * Can be used as prefix when ObjectTypes are created.
     */
    val testPackage = "extensible_classes/transitivity/"

    def mergeConfigString(extConf: String, pkgConf: String): String = extConf+"\n"+pkgConf

    describe("all directly extensible types") {

        val openConf = ConfigFactory.parseString(
            mergeConfigString(
                ClassExtensibilityConfig.classExtensibilityAnalysis,
                ClosedPackagesConfig.openCodeBase
            )
        )

        val closedConf = ConfigFactory.parseString(
            mergeConfigString(
                ClassExtensibilityConfig.classExtensibilityAnalysis,
                ClosedPackagesConfig.closedCodeBase
            )
        )

        it("should also be transitively extensible") {

            // open package
            var project = Project.recreate(testProject, openConf, true)
            var isDirectlyExtensible = project.get(ClassExtensibilityKey)
            var isExtensible = project.get(TypeExtensibilityKey)

            var relevantTypes = for {
                cf ← project.allClassFiles if (isDirectlyExtensible(cf.thisType).isYes)
            } yield cf.thisType

            if (relevantTypes.isEmpty)
                fail("No directly extensible types found!")

            relevantTypes.foreach { objectType ⇒
                isExtensible(objectType) should be(Yes)
            }

            // closed package
            project = Project.recreate(project, closedConf, true)
            isDirectlyExtensible = project.get(ClassExtensibilityKey)
            isExtensible = project.get(TypeExtensibilityKey)

            relevantTypes = for {
                cf ← project.allClassFiles if (isDirectlyExtensible(cf.thisType).isYes)
            } yield cf.thisType

            if (relevantTypes.isEmpty)
                fail("No directly extensible types found!")

            relevantTypes.foreach { objectType ⇒ isExtensible(objectType) should be(Yes) }
        }
    }

    describe("when a type is located in a closed package") {

        val configString = mergeConfigString(
            ClassExtensibilityConfig.classExtensibilityAnalysis,
            ClosedPackagesConfig.closedCodeBase
        )

        val config = ConfigFactory.parseString(configString)
        val project = Project.recreate(testProject, config, true)
        val isExtensible = project.get(TypeExtensibilityKey)

        it("a package visible class should be transitively extensible when it has a public subclass") {
            val objectType = ObjectType(s"${testPackage}case1/Class")
            isExtensible(objectType) should be(Yes)
        }

        it("a package visible class should NOT be transitively extensible when all subclasses"+
            " are (effectively) final") {
            val classOt = ObjectType(s"${testPackage}case2/Class")
            val interfaceOt = ObjectType(s"${testPackage}case2/Interface")

            isExtensible(classOt) should be(No)
            isExtensible(interfaceOt) should be(No)
        }

        it("a non-final public class is transitively extensible even when all subclasses are NOT") {
            val pClassOt = ObjectType(s"${testPackage}case3/PublicClass")

            val pfClassOt = ObjectType(s"${testPackage}case3/PublicFinalClass")
            val classOt = ObjectType(s"${testPackage}case3/Class")
            val pEfClassOt = ObjectType(s"${testPackage}case3/EffectivelyFinalClass")

            isExtensible(pClassOt) should be(Yes)

            isExtensible(pfClassOt) should be(No)
            isExtensible(classOt) should be(No)
            isExtensible(pEfClassOt) should be(No)
        }

    }

    describe("when a type belongs to an open package") {

        val configString = mergeConfigString(
            ClassExtensibilityConfig.classExtensibilityAnalysis,
            ClosedPackagesConfig.openCodeBase
        )

        val config = ConfigFactory.parseString(configString)
        val project = Project.recreate(testProject, config, true)
        val isExtensible = project.get(TypeExtensibilityKey)
        val isClassExtensible = project.get(ClassExtensibilityKey)

        it("a package visible class should be transitively extensible") {
            val case1_classOt = ObjectType(s"${testPackage}case1/Class")
            val case2_classOt = ObjectType(s"${testPackage}case2/Class")
            val case2_interfaceOt = ObjectType(s"${testPackage}case2/Interface")

            isExtensible(case1_classOt) should be(Yes)
            isExtensible(case2_classOt) should be(Yes)
            isExtensible(case2_interfaceOt) should be(Yes)
        }

        it("the extensibility of an unknown type from which an application type inherits from is (obviously) yes") {
            val hashSetObjectType = ObjectType("java/util/HashSet")
            assert(project.classFile(hashSetObjectType).isEmpty)
            assert(isClassExtensible(hashSetObjectType) == Unknown)

            isExtensible(hashSetObjectType) should be(Yes)
        }

        it("the extensibility of an unknown type should be unknown") {
            val unknownType = ObjectType("unknown/Unknown")
            assert(project.classFile(unknownType).isEmpty)
            assert(isClassExtensible(unknownType) == Unknown)

            isExtensible(unknownType) should be(Unknown)
        }
    }
}
