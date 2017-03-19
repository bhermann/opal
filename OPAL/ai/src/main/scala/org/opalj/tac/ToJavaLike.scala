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

import org.opalj.br.Method
import org.opalj.ai.AIResult
import org.opalj.br.ClassHierarchy
import org.opalj.br.Code
import org.opalj.br.ComputationalTypeReturnAddress

/**
 * Converts a list of three-address instructions into a Java like representation.
 *
 * @author Michael Eichberg
 * @author Roberts Kolosovs
 */
object ToJavaLike {

    private def callToJavaLike(name: String, params: List[Expr]): String = {
        params.reverse map { toJavaLikeExpr(_) } mkString (s".$name(", ", ", ")")
    }

    @inline final def toJavaLikeExpr(expr: Expr): String = {
        expr match {
            case dvr: DomainValueBasedVar ⇒ s"${dvr.name} /*${dvr.properties.toString()}*/"
            case v @ Var(name) ⇒
                if (v.cTpe == ComputationalTypeReturnAddress)
                    name+"/* return address */"
                else
                    name
            case Param(_ /*cTpe*/ , name) ⇒ name
            case IntConst(_, value)       ⇒ value.toString
            case LongConst(_, value)      ⇒ value.toString+"l"
            case FloatConst(_, value)     ⇒ value.toString+"f"
            case DoubleConst(_, value)    ⇒ value.toString+"d"
            case ClassConst(_, value)     ⇒ value.toJava+".class"
            case StringConst(_, value)    ⇒ s""""$value""""
            case NullExpr(_)              ⇒ "null"

            case InstanceOf(_, value, tpe) ⇒
                s"${toJavaLikeExpr(value)} instanceof ${tpe.asReferenceType.toJava}"

            case Checkcast(_, value, tpe) ⇒
                s"(${tpe.asReferenceType.toJava}) ${toJavaLikeExpr(value)}"

            case Compare(_, left, op, right) ⇒
                toJavaLikeExpr(left)+" "+op.toString()+" "+toJavaLikeExpr(right)

            case BinaryExpr(_, _ /*cTpe*/ , op, left, right) ⇒
                toJavaLikeExpr(left)+" "+op.toString()+" "+toJavaLikeExpr(right)

            case PrefixExpr(_, _, op, operand) ⇒
                op.toString()+" "+toJavaLikeExpr(operand)

            case PrimitiveTypecastExpr(_, baseTpe, operand) ⇒
                s"(${baseTpe.toJava}) ${toJavaLikeExpr(operand)}"

            case New(_, objTpe) ⇒
                s"new ${objTpe.simpleName}"

            case NewArray(_, counts, arrayType) ⇒
                val initializedDimensions = counts.size
                val dimensions = arrayType.dimensions
                val initializer =
                    counts.map(c ⇒ s"[${toJavaLikeExpr(c)}]").reverse.mkString("") +
                        ("[]" * (dimensions - initializedDimensions))
                s"new ${arrayType.drop(initializedDimensions).toJava}$initializer"

            case ArrayLoad(_, index, arrayRef) ⇒
                s"${toJavaLikeExpr(arrayRef)}[${toJavaLikeExpr(index)}]"

            case ArrayLength(_, arrayRef) ⇒
                s"${toJavaLikeExpr(arrayRef)}.length"

            case Invokedynamic(_, bootstrapMethod, name, descriptor, params) ⇒
                s"invokedynamic[${bootstrapMethod.toJava}]${callToJavaLike(name, params)}"

            case StaticFunctionCall(_, declClass, name, descriptor, params) ⇒
                declClass.toJava + callToJavaLike(name, params)

            case VirtualFunctionCall(_, declClass, name, descriptor, receiver, params) ⇒
                val call = callToJavaLike(name, params)
                toJavaLikeExpr(receiver)+"/*"+declClass.toJava+"*/"+call

            case NonVirtualFunctionCall(_, declClass, name, descriptor, receiver, params) ⇒
                val call = callToJavaLike(name, params)
                toJavaLikeExpr(receiver)+"/* (Non-Virtual) "+declClass.toJava+"*/"+call

            case GetStatic(_, declaringClass, name) ⇒
                s"${declaringClass.toJava}.$name"

            case GetField(_, declaringClass, name, receiver) ⇒
                s"${toJavaLikeExpr(receiver)}/*${declaringClass.toJava}*/.$name"
        }
    }

