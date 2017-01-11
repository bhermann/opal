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
package av
package checking

import scala.util.matching.Regex

/**
 * Matches a (binary) name of a file, method or class.
 *
 * @author Michael Eichberg
 */
trait NamePredicate extends (String ⇒ Boolean)

/**
 * @author Marco Torsello
 * @author Michael Eichberg
 */
case class Equals(name: BinaryString) extends NamePredicate {

    def apply(that: String): Boolean = {
        this.name.asString == that
    }
}

/**
 * @author Michael Eichberg
 */
case class StartsWith(name: BinaryString) extends NamePredicate {

    def apply(that: String): Boolean = {
        that.startsWith(this.name.asString)
    }
}

/**
 * Matches name of class, fields and methods based on their name.
 *
 * '''The name is matched against the binary notation.'''
 *
 * @author Michael Eichberg
 */
case class RegexNamePredicate(matcher: Regex) extends NamePredicate {

    def apply(otherName: String): Boolean = {
        matcher.findFirstIn(otherName).isDefined
    }
}
