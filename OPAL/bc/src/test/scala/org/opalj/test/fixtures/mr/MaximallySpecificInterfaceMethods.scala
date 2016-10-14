/* BSD 2-Clause License:
 * Copyright (c) 2016
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
package bc

import java.nio.file.Files
import java.nio.file.Paths

import org.opalj.bi.ACC_PUBLIC
import org.opalj.bi.ACC_ABSTRACT
import org.opalj.da.ClassFile
import org.opalj.da.Method_Info
import org.opalj.da.Constant_Pool_Entry
import org.opalj.da.CONSTANT_Class_info
import org.opalj.da.CONSTANT_Utf8
import org.opalj.da.CONSTANT_NameAndType_info
import org.opalj.da.CONSTANT_Methodref_info
import org.opalj.da.CONSTANT_String_info
import org.opalj.da.Code_attribute
import org.opalj.da.Code
import org.opalj.bi.ACC_INTERFACE

/**
 * Generates multiple intefaces with default methods and abstract
 * methods to test method resolution w.r.t. the selection of the
 * maximally specific method.
 *
 * @see For further details see: `Readme.md`.
 *
 * @author Michael Eichberg
 */
object MaximallySpecificInterfaceMethods extends App {

    final val InterfaceAccessFlags = ACC_INTERFACE.mask | ACC_ABSTRACT.mask

