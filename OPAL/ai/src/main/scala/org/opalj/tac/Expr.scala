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
package tac

import org.opalj.br.ComputationalType
import org.opalj.br.ComputationalTypeInt
import org.opalj.br.ComputationalTypeLong
import org.opalj.br.ComputationalTypeFloat
import org.opalj.br.ComputationalTypeDouble
import org.opalj.br.ComputationalTypeReference
import org.opalj.br.Type
import org.opalj.br.FieldType
import org.opalj.br.IntegerType
import org.opalj.br.BaseType
import org.opalj.br.LongType
import org.opalj.br.FloatType
import org.opalj.br.DoubleType
import org.opalj.br.ObjectType
import org.opalj.br.ReferenceType
import org.opalj.br.ArrayType
import org.opalj.br.MethodDescriptor
import org.opalj.br.BootstrapMethod
import org.opalj.br.MethodHandle
import org.opalj.br.PC

/**
 * Represents an expression. In general, every expression should be a simple expression, where
 * the child expressions are just [[Var]]s or [[Const]]s.
 * However, when the code is going to be transformed to human readable code (e.g., Java oder
 * Scala), then it is possible to build up complex/nested expressions '''after''' all
 * transformations and static analyses have been performed.
 *
 * @tparam V
 */
trait Expr[+V <: Var[V]] extends ASTNode[V] {

    /**
     * The computational type of the underlying value.
     *
     * I.e., an approximation of the type of the underlying value. It is the best
     * type information directly available. The precision of the type information
     * depends on the number of pre-/post-processing steps that are done.
     */
    def cTpe: ComputationalType

    /**
     * The number of sub expression directly referenced by this expression.
     * A unary expression has one sub expression (the operand), a binary expression has
     * two and a method has as many as explicit and implicit (`this`) parameters exist.
     *
     * @note Transitive dependencies are not counted.
     */
    def subExprCount: Int

    /**
     * Returns the sub expression with the given index; if the index is wrong the
     * result is undefined!
     */
    def subExpr(index: Int): Expr[V]

    private[tac] def remapIndexes(pcToIndex: Array[Int]): Unit = {}

    // TYPE CAST (RELATED) EXPRESSIONS

    /** `true` if ''this'' expression is a [[Var]]. */
    def isValueExpression: Boolean
    def isVar: Boolean
    def asVar: V = throw new ClassCastException();

    def asInstanceOf: InstanceOf[V] = throw new ClassCastException();
    def asCompare: Compare[V] = throw new ClassCastException();
    def asParam: Param = throw new ClassCastException();
    def asMethodTypeConst: MethodTypeConst = throw new ClassCastException();
    def asMethodHandleConst: MethodHandleConst = throw new ClassCastException();
    def isIntConst: Boolean = false
    def asIntConst: IntConst = throw new ClassCastException();
    def isLongConst: Boolean = false
    def asLongConst: LongConst = throw new ClassCastException();
    def isFloatConst: Boolean = false
    def asFloatConst: FloatConst = throw new ClassCastException();
    def isDoubleConst: Boolean = false
    def asDoubleConst: DoubleConst = throw new ClassCastException();
    def isStringConst: Boolean = false
    def asStringConst: StringConst = throw new ClassCastException();
    def isClassConst: Boolean = false
    def asClassConst: ClassConst = throw new ClassCastException();
    def isNullExpr: Boolean = false
    def asNullExpr: NullExpr = throw new ClassCastException();
    def asBinaryExpr: BinaryExpr[V] = throw new ClassCastException();
    def asPrefixExpr: PrefixExpr[V] = throw new ClassCastException();
    def asPrimitiveTypeCastExpr: PrimitiveTypecastExpr[V] = throw new ClassCastException();
    def asNew: New = throw new ClassCastException();
    def asNewArray: NewArray[V] = throw new ClassCastException();
    def asArrayLoad: ArrayLoad[V] = throw new ClassCastException();
    def asArrayLength: ArrayLength[V] = throw new ClassCastException();
    def asGetField: GetField[V] = throw new ClassCastException();
    def asGetStatic: GetStatic = throw new ClassCastException();
    def asInvokedynamic: Invokedynamic[V] = throw new ClassCastException();
    def asFunctionCall: FunctionCall[V] = throw new ClassCastException();
    def asStaticFunctionCall: StaticFunctionCall[V] = throw new ClassCastException();
    def asInstanceFunctionCall: InstanceFunctionCall[V] = throw new ClassCastException();
    def asNonVirtualFunctionCall: NonVirtualFunctionCall[V] = throw new ClassCastException();
    def asVirtualFunctionCall: VirtualFunctionCall[V] = throw new ClassCastException();
}

