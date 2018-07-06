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

import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.SomeProject

import scala.collection.Set

/**
 * For a given [[DeclaredMethod]], and for each call site (represented by the PC), the set of methods
 * that are possible call targets.
 *
 * @author Florian Kuebler
 */
sealed trait CallersPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = CallersProperty
}

sealed trait CallersProperty extends Property with OrderedProperty with CallersPropertyMetaInformation {

    def hasCallersWithUnknownContext: Boolean

    def hasVMLevelCallers: Boolean

    def size: Int

    def callers(implicit declaredMethods: DeclaredMethods): Traversable[(DeclaredMethod, Int /*PC*/ )] //TODO: maybe use traversable instead of set

    def updated(caller: DeclaredMethod, pc: Int)(implicit declaredMethods: DeclaredMethods): CallersProperty

    def updateWithUnknownContext(): CallersProperty

    def updateVMLevelCall(): CallersProperty

    override def toString: String = {
        s"Callers(size=${this.size})"
    }

    final def key: PropertyKey[CallersProperty] = CallersProperty.key

    /**
     * Tests if this property is equal or better than the given one (better means that the
     * value is above the given value in the underlying lattice.)
     */
    override def checkIsEqualOrBetterThan(e: Entity, other: CallersProperty): Unit = {
        if (other.size < size) //todo if (!pcMethodPairs.subsetOf(other.pcMethodPairs))
            throw new IllegalArgumentException(s"$e: illegal refinement of property $other to $this")
    }
}

trait CallersWithoutUnknownContext extends CallersProperty {
    override def hasCallersWithUnknownContext: Boolean = false
}

trait CallersWithUnknownContext extends CallersProperty {
    override def hasCallersWithUnknownContext: Boolean = true
    override def updateWithUnknownContext(): CallersWithUnknownContext = this
}

trait CallersWithVMLevelCall extends CallersProperty {
    override def hasVMLevelCallers: Boolean = true
    override def updateVMLevelCall(): CallersWithVMLevelCall = this
}

trait CallersWithoutVMLevelCall extends CallersProperty {
    override def hasVMLevelCallers: Boolean = true
}

trait EmptyConcreteCallers extends CallersProperty {
    override def size: Int = 0

    override def callers(implicit declaredMethods: DeclaredMethods): Traversable[(DeclaredMethod, Int)] = {
        Nil
    }

    override def updated(caller: DeclaredMethod, pc: Int)(implicit declaredMethods: DeclaredMethods): CallersProperty = {

        if (!hasCallersWithUnknownContext && !hasVMLevelCallers) {
            new CallersOnlyWithConcreteCallers(Set(CallersProperty.toLong(declaredMethods.methodID(caller), pc)))
        } else {
            ???
        }
    }
}

object NoCallers extends EmptyConcreteCallers with CallersWithoutUnknownContext with CallersWithoutVMLevelCall {
    override def updateVMLevelCall(): CallersWithVMLevelCall = OnlyVMLevelCallers

    override def updateWithUnknownContext(): CallersWithUnknownContext = OnlyCallersWithUnknownContext
}

object OnlyCallersWithUnknownContext
        extends EmptyConcreteCallers with CallersWithUnknownContext with CallersWithoutVMLevelCall {
    override def updateVMLevelCall(): CallersWithVMLevelCall = OnlyVMCallersAndWithUnknownContext
}

object OnlyVMLevelCallers
        extends EmptyConcreteCallers with CallersWithoutUnknownContext with CallersWithVMLevelCall {
    override def updateWithUnknownContext(): CallersWithUnknownContext = OnlyVMCallersAndWithUnknownContext
}

object OnlyVMCallersAndWithUnknownContext
    extends EmptyConcreteCallers with CallersWithVMLevelCall with CallersWithUnknownContext

trait CallersImplementation extends CallersProperty {
    val encodedCallers: Set[Long /* MethodId + PC*/ ]
    override def size: Int = encodedCallers.size

    override def callers(
        implicit
        declaredMethods: DeclaredMethods
    ): Traversable[(DeclaredMethod, Int /*PC*/ )] = {
        for {
            encodedPair ← encodedCallers
            (mId, pc) = CallersProperty.toMethodAndPc(encodedPair)
        } yield declaredMethods(mId) → pc
    }
}

