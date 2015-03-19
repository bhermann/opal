/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package av
package checking

import scala.language.implicitConversions
import java.net.URL
import scala.util.matching.Regex
import scala.collection.{ Map ⇒ AMap, Set ⇒ ASet }
import scala.collection.immutable.SortedSet
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.{ Map ⇒ MutableMap, HashSet }
import scala.Console.{ GREEN, RED, BLUE, RESET }
import org.opalj.util.PerformanceEvaluation.{ time, run }
import org.opalj.br._
import org.opalj.br.reader.Java8Framework.ClassFiles
import org.opalj.br.analyses.{ ClassHierarchy, Project }
import org.opalj.de._
import org.opalj.log.OPALLogger
import org.opalj.io.processSource

/**
 * A specification of a project's architectural constraints.
 *
 * ===Usage===
 * First define the ensembles, then the rules and at last specify the
 * class files that should be analyzed. The rules will then automatically be
 * evaluated.
 *
 * ===Note===
 * One ensemble is predefined: `Specification.empty` it represents an ensemble that
 * contains no source elements and which can, e.g., be used to specify that no "real"
 * ensemble is allowed to depend on a specific ensemble.
 *
 * @author Michael Eichberg
 * @author Samuel Beracasa
 * @author Marco Torsello
 */
class Specification(val project: Project[URL], val useAnsiColors: Boolean) {

    def this(project: Project[URL]) {
        this(project, useAnsiColors = false)
    }

    def this(
        classFiles: Traversable[(ClassFile, URL)],
        useAnsiColors: Boolean = false) {
        this(
            run {
                Project(projectClassFilesWithSources = classFiles, Traversable.empty)
            } { (t, project) ⇒
                import project.logContext
                val logMessage = "1. reading "+project.classFilesCount+" class files took "+t
                val message = if (useAnsiColors) GREEN + logMessage + RESET else logMessage
                OPALLogger.progress(message)
                project
            },
            useAnsiColors)
    }

    import project.logContext

    private[this] def logProgress(logMessage: String): Unit = {
        OPALLogger.progress(if (useAnsiColors) GREEN + logMessage + RESET else logMessage)
    }

    private[this] def logWarn(logMessage: String): Unit = {
        val message = if (useAnsiColors) RED + logMessage + RESET else logMessage
        OPALLogger.warn("project warn", message)
    }

    private[this] def logInfo(logMessage: String): Unit = {
        OPALLogger.info("project info", logMessage)
    }

    @volatile
    private[this] var theEnsembles: MutableMap[Symbol, (SourceElementsMatcher, ASet[VirtualSourceElement])] =
        scala.collection.mutable.OpenHashMap.empty

    /**
     * The set of defined ensembles. An ensemble is identified by a symbol, a query
     * which matches source elements and the project's source elements that are matched.
     * The latter is available only after [[analyze]] was called.
     */
    def ensembles: AMap[Symbol, (SourceElementsMatcher, ASet[VirtualSourceElement])] =
        theEnsembles

    // calculated after all class files have been loaded
    private[this] var theOutgoingDependencies: MutableMap[VirtualSourceElement, AMap[VirtualSourceElement, DependencyTypesSet]] =
        scala.collection.mutable.OpenHashMap.empty

    /**
     * Mapping between a source element and those source elements it depends on/uses.
     *
     * This mapping is automatically created when analyze is called.
     */
    def outgoingDependencies: AMap[VirtualSourceElement, AMap[VirtualSourceElement, DependencyTypesSet]] =
        theOutgoingDependencies

    // calculated after all class files have been loaded
    private[this] var theIncomingDependencies: MutableMap[VirtualSourceElement, ASet[(VirtualSourceElement, DependencyType)]] =
        scala.collection.mutable.OpenHashMap.empty

    /**
     * Mapping between a source element and those source elements that depend on it.
     *
     * This mapping is automatically created when analyze is called.
     */
    def incomingDependencies: AMap[VirtualSourceElement, ASet[(VirtualSourceElement, DependencyType)]] = theIncomingDependencies

