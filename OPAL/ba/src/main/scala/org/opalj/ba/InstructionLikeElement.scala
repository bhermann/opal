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

/**
 * @author Malte Limmeroth
 */
sealed abstract class InstructionLikeElement[T] extends CodeElement[T] {
    def instruction: br.InstructionLike
}

object InstructionLikeElement {

    def unapply(ile: InstructionLikeElement[_]): Some[br.InstructionLike] = {
        Some(ile.instruction)
    }
}

/**
 * Wrapper for [[org.opalj.br.instructions.InstructionLike]]s.
 */
private[ba] case class InstructionElement(
    instruction: br.InstructionLike
) extends InstructionLikeElement[Nothing]

/**
 * Wrapper for annotated [[org.opalj.br.instructions.InstructionLike]]s.
 */
private[ba] case class AnnotatedInstructionElement[T](
        instruction: br.InstructionLike,
        annotation:  T
) extends InstructionLikeElement[T] {

    def this(ai: (br.InstructionLike, T)) { this(ai._1, ai._2) }
}
