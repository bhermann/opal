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
package da

import scala.xml.Node

/**
 * @author Michael Eichberg
 * @author Wael Alkhatib
 * @author Isbel Isbel
 * @author Noorulla Sharief
 */
trait TypeAnnotationTarget {

    def attribute_length: Int

    def tag: Int

    def toXHTML(implicit cp: Constant_Pool): Node

}

//______________________________
// type_parameter_target

trait Type_Parameter_Target extends TypeAnnotationTarget {

    def type_parameter_index: Int

    final override def attribute_length: Int = 1 + 1
}

case class ParameterDeclarationOfClassOrInterface(
        type_parameter_index: Int
) extends Type_Parameter_Target {

    final override def tag: Int = 0x00

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(type_parameter_index).toString(cp) }</span>
    }
}

case class ParameterDeclarationOfMethodOrConstructor(
        type_parameter_index: Int
) extends Type_Parameter_Target {

    final override def tag: Int = 0x01

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(type_parameter_index).toString(cp) }</span>
    }
}

//______________________________
// supertype_target
case class Supertype_Target(supertype_index: Constant_Pool_Index) extends TypeAnnotationTarget {

    final override def attribute_length: Int = 1 + 2

    final override def tag: Int = 0x10

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(supertype_index).toString(cp) }</span>
    }
}

//______________________________
// type_parameter_bound_target

trait Type_Parameter_Bound_Target extends TypeAnnotationTarget {
    def type_parameter_index: Int
    def bound_index: Int

    final override def attribute_length: Int = 1 + 1 + 1
}
case class TypeBoundOfParameterDeclarationOfClassOrInterface(
        type_parameter_index: Int,
        bound_index:          Int
) extends Type_Parameter_Bound_Target {

    final override def tag: Int = 0x11

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(type_parameter_index).toString(cp)+"-"+cp(bound_index).toString(cp) }</span>
    }
}
case class TypeBoundOfParameterDeclarationOfMethodOrConstructor(
        type_parameter_index: Int,
        bound_index:          Int
) extends Type_Parameter_Bound_Target {

    final override def tag: Int = 0x12

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(type_parameter_index).toString(cp)+"-"+cp(bound_index).toString(cp) }</span>
    }
}

//______________________________
// empty_target
trait Empty_Target extends TypeAnnotationTarget {
    final override def attribute_length: Int = 1
}

case object FieldDeclaration extends Empty_Target {

    final override def tag: Int = 0x13

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">Field Decleration</span>
    }
}
case object ReturnType extends Empty_Target {

    final override def tag: Int = 0x14

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">Return Type</span>
    }
}
case object ReceiverType extends Empty_Target {

    final override def tag: Int = 0x15

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">Receiver Type</span>
    }
}

//______________________________
// formal_parameter_target
case class Formal_Parameter_Target(formal_parameter_index: Int) extends TypeAnnotationTarget {

    final override def attribute_length: Int = 1 + 1

    final override def tag: Int = 0x16

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(formal_parameter_index).toString(cp) }</span>
    }
}

//______________________________
// throws_target
case class Throws_Target(throws_type_index: Int) extends TypeAnnotationTarget {

    final override def attribute_length: Int = 1 + 2

    final override def tag: Int = 0x17

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(throws_type_index).toString(cp) }</span>
    }
}

//_______________________________
// localvar_target

trait Localvar_Target extends TypeAnnotationTarget {

    def localvarTable: IndexedSeq[LocalvarTableEntry]

    final override def attribute_length: Int =
        1 + 2 + localvarTable.size * 6

}

case class LocalvarTableEntry(
        start_pc: Int,
        length:   Int,
        index:    Int
) {

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[pc: { start_pc }, local variable table:{ cp(index).toString(cp) }]</span>
    }
}

case class LocalvarDecl(localvarTable: IndexedSeq[LocalvarTableEntry]) extends Localvar_Target {

    type LocalvarTable = IndexedSeq[LocalvarTableEntry]

    final override def tag: Int = 0x40

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[LocalvarDecl:{ localvarTable.map(_.toXHTML(cp)) }]</span>
    }
}
case class ResourcevarDecl(localvarTable: IndexedSeq[LocalvarTableEntry]) extends Localvar_Target {

    type LocalvarTable = IndexedSeq[LocalvarTableEntry]

    final override def tag: Int = 0x41

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[ResourcevarDecl:{ localvarTable.map(_.toXHTML(cp)) }]</span>
    }
}

//______________________________
// catch_target
case class Catch_Target(exception_table_index: Int) extends TypeAnnotationTarget {

    final override def attribute_length: Int = 1 + 2

    final override def tag: Int = 0x42

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span class="type_annotation_target">{ cp(exception_table_index).toString(cp) }</span>
    }
}

//______________________________
// offset_target

trait Offset_Target extends TypeAnnotationTarget {
    def offset: Int

    final override def attribute_length: Int = 1 + 2

}

case class InstanceOf(offset: Int) extends Offset_Target {

    final override def tag: Int = 0x43

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[offset:{ offset }]</span>
    }
}
case class New(offset: Int) extends Offset_Target {

    final override def tag: Int = 0x44

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[offset:{ offset }]</span>
    }
}
case class MethodReferenceExpressionNew /*::New*/ (offset: Int) extends Offset_Target {

    final override def tag: Int = 0x45

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[offset:{ offset }]</span>
    }
}
case class MethodReferenceExpressionIdentifier /*::Identifier*/ (offset: Int) extends Offset_Target {

    final override def tag: Int = 0x46

    def toXHTML(implicit cp: Constant_Pool): Node = {
        <span>[offset:{ offset }]</span>
    }
}

//______________________________
// type_argument_target

trait Type_Argument_Target extends TypeAnnotationTarget {
    def offset: Int
    def type_argument_index: Int

    final override def attribute_length: Int = 1 /*tag*/ + 2 + 1

    /** The description of the annotated type argument as given in the JVM spec.*/
    def description: String

    final def toXHTML(implicit cp: Constant_Pool): Node = {
        <span><i>{ description }</i>(bytecode offset = { offset }, type argument index = { type_argument_index })</span>
    }
}

case class CastExpression(offset: Int, type_argument_index: Int) extends Type_Argument_Target {

    final override def tag: Int = 0x47

    final def description: String = "type in cast expression"

}
case class ConstructorInvocation(
        offset:              Int,
        type_argument_index: Int
) extends Type_Argument_Target {

    final override def tag: Int = 0x48

    final def description: String = {
        """|type argument for generic constructor in new expression or
           |explicit constructor invocation statement""".stripMargin
    }

}
case class MethodInvocation(
        offset:              Int,
        type_argument_index: Int
) extends Type_Argument_Target {

    final override def tag: Int = 0x49

    final def description: String = {
        "type argument for generic method in method invocation expression"
    }

}
case class ConstructorInMethodReferenceExpression(
        offset:              Int,
        type_argument_index: Int
) extends Type_Argument_Target {

    final override def tag: Int = 0x4a

    final def description: String = {
        "type argument for generic constructor in method reference expression using ::new"
    }
}
case class MethodInMethodReferenceExpression(
        offset:              Int,
        type_argument_index: Int
) extends Type_Argument_Target {

    final override def tag: Int = 0x4b

    final def description: String = {
        "type argument for generic method in method reference expression using ::Identifier"
    }
}