/**
 * An `instance of` expression as defined by the JVM specification.
 */
case class InstanceOf[+V <: Var[V]](pc: PC, value: Expr[V], cmpTpe: ReferenceType) extends Expr[V] {

    final override def asInstanceOf: this.type = this
    final override def astID: Int = InstanceOf.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeInt
    final override def isSideEffectFree: Boolean = true
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def subExprCount: Int = 1
    final override def subExpr(index: Int): Expr[V] = value

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        value.remapIndexes(pcToIndex)
    }

    override def toString: String = s"InstanceOf(pc=$pc,$value,${cmpTpe.toJava})"

}
object InstanceOf { final val ASTID = -2 }

/**
 * A comparison of two values.
 */
case class Compare[+V <: Var[V]](
        pc:        PC,
        left:      Expr[V],
        condition: RelationalOperator,
        right:     Expr[V]
) extends Expr[V] {

    final override def asCompare: this.type = this
    final override def astID: Int = Compare.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeInt
    final override def isSideEffectFree: Boolean = true
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def subExprCount: Int = 2
    final override def subExpr(index: Int): Expr[V] = if (index == 0) left else right

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        left.remapIndexes(pcToIndex)
        right.remapIndexes(pcToIndex)
    }

    override def toString: String = s"Compare(pc=$pc,$left,$condition,$right)"
}
object Compare { final val ASTID = -4 }

trait ValueExpr[+V <: Var[V]] extends Expr[V] {

    final override def isValueExpression: Boolean = true

    final override def subExprCount: Int = 0
    final override def subExpr(index: Int): Expr[V] = throw new IndexOutOfBoundsException();
}

/**
 * Explicit initialization of a parameter. Parameter statements '''are only used by the naive
 * representation ([[TACNaive]])''' where it is necessary to perform an initial initialization
 * of the register values. In case of [[TACAI]], usage of parameters are implicitly encoded using
 * parameter origins (see [[DUVar]]).
 */
case class Param(cTpe: ComputationalType, name: String) extends ValueExpr[Nothing] {
    final override def asParam: this.type = this
    final override def astID: Int = Param.ASTID
    final override def isVar: Boolean = false
    final override def isSideEffectFree: Boolean = true
}

object Param { final val ASTID = -1 }

/**
 * A constant value expression.
 */
sealed abstract class Const extends ValueExpr[Nothing] {
    final override def isVar: Boolean = false
    final override def isSideEffectFree: Boolean = true
    def tpe: Type
}

case class MethodTypeConst(pc: PC, value: MethodDescriptor) extends Const {
    final override def asMethodTypeConst: this.type = this
    final override def astID: Int = MethodTypeConst.ASTID
    final override def tpe = ObjectType.MethodType
    final override def cTpe: ComputationalType = ComputationalTypeReference
    override def toString: String = s"MethodTypeConst(pc=$pc,${value.toJava})"
}
object MethodTypeConst { final val ASTID = -10 }

case class MethodHandleConst(pc: PC, value: MethodHandle) extends Const {
    final override def asMethodHandleConst: this.type = this
    final override def astID: Int = MethodHandleConst.ASTID
    final override def tpe = ObjectType.MethodHandle
    final override def cTpe: ComputationalType = ComputationalTypeReference
    override def toString: String = s"MethodHandleConst(pc=$pc,${value.toJava})"
}
object MethodHandleConst { final val ASTID = -11 }

sealed abstract class SimpleValueConst extends Const

case class IntConst(pc: PC, value: Int) extends SimpleValueConst {
    final override def isIntConst: Boolean = true
    final override def asIntConst: this.type = this
    final override def astID: Int = IntConst.ASTID
    final override def tpe = IntegerType
    final override def cTpe: ComputationalType = ComputationalTypeInt
    override def toString: String = s"IntConst(pc=$pc,$value)"
}
object IntConst { final val ASTID = -5 }