    @inline final def toJavaLikeStmt(stmt: Stmt): String = {
        stmt match {
            case Return(_)                   ⇒ "return;"
            case ReturnValue(_, expr)        ⇒ s"return ${toJavaLikeExpr(expr)};"
            case Throw(_, exc)               ⇒ s"throw ${toJavaLikeExpr(exc)};"

            case Nop(_)                      ⇒ ";"

            case MonitorEnter(_, objRef)     ⇒ s"monitorenter ${toJavaLikeExpr(objRef)};"
            case MonitorExit(_, objRef)      ⇒ s"monitorexit ${toJavaLikeExpr(objRef)};"

            case Goto(_, target)             ⇒ s"goto $target;"
            case JumpToSubroutine(_, target) ⇒ s"jsr $target;"
            case Ret(_, variable)            ⇒ s"ret ${toJavaLikeExpr(variable)};"
            case If(_, left, cond, right, target) ⇒
                s"if(${toJavaLikeExpr(left)} $cond ${toJavaLikeExpr(right)}) goto $target;"

            case Switch(_, defaultTarget, index, npairs) ⇒
                var result = "\n"
                for (x ← npairs) { result = result+"    "+x._1+": goto "+x._2+";\n" }
                result = result+"    default: goto "+defaultTarget+";\n"
                s"switch(${toJavaLikeExpr(index)}){$result}"

            case Assignment(_, variable, expr) ⇒
                s"${variable.name} = ${toJavaLikeExpr(expr)};"

            case ArrayStore(_, arrayRef, index, operandVar) ⇒
                s"${toJavaLikeExpr(arrayRef)}[${toJavaLikeExpr(index)}] = ${toJavaLikeExpr(operandVar)};"

            case PutStatic(_, declaringClass, name, value) ⇒
                s"${declaringClass.toJava}.$name = ${toJavaLikeExpr(value)}"

            case PutField(_, declaringClass, name, receiver, value) ⇒
                s"${toJavaLikeExpr(receiver)}/*${declaringClass.toJava}*/.$name = ${toJavaLikeExpr(value)}"

            case StaticMethodCall(_, declClass, name, descriptor, params) ⇒
                declClass.toJava + callToJavaLike(name, params) + ';'

            case VirtualMethodCall(_, declClass, name, descriptor, receiver, params) ⇒
                val call = callToJavaLike(name, params)
                toJavaLikeExpr(receiver)+"/*"+declClass.toJava+"*/"+call + ';'

            case NonVirtualMethodCall(_, declClass, name, descriptor, receiver, params) ⇒
                val call = callToJavaLike(name, params)
                toJavaLikeExpr(receiver)+"/* (Non-Virtual) "+declClass.toJava+"*/"+call + ';'
        }
    }

    /**
     * Converts the quadruples representation into Java-like code.
     */
    def apply(stmts: Array[Stmt]): String = {
        apply(stmts, true).mkString("\n")
    }

    /**
     * Converts each statement into a Java-like statement.
     */
    def apply(stmts: Array[Stmt], indented: Boolean): Array[String] = {

        val max = stmts.size
        val javaLikeCode = new Array[String](max)
        var index = 0;
        while (index < max) {
            def qualify(javaLikeStmt: String): String = {
                if (indented)
                    f"$index%5d: ${javaLikeStmt.replace("\n", "\n       ")}"
                else
                    s"$index: $javaLikeStmt"
            }

            javaLikeCode(index) = qualify(toJavaLikeStmt(stmts(index)))

            index += 1
        }

        javaLikeCode
    }

    /**
     * Converts each statement into a Java-like statement.
     */
    def apply(stmts: IndexedSeq[Stmt], indented: Boolean): Array[String] = {
        apply(stmts.toArray, indented)
    }

    def apply(
        method:         Method,
        classHierarchy: ClassHierarchy   = Code.BasicClassHierarchy,
        aiResult:       Option[AIResult] = None
    ): String = {
        val optimizations = List(SimplePropagation)
        val quadruples = AsQuadruples(method, classHierarchy, aiResult, optimizations, false)
        ToJavaLike(quadruples._1)
    }

}