    // calculated after the extension of all ensembles is determined
    private[this] val matchedSourceElements: HashSet[VirtualSourceElement] = HashSet.empty

    private[this] val allSourceElements: HashSet[VirtualSourceElement] = HashSet.empty

    private[this] var unmatchedSourceElements: ASet[VirtualSourceElement] = _

    /**
     * Adds a new ensemble definition to this architecture specification.
     *
     * @throws SpecificationError If the ensemble is already defined.
     */
    @throws(classOf[SpecificationError])
    def ensemble(
        ensembleSymbol: Symbol)(
            sourceElementMatcher: SourceElementsMatcher): Unit = {
        if (ensembles.contains(ensembleSymbol))
            throw SpecificationError("the ensemble is already defined: "+ensembleSymbol)

        theEnsembles += (
            (ensembleSymbol, (sourceElementMatcher, Set.empty[VirtualSourceElement]))
        )
    }

    /**
     * Creates a `Symbol` with the given name.
     *
     * This method is primarily useful if ensemble names are created programmatically
     * and the code should communicate that the created name identifies an ensemble.
     * E.g., instead of
     * {{{
     *  for (moduleID <- 1 to 10) Symbol("module"+moduleID)
     * }}}
     * it is now possible to write
     * {{{
     *  for (moduleID <- 1 to 10) EnsembleID("module"+moduleID)
     * }}}
     * which better communicates the intention.
     */
    def EnsembleID(ensembleName: String): Symbol = Symbol(ensembleName)