case class LongConst(pc: PC, value: Long) extends SimpleValueConst {
    final override def isLongConst: Boolean = true
    final override def asLongConst: this.type = this
    final override def astID: Int = LongConst.ASTID
    final override def tpe = LongType
    final override def cTpe: ComputationalType = ComputationalTypeLong
    override def toString: String = s"LongConst(pc=$pc,$value)"
}
object LongConst { final val ASTID = -6 }

case class FloatConst(pc: PC, value: Float) extends SimpleValueConst {
    final override def isFloatConst: Boolean = true
    final override def asFloatConst: this.type = this
    final override def astID: Int = FloatConst.ASTID
    final override def tpe = FloatType
    final override def cTpe: ComputationalType = ComputationalTypeFloat
    override def toString: String = s"FloatConst(pc=$pc,$value)"
}
object FloatConst { final val ASTID = -7 }

case class DoubleConst(pc: PC, value: Double) extends SimpleValueConst {
    final override def isDoubleConst: Boolean = true
    final override def asDoubleConst: this.type = this
    final override def astID: Int = DoubleConst.ASTID
    final override def tpe = DoubleType
    final override def cTpe: ComputationalType = ComputationalTypeDouble
    override def toString: String = s"DoubleConst(pc=$pc,$value)"
}
object DoubleConst { final val ASTID = -8 }

case class StringConst(pc: PC, value: String) extends SimpleValueConst {
    final override def isStringConst: Boolean = true
    final override def asStringConst: this.type = this
    final override def astID: Int = StringConst.ASTID
    final override def tpe = ObjectType.String
    final override def cTpe: ComputationalType = ComputationalTypeReference
    override def toString: String = s"StringConst(pc=$pc,$value)"
}
object StringConst { final val ASTID = -9 }

case class ClassConst(pc: PC, value: ReferenceType) extends SimpleValueConst {
    final override def isClassConst: Boolean = true
    final override def asClassConst: this.type = this
    final override def astID: Int = ClassConst.ASTID
    final override def tpe = ObjectType.Class
    final override def cTpe: ComputationalType = ComputationalTypeReference
    override def toString: String = s"ClassConst(pc=$pc,${value.toJava})"
}
object ClassConst { final val ASTID = -12 }

case class NullExpr(pc: PC) extends SimpleValueConst {
    final override def isNullExpr: Boolean = true
    final override def asNullExpr: this.type = this
    final override def astID: Int = NullExpr.ASTID
    final override def tpe = ObjectType.Object // TODO Should we introduce a fake type such as "java.null"
    final override def cTpe: ComputationalType = ComputationalTypeReference
    override def toString: String = s"NullExpr(pc=$pc)"
}
object NullExpr { final val ASTID = -13 }

/**
 * @param cTpe  The computational type of the result of the binary expression if the expression
 *              succeeds.
 */
case class BinaryExpr[+V <: Var[V]](
        pc:   PC,
        cTpe: ComputationalType,
        op:   BinaryArithmeticOperator,
        left: Expr[V], right: Expr[V]
) extends Expr[V] {

    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def asBinaryExpr: this.type = this
    final override def astID: Int = BinaryExpr.ASTID
    final override def subExprCount: Int = 2
    final override def subExpr(index: Int): Expr[V] = if (index == 0) left else right

    final override def isSideEffectFree: Boolean = {
        // For now, we have to consider a potential "div by zero exception";
        // a better handling is only possible if we know that the value is not zero (0).
        (op != BinaryArithmeticOperators.Divide && op != BinaryArithmeticOperators.Modulo) ||
            (right.cTpe != ComputationalTypeInt && right.cTpe != ComputationalTypeLong) ||
            (right.isLongConst && right.asLongConst.value != 0) ||
            (right.isIntConst && right.asIntConst.value != 0)
    }

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        left.remapIndexes(pcToIndex)
        right.remapIndexes(pcToIndex)
    }

    override def toString: String = s"BinaryExpr(pc=$pc,$cTpe,$left,$op,$right)"
}
object BinaryExpr { final val ASTID = -14 }

/**
 * @param cTpe The computational type of the result of the prefix expression.
 */