    {
        val s0_1CF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S0_1"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6), //  HERE - UNUSED
                /*  6 */ CONSTANT_Utf8("mr/SuperIntf"), // HERE - UNUSED
                /*  7 */ CONSTANT_Utf8("m"),
                /*  8 */ CONSTANT_Utf8("()V"),
                /*  9 */ CONSTANT_Utf8("Code"),
                /* 10 */ CONSTANT_String_info(11),
                /* 11 */ CONSTANT_Utf8("S0_1.m"), // the printed value
                /* 12 */ CONSTANT_Methodref_info(13, 15),
                /* 13 */ CONSTANT_Class_info(14),
                /* 14 */ CONSTANT_Utf8("mr/Helper"),
                /* 15 */ CONSTANT_NameAndType_info(16, 17),
                /* 16 */ CONSTANT_Utf8("println"),
                /* 17 */ CONSTANT_Utf8("(Ljava/lang/String;)V")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ ,
            methods = IndexedSeq(Method_Info(
                access_flags = ACC_PUBLIC.mask, name_index = 7, descriptor_index = 8,
                attributes = IndexedSeq(Code_attribute(
                    attribute_name_index = 9, max_stack = 1, max_locals = 1,
                    code = new Code(Array[Byte](
                    18, /* ldc*/ 10, /* #10*/
                    (0xff & 184).toByte, /* invokestatic*/ 0, /* -> Methodref */ 12, /* #12 */
                    (0xff & 177).toByte /* return */
                ))
                ))
            ))
        )
        val assembledS0_1 = Assembler(s0_1CF)
        val assembledS0_1Path = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S0_1.class")
        val assembledS0_1File = Files.write(assembledS0_1Path, assembledS0_1)
        println("Created class file: "+assembledS0_1File.toAbsolutePath())
    }

    {
        val s0_2CF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S0_2"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6), //  HERE - UNUSED
                /*  6 */ CONSTANT_Utf8("mr/SuperIntf"), // HERE - UNUSED
                /*  7 */ CONSTANT_Utf8("m"),
                /*  8 */ CONSTANT_Utf8("()V"),
                /*  9 */ CONSTANT_Utf8("Code"),
                /* 10 */ CONSTANT_String_info(11),
                /* 11 */ CONSTANT_Utf8("S0_2.m"), // the printed value
                /* 12 */ CONSTANT_Methodref_info(13, 15),
                /* 13 */ CONSTANT_Class_info(14),
                /* 14 */ CONSTANT_Utf8("mr/Helper"),
                /* 15 */ CONSTANT_NameAndType_info(16, 17),
                /* 16 */ CONSTANT_Utf8("println"),
                /* 17 */ CONSTANT_Utf8("(Ljava/lang/String;)V")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ ,
            methods = IndexedSeq(Method_Info(
                access_flags = ACC_PUBLIC.mask, name_index = 7, descriptor_index = 8,
                attributes = IndexedSeq(Code_attribute(
                    attribute_name_index = 9, max_stack = 1, max_locals = 1,
                    code = new Code(Array[Byte](
                    18, /* ldc*/ 10, /* #10*/
                    (0xff & 184).toByte, /* invokestatic*/ 0, /* -> Methodref */ 12, /* #12 */
                    (0xff & 177).toByte /* return */
                ))
                ))
            ))
        )
        val assembledS0_2 = Assembler(s0_2CF)
        val assembledS0_2Path = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S0_2.class")
        val assembledS0_2File = Files.write(assembledS0_2Path, assembledS0_2)
        println("Created class file: "+assembledS0_2File.toAbsolutePath())
    }

    {
        val s1_aCF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S1_a"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6),
                /*  6 */ CONSTANT_Utf8("mr/S0_1"),
                /*  7 */ CONSTANT_Utf8("m"),
                /*  8 */ CONSTANT_Utf8("()V")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ , interfaces = IndexedSeq(5),
            methods = IndexedSeq(Method_Info(
                access_flags = ACC_PUBLIC.mask | ACC_ABSTRACT.mask,
                name_index = 7, descriptor_index = 8
            ))
        )
        val assembledS1_a = Assembler(s1_aCF)
        val assembledS1_aPath = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S1_a.class")
        val assembledS1_aFile = Files.write(assembledS1_aPath, assembledS1_a)
        println("Created class file: "+assembledS1_aFile.toAbsolutePath())
    }

    {
        val s1_cCF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S1_c"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6),
                /*  6 */ CONSTANT_Utf8("mr/S0_1"),
                /*  7 */ CONSTANT_Utf8("m"),
                /*  8 */ CONSTANT_Utf8("()V"),
                /*  9 */ CONSTANT_Utf8("Code"),
                /* 10 */ CONSTANT_String_info(11),
                /* 11 */ CONSTANT_Utf8("S1_c.m"), // the printed value
                /* 12 */ CONSTANT_Methodref_info(13, 15),
                /* 13 */ CONSTANT_Class_info(14),
                /* 14 */ CONSTANT_Utf8("mr/Helper"),
                /* 15 */ CONSTANT_NameAndType_info(16, 17),
                /* 16 */ CONSTANT_Utf8("println"),
                /* 17 */ CONSTANT_Utf8("(Ljava/lang/String;)V"),
                /* 18 */ CONSTANT_Class_info(19),
                /* 19 */ CONSTANT_Utf8("mr/S0_2")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ , interfaces = IndexedSeq(5, 18),
            methods = IndexedSeq(Method_Info(
                access_flags = ACC_PUBLIC.mask, name_index = 7, descriptor_index = 8,
                attributes = IndexedSeq(Code_attribute(
                    attribute_name_index = 9, max_stack = 1, max_locals = 1,
                    code = new Code(Array[Byte](
                    18, /* ldc*/ 10, /* #10*/
                    (0xff & 184).toByte, /* invokestatic*/ 0, /* -> Methodref */ 12, /* #12 */
                    (0xff & 177).toByte /* return */
                ))
                ))
            ))
        )
        val assembledS1_c = Assembler(s1_cCF)
        val assembledS1_cPath = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S1_c.class")
        val assembledS1_cFile = Files.write(assembledS1_cPath, assembledS1_c)
        println("Created class file: "+assembledS1_cFile.toAbsolutePath())
    }

    {
        val s2_1CF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S2_1"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6),
                /*  6 */ CONSTANT_Utf8("mr/S1_a"),
                /*  7 */ CONSTANT_Class_info(8),
                /*  8 */ CONSTANT_Utf8("mr/S1_c")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ , interfaces = IndexedSeq(5, 7)
        )
        val assembledS2_1 = Assembler(s2_1CF)
        val assembledS2_1Path = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S2_1.class")
        val assembledS2_1File = Files.write(assembledS2_1Path, assembledS2_1)
        println("Created class file: "+assembledS2_1File.toAbsolutePath())
    }

    {
        val s2_2CF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/S2_2"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6),
                /*  6 */ CONSTANT_Utf8("mr/S0_2")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ , interfaces = IndexedSeq(5)
        )
        val assembledS2_2 = Assembler(s2_2CF)
        val assembledS2_2Path = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/S2_2.class")
        val assembledS2_2File = Files.write(assembledS2_2Path, assembledS2_2)
        println("Created class file: "+assembledS2_2File.toAbsolutePath())
    }

    {
        val intfCF = ClassFile(
            Array[Constant_Pool_Entry](
                /*  0 */ null,
                /*  1 */ CONSTANT_Class_info(2),
                /*  2 */ CONSTANT_Utf8("mr/Intf"),
                /*  3 */ CONSTANT_Class_info(4),
                /*  4 */ CONSTANT_Utf8("java/lang/Object"),
                /*  5 */ CONSTANT_Class_info(6),
                /*  6 */ CONSTANT_Utf8("mr/S2_1"),
                /*  7 */ CONSTANT_Class_info(8),
                /*  8 */ CONSTANT_Utf8("mr/S2_2")
            ),
            minor_version = 0, major_version = 52, access_flags = InterfaceAccessFlags,
            this_class = 1, super_class = 3 /*extends java.lang.Object*/ , interfaces = IndexedSeq(5, 7)
        )
        val assembledIntf = Assembler(intfCF)
        val assembledIntfPath = Paths.get("OPAL/bc/src/test/resources/MaximallySpecificInterfaceMethods/mr/Intf.class")
        val assembledIntfFile = Files.write(assembledIntfPath, assembledIntf)
        println("Created class file: "+assembledIntfFile.toAbsolutePath())
    }

}
