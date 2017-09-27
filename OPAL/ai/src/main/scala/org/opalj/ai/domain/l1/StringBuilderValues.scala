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
package domain
package l1

import org.opalj.br.ObjectType
import java.lang.{StringBuilder ⇒ JStringBuilder}

/**
 * Enables the tracing of `StringBuilders`.
 *
 * TODO ==Implementation Details==
 * ==Copy on Branch==
 * Given that StringBuilders are mutable, we have to create a copy whenever we
 * have a branch. This enables us to make the domain value
 * that represents the state of the StringBuilder independently mutable on each branch.
 * E.g.,
 * {{{
 * val sb : StringBuilder = ....
 * if (condition) sb.append("X") else sb.append("Y")
 * // here, the represented string either ends with "X" or with "Y", but not with "XY" or "YX"
 * }}}
 *
 * ==Ensure Termination==
 * To ensure termination in degenerated cases, such as:
 * {{{
 * val b : StringBuilder = ...
 * while((System.nanoTime % 33L) != 0){
 *     b.append((System.nanoTime % 33L).toString)
 * }
 * return b.toString
 * }}}
 * We count the number of joins per PC and if that value exceeds the configured threshold,
 * we completely abstract over the contents of the string builder.
 *
 * @author Michael Eichberg
 */
trait StringBuilderValues extends StringValues {
    domain: Domain with CorrelationalDomainSupport with Configuration with IntegerValuesDomain with TypedValuesFactory with TheClassHierarchy ⇒

    import StringBuilderValues._

    protected class StringBuilderValue(
            origin:          ValueOrigin,
            val builderType: ObjectType /*either StringBuilder oder StringBuffer*/ ,
            val builder:     JStringBuilder,
            refId:           RefId
    ) extends SObjectValue(origin, No, true, builderType, refId) {
        this: DomainStringValue ⇒

        assert(builder != null)
        assert((builderType eq JavaStringBuffer) || (builderType eq JavaStringBuilder))

        override def doJoinWithNonNullValueWithSameOrigin(
            joinPC: PC,
            other:  DomainSingleOriginReferenceValue
        ): Update[DomainSingleOriginReferenceValue] = {

            other match {
                case that: StringBuilderValue ⇒
                    if (this.builder == that.builder && this.refId == that.refId) {
                        NoUpdate
                    } else {
                        // we have to drop the concrete information...
                        // we are no longer able to track a concrete instance
                        StructuralUpdate(this.update())
                    }
                case _ ⇒
                    val result = super.doJoinWithNonNullValueWithSameOrigin(joinPC, other)
                    if (result.isStructuralUpdate) {
                        result
                    } else {
                        // This value and the other value may have a corresponding
                        // abstract representation (w.r.t. the next abstraction level!)
                        // but we still need to drop the concrete information and
                        // have to update the timestamp.
                        StructuralUpdate(result.value.update())
                    }
            }
        }

        override def abstractsOver(other: DomainValue): Boolean = {
            if (this eq other)
                return true;

            other match {
                case that: StringBuilderValue ⇒
                    that.builder == this.builder && (this.builderType eq that.builderType)
                case _ ⇒
                    false
            }
        }

        override def adapt(target: TargetDomain, vo: ValueOrigin): target.DomainValue =
            target match {
                case that: StringBuilderValues ⇒
                    that.StringBuilderValue(
                        this.origin,
                        this.builderType,
                        this.builder
                    ).asInstanceOf[target.DomainValue]
                case _ ⇒
                    super.adapt(target, vo)
            }

        override def equals(other: Any): Boolean = {
            other match {
                case that: StringBuilderValue ⇒
                    that.builder == this.builder && (this.builderType eq that.builderType)
                case _ ⇒ false
            }
        }

        override protected def canEqual(other: SObjectValue): Boolean = {
            other.isInstanceOf[StringBuilderValue]
        }

        override def hashCode: Int = super.hashCode * 71 + value.hashCode()

        override def toString(): String = {
            s"""${this.builderType.toJava}(origin=$origin;builder="$builder";refId=$refId)"""
        }

    }

    object StringBuilderValue {
        def unapply(value: StringBuilderValue): Option[String] = Some(value.builder.toString)
    }

    final def StringBuilderValue(
        origin:      ValueOrigin,
        builderType: ObjectType
    ): StringBuilderValue = {
        StringBuilderValue(origin, builderType, new JStringBuilder())
    }

    def StringBuilderValue(
        origin:      ValueOrigin,
        builderType: ObjectType,
        builder:     JStringBuilder
    ): StringBuilderValue
}

object StringBuilderValues {
    val JavaStringBuilder = ObjectType("java/lang/StringBuilder")
    val JavaStringBuffer = ObjectType("java/lang/StringBuffer")
}
