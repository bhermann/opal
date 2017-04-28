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
package fpcf
package properties

import scala.collection.Set
import org.opalj.br.Method
import org.opalj.br.analyses.SomeProject
import AnalysisModes._

/**
 * This property encapsulates for each interface method those method that are potentially called by call-by-signature
 * means '''only'''. Since the property assumes that the current codebase can be extended, it does not make sense
 * to compute this property when a closed-world program/whole application is analyzed.
 *
 * When an incomplete application or library is analyzed the unknown code could contain subtypes that introduce valid
 * call edges within the known code base. This is in particular the case when it exists an interface and a - from the
 * interface independent - class within the codebase which share a method with the same signature. The unknown codebase
 * could contain a subtype which extends both the class and the interface but does not override the method. If there
 * exists a call on an interface method which fulfills the previously described scenario the method of the class
 * becomes a possible call target even if it does not implement the interface.
 * The following example illustrates this case:
 *
 * {{{
 *
 *   /* Known codebase */
 *
 *   public interface Logger {
 *     public void log();
 *   }
 *
 *   public class ConcreteLogger {
 *     public void log(){ /* */} // this method becomes a call target of an interface invocation because of the ApplicationLogger class
 *   }
 *
 *     /* unknown/hypothetical codebase */
 *
 *   public class ApplicationLogger extends ConcreteLogger implements Logger {
 *     // if the log method of the interface is invoked the log method of ConcreteLogger is called.
 *   }
 * }}}
 *
 * == Fallback ==
 *
 * The [[CallBySignature]] property has only a save fallback in the closed-world application scenario where the codebase
 * can not be extended. Hence, in this case it is save to yield [[NoCBSTargets]] as fallback.
 * In any other case there is no save fallback. Due to the unavailability of a save fallhback a very simple computation
 * is triggered that returns either [[NoCBSTargets]] if no call-by-signature targets could be found and [[CBSTargets]]
 * if a sound approximation of possible call-by-signature targets could be found. This computation returns all possible
 * call targets that fulfill the following criteria:
 *   - the respective method has to be concrete
 *   - the respective method need the access flag public
 *   - the declaring class of the respective method allows inheritance (it is not (effectively) final)
 *   - the declaring class of the respective method does not implement interface where the given method is defined
 *   - if it can not be determined whether the declaring class is a subtype of the interface, the respective method
 *     is included in the set of potential call-by-signature targets
 *
 * == Cycle Resolution Strategy ==
 *
 * This property does not depend on other entities since there can not be any cycles.
 *
 * @author Michael Reif
 */
sealed trait CallBySignature extends Property {

    final type Self = CallBySignature

    final def isRefineable = false

    /**
     * Returns the key used by all `CallBySignature` properties.
     */
    final def key = CallBySignature.Key
}

object CallBySignature {

    val fallback: (PropertyStore, Entity) ⇒ CallBySignature = (ps, e) ⇒ {
        val method = e.asInstanceOf[Method]
        val project = ps.context[SomeProject]
        val classFile = project.classFile(method)

        if (classFile.isClassDeclaration)
            NoCBSTargets;
        else {
            val analysisMode = project.analysisMode
            analysisMode match {
                case CPA                                     ⇒ UnknownCBSTargets
                case OPA                                     ⇒ UnknownCBSTargets
                case DesktopApplication | JEE6WebApplication ⇒ NoCBSTargets
            }
        }
    }

    final val Key = {
        PropertyKey.create[CallBySignature](
            // The unique name of the property.
            "CallBySignatureTargets",
            // The default property that will be used if no analysis is able
            // to (directly) compute the respective property.
            fallbackProperty = fallback,
            (ps: PropertyStore, epks: Iterable[SomeEPK]) ⇒ throw new UnknownError("internal error")
        )
    }
}

case class CBSTargets(cbsTargets: Set[Method]) extends CallBySignature

case object UnknownCBSTargets extends CallBySignature

case object NoCBSTargets extends CallBySignature

