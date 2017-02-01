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

/**
 * Super class of all bytecode instructions that always jump to a specific
 * target instruction.
 *
 * @author Michael Eichberg
 */
trait UnconditionalBranchInstructionLike
        extends ControlTransferInstructionLike
        with ConstantLengthInstruction {

    override final def numberOfPoppedOperands(ctg: Int ⇒ ComputationalTypeCategory): Int = 0

    override final def readsLocal: Boolean = false

    override final def indexOfReadLocal: Int = throw new UnsupportedOperationException()

    override final def writesLocal: Boolean = false

    override final def indexOfWrittenLocal: Int = throw new UnsupportedOperationException()

}
object UnconditionalBranch {

    def unapply(i: UnconditionalBranchInstruction): Some[Int] = Some(i.branchoffset)

}

trait UnconditionalBranchInstruction extends Instruction with UnconditionalBranchInstructionLike {
    def branchoffset: Int

    override final def nextInstructions(
        currentPC:             PC,
        regularSuccessorsOnly: Boolean
    )(
        implicit
        code:           Code,
        classHierarchy: ClassHierarchy = Code.BasicClassHierarchy
    ): Chain[PC] = {
        Chain.singleton(currentPC + branchoffset)
    }

    override def toString(currentPC: Int) = {
        val direction = (if (branchoffset >= 0) "↓" else "↑")
        getClass.getSimpleName+" "+(currentPC + branchoffset) + direction
    }

}