    /**
     * Represents an ensemble that contains no source elements. This can be used, e.g.,
     * to specify that a (set of) specific source element(s) is not allowed to depend
     * on any other source elements (belonging to the project).
     */
    val empty = {
        ensemble('empty)(NoSourceElementsMatcher)
        'empty
    }

    /**
     * Facilitates the definition of common source element matchers by means of common
     * String patterns.
     */
    @throws(classOf[SpecificationError])
    implicit def StringToSourceElementMatcher(matcher: String): SourceElementsMatcher = {
        if (matcher endsWith ".*")
            PackageMatcher(matcher.substring(0, matcher.length() - 2).replace('.', '/'))
        else if (matcher endsWith ".**")
            PackageMatcher(matcher.substring(0, matcher.length() - 3).replace('.', '/'), true)
        else if (matcher endsWith "*")
            SimpleClassMatcher(matcher.substring(0, matcher.length() - 1).replace('.', '/'), true)
        else if (matcher.indexOf('*') == -1)
            SimpleClassMatcher(matcher.replace('.', '/'))
        else
            throw SpecificationError("unsupported matcher pattern: "+matcher);
    }

    def classes(matcher: Regex): SourceElementsMatcher = SimpleClassMatcher(matcher)

    /**
     * Returns the class files stored at the given location.
     */
    implicit def FileToClassFileProvider(file: java.io.File): Seq[(ClassFile, URL)] =
        ClassFiles(file)

    var architectureCheckers: List[ArchitectureChecker] = Nil

    case class GlobalIncomingConstraint(
        targetEnsemble: Symbol,
        sourceEnsembles: Seq[Symbol])
            extends DependencyChecker {

        override def targetEnsembles: Seq[Symbol] = Seq(targetEnsemble)

        override def violations(): ASet[SpecificationViolation] = {
            val sourceEnsembleElements =
                (Set[VirtualSourceElement]() /: sourceEnsembles)(_ ++ ensembles(_)._2)
            val (_, targetEnsembleElements) = ensembles(targetEnsemble)
            for {
                targetEnsembleElement ← targetEnsembleElements
                if incomingDependencies.contains(targetEnsembleElement)
                (incomingElement, dependencyType) ← incomingDependencies(targetEnsembleElement)
                if !(
                    sourceEnsembleElements.contains(incomingElement) ||
                    targetEnsembleElements.contains(incomingElement))
            } yield {
                DependencyViolation(
                    project,
                    this,
                    incomingElement,
                    targetEnsembleElement,
                    dependencyType,
                    "not allowed global incoming dependency found")
            }
        }

        override def toString =
            targetEnsemble+" is_only_to_be_used_by ("+sourceEnsembles.mkString(",")+")"
    }

    case class LocalOutgoingIsOnlyAllowedToConstraint(
        sourceEnsemble: Symbol,
        targetEnsembles: Seq[Symbol])
            extends DependencyChecker {

        if (targetEnsembles.isEmpty)
            throw SpecificationError("no target ensembles specified: "+toString())

        override def sourceEnsembles: Seq[Symbol] = Seq(sourceEnsemble)

        override def violations(): ASet[SpecificationViolation] = {
            val unknownEnsembles = targetEnsembles.filterNot(ensembles.contains(_))
            if (unknownEnsembles.nonEmpty)
                throw SpecificationError(
                    unknownEnsembles.mkString("unknown ensemble(s): ", ",", ""))

            val (_ /*ensembleName*/ , sourceEnsembleElements) = ensembles(sourceEnsemble)
            val allAllowedLocalTargetSourceElements =
                // self references are allowed as well as references to source elements belonging
                // to a target ensemble
                (sourceEnsembleElements /: targetEnsembles)(_ ++ ensembles(_)._2)

            for {
                sourceElement ← sourceEnsembleElements
                // outgoingDependences : Map[VirtualSourceElement, Set[(VirtualSourceElement, DependencyType)]]
                targets = outgoingDependencies.get(sourceElement)
                if targets.isDefined
                (targetElement, dependencyTypes) ← targets.get
                if !(allAllowedLocalTargetSourceElements contains targetElement)
                // references to unmatched source elements are ignored
                if !(unmatchedSourceElements contains targetElement)
                // from here on, we have found a violation
                dependencyType ← dependencyTypes
            } yield {
                DependencyViolation(
                    project,
                    this,
                    sourceElement,
                    targetElement,
                    dependencyType,
                    "not allowed local outgoing dependency found")
            }
        }

        override def toString =
            sourceEnsemble+" is_only_allowed_to_use ("+targetEnsembles.mkString(",")+")"
    }

    /**
     * Forbids any locals dependency between a specific sourcs ensemble and
     * several target ensembles.
     *
     * ==Example Scenario==
     * If the ensemble `ex` is not allowed to use `ey` and the source element `x` which
     * belongs to ensemble `ex` depends on a source element belonging to `ey` then
     * a [[SpecificationViolation]] is generated.
     */
    case class LocalOutgoingNotAllowedConstraint(
        sourceEnsemble: Symbol,
        targetEnsembles: Seq[Symbol])
            extends DependencyChecker {

        if (targetEnsembles.isEmpty)
            throw SpecificationError("no target ensembles specified: "+toString())

        // WE DO NOT WANT TO CHECK THE VALIDITY OF THE ENSEMBLE IDS NOW TO MAKE IT EASY
        // TO INTERMIX THE DEFINITION OF ENSEMBLES AND CONSTRAINTS

        override def sourceEnsembles: Seq[Symbol] = Seq(sourceEnsemble)

        override def violations(): ASet[SpecificationViolation] = {
            val unknownEnsembles = targetEnsembles.filterNot(ensembles.contains(_))
            if (unknownEnsembles.nonEmpty)
                throw SpecificationError(
                    unknownEnsembles.mkString("unknown ensemble(s): ", ",", ""))

            val (_ /*ensembleName*/ , sourceEnsembleElements) = ensembles(sourceEnsemble)
            val notAllowedTargetSourceElements =
                (Set.empty[VirtualSourceElement] /: targetEnsembles)(_ ++ ensembles(_)._2)

            for {
                sourceElement ← sourceEnsembleElements
                targets = outgoingDependencies.get(sourceElement)
                if targets.isDefined
                (targetElement, dependencyTypes) ← targets.get
                dependencyType ← dependencyTypes
                if (notAllowedTargetSourceElements contains targetElement)
            } yield {
                DependencyViolation(
                    project,
                    this,
                    sourceElement,
                    targetElement,
                    dependencyType,
                    "not allowed local outgoing dependency found")
            }
        }

        override def toString =
            targetEnsembles.mkString(s"$sourceEnsemble is_not_allowed_to_use (", ",", ")")
    }

    /**
     * Checks whether all elements in the source ensemble are annotated with the given
     * annotation.
     *
     * ==Example Scenario==
     * If every element in the ensemble `ex` should be annotated with `ey` and the
     * source element `x` which belongs to ensemble `ex` has no annotation that matches
     * `ey` then a [[SpecificationViolation]] is generated.
     *
     * 	@param sourceEnsemble An ensemble containing elements that should be annotated.
     *  @param annotationMatcher The annotation that should match.
     */
    case class LocalOutgoingAnnotatedWithConstraint(
        sourceEnsemble: Symbol,
        annotationMatcher: AnnotationMatcher)
            extends PropertyChecker {

        import DependencyType._

        override def property: String = annotationMatcher.toString

        override def sourceEnsembles: Seq[Symbol] = Seq(sourceEnsemble)

        override def violations(): ASet[SpecificationViolation] = {
            val (_ /*ensembleName*/ , sourceEnsembleElements) = ensembles(sourceEnsemble)

            for {
                sourceElement ← sourceEnsembleElements
                classFile ← project.classFile(sourceElement.classType.asObjectType)
                annotations = sourceElement match {
                    case s: VirtualClass ⇒ classFile.annotations
                    case s: VirtualField ⇒ classFile.fields.collectFirst {
                        case field if field.asVirtualField(classFile).compareTo(s) == 0 ⇒ field
                    } match {
                        case Some(f) ⇒ f.annotations
                        case _       ⇒ IndexedSeq.empty
                    }
                    case s: VirtualMethod ⇒ classFile.methods.collectFirst {
                        case method if method.asVirtualMethod(classFile).compareTo(s) == 0 ⇒ method
                    } match {
                        case Some(m) ⇒ m.annotations
                        case _       ⇒ IndexedSeq.empty
                    }
                    case _ ⇒ IndexedSeq.empty
                }

                if !annotations.foldLeft(false) {
                    (v: Boolean, a: Annotation) ⇒ v || annotationMatcher.doesMatch(a)
                }
            } yield {
                PropertyViolation(
                    project,
                    this,
                    sourceElement,
                    ANNOTATED_WITH,
                    "required annotation not found")
            }
        }

        override def toString =
            s"$sourceEnsemble every_element_should_be_annotated_with "+annotationMatcher.toString
    }

    case class SpecificationFactory(contextEnsembleSymbol: Symbol) {

        def apply(sourceElementsMatcher: SourceElementsMatcher): Unit = {
            ensemble(contextEnsembleSymbol)(sourceElementsMatcher)
        }

        def is_only_to_be_used_by(sourceEnsembleSymbols: Symbol*): Unit = {
            architectureCheckers =
                GlobalIncomingConstraint(
                    contextEnsembleSymbol,
                    sourceEnsembleSymbols.toSeq) :: architectureCheckers
        }

        def allows_incoming_dependencies_from(sourceEnsembleSymbols: Symbol*): Unit = {
            architectureCheckers =
                GlobalIncomingConstraint(
                    contextEnsembleSymbol,
                    sourceEnsembleSymbols.toSeq) :: architectureCheckers
        }

        def is_only_allowed_to_use(targetEnsembles: Symbol*): Unit = {
            architectureCheckers =
                LocalOutgoingIsOnlyAllowedToConstraint(
                    contextEnsembleSymbol,
                    targetEnsembles.toSeq) :: architectureCheckers
        }

        def is_not_allowed_to_use(targetEnsembles: Symbol*): Unit = {
            architectureCheckers =
                LocalOutgoingNotAllowedConstraint(
                    contextEnsembleSymbol,
                    targetEnsembles.toSeq) :: architectureCheckers
        }

        def every_element_should_be_annotated_with(annotationMatcher: AnnotationMatcher): Unit = {
            architectureCheckers =
                LocalOutgoingAnnotatedWithConstraint(
                    contextEnsembleSymbol,
                    annotationMatcher) :: architectureCheckers
        }
    }

    protected implicit def EnsembleSymbolToSpecificationElementFactory(
        ensembleSymbol: Symbol): SpecificationFactory =
        SpecificationFactory(ensembleSymbol)

    protected implicit def EnsembleToSourceElementMatcher(
        ensembleSymbol: Symbol): SourceElementsMatcher = {
        if (!ensembles.contains(ensembleSymbol))
            throw SpecificationError(s"the ensemble: $ensembleSymbol is not yet defined")

        ensembles(ensembleSymbol)._1
    }

    /**
     * Returns a textual representation of an ensemble.
     */
    def ensembleToString(ensembleSymbol: Symbol): String = {
        val (sourceElementsMatcher, extension) = ensembles(ensembleSymbol)
        ensembleSymbol+"{"+
            sourceElementsMatcher+"  "+
            {
                if (extension.isEmpty)
                    "/* NO ELEMENTS */ "
                else {
                    (("\n\t//"+extension.head.toString+"\n") /: extension.tail)((s, vse) ⇒ s+"\t//"+vse.toJava+"\n")
                }
            }+"}"
    }

    /**
     * Can be called after the evaluation of the extents of the ensembles to print
     * out the current configuration.
     */
    def ensembleExtentsToString: String = {
        var s = ""
        for ((ensemble, (_, elements)) ← theEnsembles) {
            s += ensemble+"\n"
            for (element ← elements) {
                s += "\t\t\t"+element.toJava+"\n"
            }
        }
        s
    }

    def analyze(): Set[SpecificationViolation] = {
        val dependencyStore = time {
            project.get(DependencyStoreWithoutSelfDependenciesKey)
        } { t ⇒ logProgress("2.1. preprocessing dependencies took "+t) }

        logInfo("Dependencies between source elements: "+dependencyStore.dependencies.size)
        logInfo("Dependencies on primitive types: "+dependencyStore.dependenciesOnBaseTypes.size)
        logInfo("Dependencies on array types: "+dependencyStore.dependenciesOnArrayTypes.size)

        time {
            for {
                (source, targets) ← dependencyStore.dependencies
                (target, dTypes) ← targets
            } {
                allSourceElements += source
                allSourceElements += target

                theOutgoingDependencies.update(source, targets)

                for { dType ← dTypes } {
                    theIncomingDependencies.update(
                        target,
                        theIncomingDependencies.getOrElse(target, Set.empty) +
                            ((source, dType)))
                }
            }
        } { t ⇒ logProgress("2.2. postprocessing dependencies took "+t) }
        logInfo("Number of source elements: "+allSourceElements.size)
        logInfo("Outgoing dependencies: "+theOutgoingDependencies.size)
        logInfo("Incoming dependencies: "+theIncomingDependencies.size)

        // Calculate the extension of the ensembles
        //
        time {
            val instantiatedEnsembles =
                theEnsembles.par map { ensemble ⇒
                    val (ensembleSymbol, (sourceElementMatcher, _)) = ensemble
                    // if a sourceElementMatcher is reused!
                    sourceElementMatcher.synchronized {
                        val extension = sourceElementMatcher.extension(project)
                        if (extension.isEmpty && sourceElementMatcher != NoSourceElementsMatcher)
                            logWarn(s"   $ensembleSymbol (${extension.size})")
                        else
                            logInfo(s"   $ensembleSymbol (${extension.size})")

                        Specification.this.synchronized {
                            matchedSourceElements ++= extension
                        }
                        (ensembleSymbol, (sourceElementMatcher, extension))
                    }
                }
            theEnsembles = instantiatedEnsembles.seq

            unmatchedSourceElements = allSourceElements -- matchedSourceElements

            logInfo("   => Matched source elements: "+matchedSourceElements.size)
            logInfo("   => Other source elements: "+unmatchedSourceElements.size)
        } { t ⇒ logProgress("3. determing the extension of the ensembles took "+t) }

        // Check all rules
        //
        time {
            val result =
                for { architectureChecker ← architectureCheckers.par } yield {
                    logProgress("   checking: "+architectureChecker)
                    for (violation ← architectureChecker.violations) yield violation
                }
            Set.empty ++ (result.filter(_.nonEmpty).flatten)
        } { t ⇒ logProgress("4. checking the specified dependency constraints took "+t) }
    }

}
object Specification {