case class PrefixExpr[+V <: Var[V]](
        pc:      PC,
        cTpe:    ComputationalType,
        op:      UnaryArithmeticOperator,
        operand: Expr[V]
) extends Expr[V] {

    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def asPrefixExpr: this.type = this
    final override def astID: Int = PrefixExpr.ASTID
    final override def isSideEffectFree: Boolean = true
    final override def subExprCount: Int = 1
    final override def subExpr(index: Int): Expr[V] = operand

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        operand.remapIndexes(pcToIndex)
    }

    override def toString: String = s"PrefixExpr(pc=$pc,$cTpe,$op,$operand)"
}
object PrefixExpr { final val ASTID = -15 }

case class PrimitiveTypecastExpr[+V <: Var[V]](
        pc:        PC,
        targetTpe: BaseType,
        operand:   Expr[V]
) extends Expr[V] {

    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def asPrimitiveTypeCastExpr: this.type = this
    final override def astID: Int = PrimitiveTypecastExpr.ASTID
    final override def cTpe: ComputationalType = targetTpe.computationalType
    final override def isSideEffectFree: Boolean = true
    final override def subExprCount: Int = 1
    final override def subExpr(index: Int): Expr[V] = operand

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        operand.remapIndexes(pcToIndex)
    }

    override def toString: String = s"PrimitiveTypecastExpr(pc=$pc,$targetTpe,$operand)"
}
object PrimitiveTypecastExpr { final val ASTID = -16 }

/**
 * Allocates memory for the (non-abstract) given object. Note, that the call of the separator
 * is done later and therefore the object is not considered to be properly initialized and –
 * therefore – no further operations other than the call of a constructor are allowed.
 */
case class New(pc: PC, tpe: ObjectType) extends Expr[Nothing] {

    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def asNew: this.type = this
    final override def astID: Int = New.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeReference
    final override def subExprCount: Int = 0
    final override def subExpr(index: Int): Nothing = throw new IndexOutOfBoundsException();

    /**
     * Returns `false` because an `OutOfMemoryError` may be thrown.
     */
    final override def isSideEffectFree: Boolean = false

    override def toString: String = s"New(pc=$pc,${tpe.toJava})"
}
object New { final val ASTID = -17 }

trait ArrayExpr[+V <: Var[V]] extends Expr[V] {
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
}

/**
 * @param counts Encodes the number of dimensions that are initialized and the size of the
 *               respective dimension.
 * @param tpe The type of the array. The number of dimensions is always `>= count.size`.
 */
case class NewArray[+V <: Var[V]](pc: PC, counts: Seq[Expr[V]], tpe: ArrayType) extends ArrayExpr[V] {

    final override def asNewArray: this.type = this
    final override def astID: Int = NewArray.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeReference
    final override def subExprCount: Int = counts.size
    final override def subExpr(index: Int): Expr[V] = counts(index)

    /**
     * Returns `false` by default, because a `NewArray` instruction may throw
     * `NegativeIndexSizeException` (and also `OutOfMemoryError`.)
     */
    final override def isSideEffectFree: Boolean = false

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        counts.foreach { c ⇒ c.remapIndexes(pcToIndex) }
    }

    override def toString: String = {
        s"NewArray(pc=$pc,${counts.mkString("[", ",", "]")},${tpe.toJava})"
    }
}
object NewArray { final val ASTID = -18 }

case class ArrayLoad[+V <: Var[V]](pc: PC, index: Expr[V], arrayRef: Expr[V]) extends ArrayExpr[V] {

    final override def asArrayLoad: this.type = this
    final override def astID: Int = ArrayLoad.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeReference
    final override def isSideEffectFree: Boolean = false
    final override def subExprCount: Int = 2
    final override def subExpr(index: Int): Expr[V] = if (index == 0) this.index else arrayRef

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        index.remapIndexes(pcToIndex)
        arrayRef.remapIndexes(pcToIndex)
    }

    override def toString: String = s"ArrayLoad(pc=$pc,$index,$arrayRef)"
}
object ArrayLoad { final val ASTID = -19 }

