/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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

import org.scalatest.FunSuite

/**
 * Test the construction and analysis of types.
 *
 * @author Michael Eichberg
 */
@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class FieldTypeTest extends FunSuite {

    test("Byte Field Descriptor") {
        val fieldType = FieldType("B")
        assert(fieldType.toJavaClass == java.lang.Byte.TYPE)
        assert(fieldType == ByteType)
    }

    test("Char Field Descriptor") {
        val fieldType = FieldType("C")
        assert(fieldType.toJavaClass == java.lang.Character.TYPE)
        assert(fieldType == CharType)
    }

    test("Double Field Descriptor") {
        val fieldType = FieldType("D")
        assert(fieldType.toJavaClass == java.lang.Double.TYPE)
        assert(fieldType == DoubleType)
    }

    test("Float Field Descriptor") {
        val fieldType = FieldType("F")
        assert(fieldType.toJavaClass == java.lang.Float.TYPE)
        assert(fieldType == FloatType)
    }

    test("Integer Field Descriptor") {
        val fieldType = FieldType("I")
        assert(fieldType.toJavaClass == java.lang.Integer.TYPE)
        assert(fieldType == IntegerType)
    }

    test("Long Field Descriptor") {
        val fieldType = FieldType("J")
        assert(fieldType.toJavaClass == java.lang.Long.TYPE)
        assert(fieldType == LongType)
    }

}