    def ProjectDirectory(directoryName: String): Seq[(ClassFile, URL)] = {
        val file = new java.io.File(directoryName)
        if (!file.exists)
            throw SpecificationError("the specified directory does not exist: "+directoryName)
        if (!file.canRead)
            throw SpecificationError("cannot read the specified directory: "+directoryName)
        if (!file.isDirectory)
            throw SpecificationError("the specified directory is not a directory: "+directoryName)

        Project.Java8ClassFileReader.ClassFiles(file)
    }

    def ProjectJAR(jarName: String): Seq[(ClassFile, URL)] = {
        val file = new java.io.File(jarName)
        if (!file.exists)
            throw SpecificationError("the specified directory does not exist: "+jarName)
        if (!file.canRead)
            throw SpecificationError("cannot read the specified JAR: "+jarName)
        if (file.isDirectory)
            throw SpecificationError("the specified jar file is a directory: "+jarName)

        println(s"Loading project JAR: $jarName")

        Project.Java8ClassFileReader.ClassFiles(file)
    }

    /**
     * Load all jar files.
     */
    def ProjectJARs(jarNames: Seq[String]): Seq[(ClassFile, URL)] = {
        jarNames.map(ProjectJAR(_)).flatten
    }

    /**
     * Loads all class files of the specified jar file using the library class file reader.
     * (I.e., the all method implementations are skipped.)
     *
     * @param jarName The name of a jar file.
     */
    def LibraryJAR(jarName: String): Seq[(ClassFile, URL)] = {
        val file = new java.io.File(jarName)
        if (!file.exists)
            throw SpecificationError("the specified directory does not exist: "+jarName)
        if (!file.canRead)
            throw SpecificationError("cannot read the specified JAR: "+jarName)
        if (file.isDirectory)
            throw SpecificationError("the specified jar file is a directory: "+jarName)

        println(s"Loading library JAR: $jarName")

        Project.Java8LibraryClassFileReader.ClassFiles(file)
    }

