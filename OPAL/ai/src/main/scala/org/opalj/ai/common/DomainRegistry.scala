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
package ai
package common

import org.opalj.br._
import org.opalj.br.analyses.SomeProject

/**
 * Registry for all domains that can be instantiated given a `Project`, `ClassFile` and
 * `Method` object.
 *
 * The registry was developed to support tools for debugging purposes that let
 * the user/developer choose between different domains. After choosing a domain,
 * an abstract interpretation can be performed.
 *
 * The compatible domains that are part of OPAL are already registered.
 *
 * ==Thread Safety==
 * The registry is thread safe.
 *
 * @author Michael Eichberg
 */
object DomainRegistry {

    type TheRegistry = Map[Class[_ <: Domain], (SomeProject, ClassFile, Method) ⇒ Domain]

    private[this] var descriptions: Map[String, Class[_ <: Domain]] = Map.empty
    private[this] var theRegistry: TheRegistry = Map.empty

    /**
     * Register a new domain that can be used to perform an abstract interpretation
     * of a specific method.
     *
     * @param domainDescription A short description of the properties of the domain;
     *      in particular w.r.t. the kind of computations the domain does.
     * @param domainClass The class of the domain.
     * @param factory The factory method that will be used to create instances of the
     *      domain.
     */
    def register(
        domainDescription: String,
        domainClass:       Class[_ <: Domain],
        factory:           (SomeProject, ClassFile, Method) ⇒ Domain
    ): Unit = {
        this.synchronized {
            descriptions += ((domainDescription, domainClass))
            theRegistry += ((domainClass, factory))
        }
    }

    /**
     * Returns an `Iterable` to make it possible to iterate over the descriptions of
     * the domain. Useful to show the (end-users) some meaningful descriptions.
     */
    def domainDescriptions(): Iterable[String] = this.synchronized { descriptions.keys }

    /**
     * Returns the current view of the registry.
     */
    def registry: TheRegistry = this.synchronized { theRegistry }

    /**
     * Creates a new instance of the domain identified by the given `domainDescription`.
     *
     * @param domainDescription The description that identifies the domain.
     * @param project The project.
     * @param classFile A class file object that belongs to the given project.
     * @param method A non-native/non-abstract method belonging to the specified class
     *      file.
     */
    // primarily introduced to facilitate the interaction with Java
    def newDomain(
        domainDescription: String,
        project:           SomeProject,
        classFile:         ClassFile,
        method:            Method
    ): Domain = {
        this.synchronized {
            val domainClass: Class[_ <: Domain] = descriptions(domainDescription)
            newDomain(domainClass, project, classFile, method)
        }
    }

    /**
     * Creates a new instance of the domain identified by the given `domainClass`. To
     * create the instance the registered factory method will be used.
     *
     * @param domainClass The class object of the domain.
     * @param project The project.
     * @param classFile A class file object that belongs to the given project.
     * @param method A non-native/non-abstract method belonging to the specified class
     *      file.
     */
    def newDomain(
        domainClass: Class[_ <: Domain],
        project:     SomeProject,
        classFile:   ClassFile,
        method:      Method
    ): Domain = {
        this.synchronized {
            theRegistry(domainClass)(project, classFile, method)
        }
    }

    // initialize the registry with the known default domains
    register(
        "[l0.BaseDomain] The most basic domain; it does all computations at the type level.",
        classOf[domain.l0.BaseDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l0.BaseDomain(project, classFile, method)
        }
    )

    register(
        "[l0.BaseDomainWithDefUse] The most basic domain; it does all computations at the type level and records the definition/use information.",
        classOf[domain.l0.BaseDomainWithDefUse[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l0.BaseDomainWithDefUse(project, classFile, method)
        }
    )

    register(
        "[l1.DefaultIntervalValuesDomain] A domain that uses intervals to represent the values of primitive values/variables.",
        classOf[domain.l1.DefaultIntervalValuesDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultIntervalValuesDomain(project, classFile, method)
        }
    )

    register(
        "[l1.DefaultSetValuesDomain] A domain that represents reference values at the type level and represents int/long values using sets.",
        classOf[domain.l1.DefaultSetValuesDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultSetValuesDomain(project, classFile, method)
        }
    )

    register(
        "[l1.DefaultReferenceValuesDomain] A domain that tracks various properties related to references values.",
        classOf[domain.l1.DefaultReferenceValuesDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultReferenceValuesDomain(project, classFile, method)
        }
    )

    register(
        "[l1.DefaultDomain] A domain that tracks origin and null-ness properties of references values as well as tracks int/long values using ranges/sets.",
        classOf[domain.l1.DefaultDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultDomain(project, classFile, method)
        }
    )

    register(
        "[l1.DefaultDomainWithDefUse] A classical abstract domain that records the CFG and tracks the def/use relations. The null-ness properties of references values are also tracked as well as int/long values using ranges/sets.",
        classOf[domain.l1.DefaultDomainWithCFGAndDefUse[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultDomainWithCFGAndDefUse(project, classFile, method)
        }
    )

    register(
        "[l2.DefaultDomain] This abstract domain performs method invocations up to two levels deep additionally to the features of the l1.DefaultDomain.",
        classOf[domain.l2.DefaultDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l2.DefaultDomain(project, classFile, method)
        }
    )

    register(
        "[l2.DefaultPerformInvocationsDomain] This abstract domain performs simple method invocations additionally to the features of the l1.DefaultDomain.",
        classOf[domain.l2.DefaultPerformInvocationsDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l2.DefaultPerformInvocationsDomain(project, classFile, method)
        }
    )

    register(
        "[la.DefaultDomain] This abstract domain reuses information provided by some pre analyses additionally to the features of the l1.DefaultDomain.",
        classOf[domain.l1.DefaultDomain[_]],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.l1.DefaultDomain(project, classFile, method)
        }
    )

    register(
        "[li.DefaultDomain] This domain performs some partial concrete evaluation.",
        classOf[domain.la.DefaultDomain],
        (project: SomeProject, classFile: ClassFile, method: Method) ⇒ {
            new domain.la.DefaultDomain(project, classFile, method)
        }
    )
}