class CallersOnlyWithConcreteCallers(
        val encodedCallers: Set[Long /*MethodId + PC*/ ]
) extends CallersImplementation with CallersWithoutVMLevelCall with CallersWithoutUnknownContext {

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = {
        val newCallers = this.encodedCallers +
            CallersProperty.toLong(declaredMethods.methodID(caller), pc)
        new CallersOnlyWithConcreteCallers(newCallers)
    }

    override def updateWithUnknownContext(): CallersProperty =
        CallersImplWithOtherCalls(
            encodedCallers,
            hasVMLevelCallers = false,
            hasCallersWithUnknownContext = true
        )

    override def updateVMLevelCall(): CallersProperty =
        CallersImplWithOtherCalls(
            encodedCallers,
            hasVMLevelCallers = true,
            hasCallersWithUnknownContext = false
        )
}

class CallersImplWithOtherCalls(
        val encodedCallers: Set[Long /*MethodId + PC*/ ],
        val coding:         Byte // last bit vm lvl, second last bit unknown context
) extends CallersImplementation {

    override def hasVMLevelCallers: Boolean = (coding & 1) != 0

    override def hasCallersWithUnknownContext: Boolean = (coding & 2) != 0

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = {
        val newCallers = this.encodedCallers +
            CallersProperty.toLong(declaredMethods.methodID(caller), pc)
        new CallersImplWithOtherCalls(newCallers, coding)
    }

    override def updateVMLevelCall(): CallersProperty =
        new CallersImplWithOtherCalls(encodedCallers, (coding | 1).toByte)

    override def updateWithUnknownContext(): CallersProperty =
        new CallersImplWithOtherCalls(encodedCallers, (coding | 2).toByte)
}

object CallersImplWithOtherCalls {
    def apply(
        encodedCallers:               Set[Long /* MethodId + PC */ ],
        hasVMLevelCallers:            Boolean,
        hasCallersWithUnknownContext: Boolean
    ): CallersImplWithOtherCalls = {
        assert(hasVMLevelCallers | hasCallersWithUnknownContext)

        val vmLvlCallers = if (hasVMLevelCallers) 1 else 0
        val unknownContext = if (hasCallersWithUnknownContext) 2 else 0

        new CallersImplWithOtherCalls(
            encodedCallers, (vmLvlCallers & unknownContext).toByte
        )
    }
}

class LowerBoundCallers(
        project: SomeProject, method: DeclaredMethod
) extends CallersWithUnknownContext with CallersWithVMLevelCall {

    override lazy val size: Int = {
        //callees.size * project.allMethods.size
        // todo this is for performance improvement only
        Int.MaxValue
    }

    override def callers(
        implicit
        declaredMethods: DeclaredMethods
    ): Traversable[(DeclaredMethod, Int /*PC*/ )] = {
        ??? // todo
    }

    override def updated(
        caller: DeclaredMethod, pc: Int
    )(implicit declaredMethods: DeclaredMethods): CallersProperty = this
}

object CallersProperty extends CallersPropertyMetaInformation {

    final val key: PropertyKey[CallersProperty] = {
        PropertyKey.create(
            "Callers",
            (ps: PropertyStore, reason: FallbackReason, m: DeclaredMethod) ⇒ reason match {
                case PropertyIsNotComputedByAnyAnalysis ⇒
                    CallersProperty.fallback(m, ps.context[SomeProject])
                case PropertyIsNotDerivedByPreviouslyExecutedAnalysis ⇒
                    NoCallers
            },
            (_: PropertyStore, eps: EPS[DeclaredMethod, CallersProperty]) ⇒ eps.ub,
            (_: PropertyStore, _: DeclaredMethod) ⇒ None
        )
    }

    def fallback(m: DeclaredMethod, p: SomeProject): CallersProperty = {
        new LowerBoundCallers(p, m)
    }

    def toLong(methodId: Int, pc: Int): Long = {
        (methodId.toLong << 32) | (pc & 0xFFFFFFFFL)
    }
    def toMethodAndPc(methodAndPc: Long): (Int, Int) = {
        ((methodAndPc >> 32).toInt, methodAndPc.toInt)
    }
}