    /**
     * Load all jar files using the library class loader.
     */
    def LibraryJARs(jarNames: Seq[String]): Seq[(ClassFile, URL)] = {
        jarNames.map(LibraryJAR(_)).flatten
    }

    /**
     * Returns a list of paths contained inside the given classpath file.
     * A classpath file should contain paths as text seperated by a path-separator character.
     * On UNIX systems, this character is <code>':'</code>; on Microsoft Windows systems it
     * is <code>';'</code>.
     *
     * ===Example===
     * /path/to/jar/library.jar:/path/to/library/example.jar:/path/to/library/example2.jar
     *
     * Classpath files should be used to prevent absolute paths in tests.
     */
    def Classpath(
        fileName: String,
        pathSeparatorChar: Char = java.io.File.pathSeparatorChar): Iterable[String] = {
        processSource(scala.io.Source.fromFile(new java.io.File(fileName))) { s ⇒
            s.getLines().map(_.split(pathSeparatorChar)).flatten.toSet
        }
    }

    /**
     * Returns a list of paths that matches the given
     * regular expression from the given list of paths.
     */
    def PathToJARs(paths: Iterable[String], jarName: Regex): Iterable[String] = {
        val matchedPaths = paths.collect {
            case p @ (jarName(m)) ⇒ p
        }
        if (matchedPaths.isEmpty) {
            throw SpecificationError(s"no path is matched by: $jarName.")
        }
        matchedPaths
    }

    /**
     * Returns a list of paths that match the given list of
     * regular expressions from the given list of paths.
     */
    def PathToJARs(paths: Iterable[String], jarNames: Iterable[Regex]): Iterable[String] = {
        jarNames.foldLeft(Set.empty[String])((c, n) ⇒ c ++ PathToJARs(paths, n))
    }
}

