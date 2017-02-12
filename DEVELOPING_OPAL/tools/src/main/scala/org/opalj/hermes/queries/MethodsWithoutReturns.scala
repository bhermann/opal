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
package hermes
package queries

import org.opalj.br.analyses.Project
import org.opalj.br.instructions.ReturnInstruction
import org.opalj.br.cfg.CFGFactory
import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught

/**
 * Counts the number of class files per class file version.
 *
 * @author Michael Eichberg
 */
object MethodsWithoutReturns extends FeatureQuery {

    final val AlwaysThrowsExceptionMethodsFeatureId = "Never Returns Normally"
    final val InfiniteLoopMethodsFeatureId = "Method with Infinite Loops"
    override def featureIDs: Seq[String] = List(
        AlwaysThrowsExceptionMethodsFeatureId,
        InfiniteLoopMethodsFeatureId
    )

    override def apply[S](
        projectConfiguration: ProjectConfiguration,
        project:              Project[S],
        rawClassFiles:        Traversable[(da.ClassFile, S)]
    ): TraversableOnce[Feature[S]] = {
        var infiniteLoopMethods: Chain[MethodLocation[S]] = Naught
        var alwaysThrowsExceptionMethods: Chain[MethodLocation[S]] = Naught

        for {
            (classFile, source) ← project.projectClassFilesWithSources
            classFileLocation = ClassFileLocation(source, classFile)
            method ← classFile.methods
            body ← method.body
            if !isInterrupted
            hasReturn = body.exists { (pc, i) ⇒ i.isInstanceOf[ReturnInstruction] }
            if !hasReturn
        } {
            val cfg = CFGFactory(body, project.classHierarchy)
            if (cfg.abnormalReturnNode.predecessors.isEmpty)
                infiniteLoopMethods :&:= MethodLocation(classFileLocation, method)
            else
                alwaysThrowsExceptionMethods :&:= MethodLocation(classFileLocation, method)
        }

        List(
            Feature[S](
                AlwaysThrowsExceptionMethodsFeatureId,
                alwaysThrowsExceptionMethods.size, alwaysThrowsExceptionMethods
            ),
            Feature[S](InfiniteLoopMethodsFeatureId, infiniteLoopMethods.size, infiniteLoopMethods)
        )
    }
}