case class ArrayLength[+V <: Var[V]](pc: PC, arrayRef: Expr[V]) extends ArrayExpr[V] {

    final override def asArrayLength: this.type = this
    final override def astID: Int = ArrayLength.ASTID
    final override def cTpe: ComputationalType = ComputationalTypeInt
    final override def isSideEffectFree: Boolean = { assert(arrayRef.isVar); false /* potential NPE */ }
    final override def subExprCount: Int = 1
    final override def subExpr(index: Int): Expr[V] = arrayRef

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        arrayRef.remapIndexes(pcToIndex)
    }

    override def toString: String = s"ArrayLength(pc=$pc,$arrayRef)"
}
object ArrayLength { final val ASTID = -20 }

abstract class FieldRead[+V <: Var[V]] extends Expr[V] {

    final override def cTpe: ComputationalType = declaredFieldType.computationalType
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false

    def declaredFieldType: FieldType

}

case class GetField[+V <: Var[V]](
        pc:                PC,
        declaringClass:    ObjectType,
        name:              String,
        declaredFieldType: FieldType,
        objRef:            Expr[V]
) extends FieldRead[V] {

    final override def asGetField: this.type = this
    final override def astID: Int = GetField.ASTID
    final override def subExprCount: Int = 1
    final override def subExpr(index: Int): Expr[V] = objRef

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        objRef.remapIndexes(pcToIndex)
    }

    final def isSideEffectFree: Boolean = {
        assert(objRef.isValueExpression)
        // IMPROVE if the access is non-null, it is side-effect free
        false
    }

    override def toString: String = {
        s"GetField(pc=$pc,${declaringClass.toJava},$name,${declaredFieldType.toJava},$objRef)"
    }
}
object GetField { final val ASTID = -21 }

case class GetStatic(
        pc:                PC,
        declaringClass:    ObjectType,
        name:              String,
        declaredFieldType: FieldType
) extends FieldRead[Nothing] {

    final override def asGetStatic: this.type = this
    final override def astID: Int = GetStatic.ASTID
    final override def isSideEffectFree: Boolean = true
    final override def subExprCount: Int = 0
    final override def subExpr(index: Int): Nothing = throw new IndexOutOfBoundsException();

    override def toString: String = {
        s"GetStatic(pc=$pc,${declaringClass.toJava},$name,${declaredFieldType.toJava})"
    }
}
object GetStatic { final val ASTID = -22 }

case class Invokedynamic[+V <: Var[V]](
        pc:              PC,
        bootstrapMethod: BootstrapMethod,
        name:            String,
        descriptor:      MethodDescriptor,
        params:          Seq[Expr[V]]
) extends Expr[V] {

    final override def asInvokedynamic: this.type = this
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def astID: Int = Invokedynamic.ASTID
    final override def cTpe: ComputationalType = descriptor.returnType.computationalType
    // IMPROVE [FUTURE] Use some analysis to determine if a method call is side effect free
    final override def isSideEffectFree: Boolean = false
    final override def subExprCount: Int = params.size
    final override def subExpr(index: Int): Expr[V] = params(index)

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        params.foreach { p ⇒ p.remapIndexes(pcToIndex) }
    }

    override def toString: String = {
        val sig = descriptor.toJava(name)
        val params = this.params.mkString("(", ",", ")")
        s"Invokedynamic(pc=$pc,$bootstrapMethod,$sig,$params)"
    }
}
object Invokedynamic { final val ASTID = -23 }

sealed abstract class FunctionCall[+V <: Var[V]] extends Expr[V] with Call[V] {

    final override def cTpe: ComputationalType = descriptor.returnType.computationalType
    final override def isValueExpression: Boolean = false
    final override def isVar: Boolean = false
    final override def subExprCount: Int = params.size
    final override def subExpr(index: Int): Expr[V] = params(index)
    final override def asFunctionCall: this.type = this
}

sealed abstract class InstanceFunctionCall[+V <: Var[V]] extends FunctionCall[V] {

    def receiver: Expr[V]
    final override def asInstanceFunctionCall: this.type = this
}

