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
package instructions

import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught

/**
 * Common superclass of all instructions that perform a conditional jump.
 *
 * @author Michael Eichberg
 */
trait SimpleConditionalBranchInstructionLike
        extends ConditionalBranchInstructionLike
        with ConstantLengthInstruction {

    /**
     * The comparison operator (incl. the constant) underlying the if instruction.
     * E.g., `<`, `< 0` or `!= null`.
     */
    def operator: String

    final def length: Int = 3

    final def isIsomorphic(thisPC: PC, otherPC: PC)(implicit code: Code): Boolean = {
        val other = code.instructions(otherPC)
        (this eq other) || (this == other)
    }
}

trait SimpleConditionalBranchInstruction
        extends ConditionalBranchInstruction
        with SimpleConditionalBranchInstructionLike {

    def branchoffset: Int

    /**
     * @inheritedDoc
     *
     * A simple conditional branch instruction has two targets unless both targets point
     * to the same instruction. In that case the jump has only one target, because the state
     * - independent of the taken path - always has to be the same.
     */
    final def nextInstructions(
        currentPC:             PC,
        regularSuccessorsOnly: Boolean
    )(
        implicit
        code:           Code,
        classHierarchy: ClassHierarchy = Code.preDefinedClassHierarchy
    ): Chain[PC] = {
        val nextInstruction = indexOfNextInstruction(currentPC)
        val jumpInstruction = currentPC + branchoffset
        if (nextInstruction == jumpInstruction)
            Chain.singleton(nextInstruction)
        else
            nextInstruction :&: jumpInstruction :&: Naught
    }

    override def toString(currentPC: Int): String = {
        val jumpDirection = if (branchoffset >= 0) "↓" else "↑"
        s"${getClass.getSimpleName}(true=${currentPC + branchoffset}$jumpDirection, false=↓)"
    }

}
object SimpleConditionalBranchInstruction {

    def unapply(i: SimpleConditionalBranchInstruction): Some[Int] = Some(i.branchoffset)

}