/**
 * An instance based method call which does not require virtual method lookup. In other
 * words the target method is either directly found in the specified class or a super
 * class thereof. (Basically corresponds to an invokespecial at bytecode level.)
 *
 * @param pc The pc of the underlying, original bytecode instruction. Primarily useful to
 *           do a lookup in the line-/local-variable tables.
 * @param declaringClass The declaring class of the target method.
 * @param isInterface `true` if the declaring class defines an interface type.
 *                   (Required since Java 8.)
 * @param name The name of the target method.
 * @param descriptor The descriptor.
 * @param receiver The receiver object.
 * @param params The parameters of the method call (including the implicit `this` reference.)
 * @tparam V The type of the Var used by this representation.
 */
case class NonVirtualFunctionCall[+V <: Var[V]](
        pc:             PC,
        declaringClass: ObjectType,
        isInterface:    Boolean,
        name:           String,
        descriptor:     MethodDescriptor,
        receiver:       Expr[V],
        params:         Seq[Expr[V]]
) extends InstanceFunctionCall[V] {

    final override def asNonVirtualFunctionCall: this.type = this
    final override def astID: Int = NonVirtualFunctionCall.ASTID
    final override def isSideEffectFree: Boolean = false

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        receiver.remapIndexes(pcToIndex)
        params.foreach { p ⇒ p.remapIndexes(pcToIndex) }
    }

    override def toString: String = {
        val sig = descriptor.toJava(name)
        val declClass = declaringClass.toJava
        val params = this.params.mkString("(", ",", ")")
        s"NonVirtualFunctionCall(pc=$pc,$declClass,isInterface=$isInterface,$sig,$receiver,$params)"
    }
}
object NonVirtualFunctionCall { final val ASTID = -24 }

case class VirtualFunctionCall[+V <: Var[V]](
        pc:             PC,
        declaringClass: ReferenceType,
        isInterface:    Boolean,
        name:           String,
        descriptor:     MethodDescriptor,
        receiver:       Expr[V],
        params:         Seq[Expr[V]]
) extends InstanceFunctionCall[V] {

    final override def asVirtualFunctionCall: this.type = this
    final override def astID: Int = VirtualFunctionCall.ASTID
    final override def isSideEffectFree: Boolean = false

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        receiver.remapIndexes(pcToIndex)
        params.foreach { p ⇒ p.remapIndexes(pcToIndex) }
    }

    override def toString: String = {
        val sig = descriptor.toJava(name)
        val declClass = declaringClass.toJava
        val params = this.params.mkString("(", ",", ")")
        s"VirtualFunctionCall(pc=$pc,$declClass,isInterface=$isInterface,$sig,$receiver,$params)"
    }
}
object VirtualFunctionCall { final val ASTID = -25 }

case class StaticFunctionCall[+V <: Var[V]](
        pc:             PC,
        declaringClass: ObjectType,
        isInterface:    Boolean,
        name:           String,
        descriptor:     MethodDescriptor,
        params:         Seq[Expr[V]]
) extends FunctionCall[V] {

    final override def asStaticFunctionCall: this.type = this
    final override def astID: Int = StaticFunctionCall.ASTID
    final override def isSideEffectFree: Boolean = false

    private[tac] override def remapIndexes(pcToIndex: Array[Int]): Unit = {
        params.foreach { p ⇒ p.remapIndexes(pcToIndex) }
    }

    override def toString: String = {
        val sig = descriptor.toJava(name)
        val declClass = declaringClass.toJava
        val params = this.params.mkString("(", ",", ")")
        s"StaticFunctionCall(pc=$pc,$declClass,isInterface=$isInterface,$sig,$params)"
    }
}
object StaticFunctionCall { final val ASTID = -26 }

/**
 * Represents a variable. Depending on the concrete usage, it is possible to distinguish between
 * a use and/or definition site. Typically, `V` is directly bound by the direct subtypes of Var.
 *
 * @example
 * {{{
 *     trait MyVar extends Var[MyVar]
 * }}}
 *
 * @tparam V Specifies the type of `Var` used by the three address representation. `V` is also
 *           the self type.
 */
trait Var[+V <: Var[V]] extends ValueExpr[V] { this: V ⇒

    final override def isVar: Boolean = true
    final override def asVar: V = this
    final override def astID: Int = Var.ASTID

    /**
     * A ''human readable'' name of the local variable.
     */
    def name: String
}

object Var {

    final val ASTID = -27

    def unapply[V <: Var[V]](variable: Var[V]): Some[String] = Some(variable.name)

}
