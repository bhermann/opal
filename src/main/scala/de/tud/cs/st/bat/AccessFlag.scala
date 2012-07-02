/* License (BSD Style License):
 *  Copyright (c) 2009, 2011
 *  Software Technology Group
 *  Department of Computer Science
 *  Technische Universität Darmstadt
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *  - Neither the name of the Software Technology Group or Technische
 *    Universität Darmstadt nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific
 *    prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package de.tud.cs.st.bat

/**
  * A class, field or method declaration's access flags. An access flag is
  * basically just a specific bit that can be combined with other
  * access flags to create an integer based bit vector that represents all
  * flags defined for a class, method or field declaration.
  *
  * @author Michael Eichberg
  */
sealed trait AccessFlag {

   /**
     * The Java (source code) name of the access flag if it exists. E.g.,
     * "public", "native", etc.
     */
   def javaName : Option[String]

   /**
     * The int mask of this access flag as defined by the JVM 7 specification.
     */
   def mask : Int

   /**
     * Determines if this access flag is set in the given access_flags bit vector.
     * E.g., to determine if a method's static modifier is set it is sufficient
     * to call {{{ACC_STATIC ∈ method.access_flags}}}.
     */
   def element_of(access_flags : Int) : Boolean = (access_flags & mask) != 0

   final def ∈(access_flags : Int) : Boolean = element_of(access_flags)

   def unapply(accessFlags : Int) : Boolean = element_of(accessFlags)
}

sealed trait VisibilityModifier extends AccessFlag
final object VisibilityModifier {

   val mask = ACC_PRIVATE.mask | ACC_PUBLIC.mask | ACC_PROTECTED.mask

   private val SOME_PUBLIC = Some(ACC_PUBLIC)
   private val SOME_PRIVATE = Some(ACC_PRIVATE)
   private val SOME_PROTECTED = Some(ACC_PROTECTED)

   def get(accessFlags : Int) : Option[VisibilityModifier] =
      ((accessFlags & VisibilityModifier.mask) : @scala.annotation.switch) match {
         case 1 /*ACC_PUBLIC.mask*/    ⇒ SOME_PUBLIC
         case 2 /*ACC_PRIVATE.mask*/   ⇒ SOME_PRIVATE
         case 4 /*ACC_PROTECTED.mask*/ ⇒ SOME_PROTECTED
         case _                        ⇒ None
      }

   def unapply(accessFlags : Int) : Option[VisibilityModifier] = get(accessFlags)
}

final case object ACC_PUBLIC extends VisibilityModifier {
   val javaName : Option[String] = Some("public")
   val mask : Int = 0x0001
}

final case object ACC_PRIVATE extends VisibilityModifier {
   val javaName : Option[String] = Some("private")
   val mask : Int = 0x0002
}

final case object ACC_PROTECTED extends VisibilityModifier {
   val javaName : Option[String] = Some("protected")
   val mask : Int = 0x0004
}

final case object ACC_STATIC extends AccessFlag {
   val javaName : Option[String] = Some("static")
   val mask = 0x0008
}

final case object ACC_FINAL extends AccessFlag {
   val javaName : Option[String] = Some("final")
   val mask = 0x0010
}

final case object ACC_SUPER extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x0020
}

final case object ACC_SYNCHRONIZED extends AccessFlag {
   val javaName : Option[String] = Some("synchronized")
   val mask = 0x0020
}

final case object ACC_VOLATILE extends AccessFlag {
   val javaName : Option[String] = Some("volatile")
   val mask = 0x0040
}

final case object ACC_BRIDGE extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x0040
}

final case object ACC_TRANSIENT extends AccessFlag {
   val javaName : Option[String] = Some("transient")
   val mask = 0x0080
}

final case object ACC_VARARGS extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x0080
}

final case object ACC_NATIVE extends AccessFlag {
   val javaName : Option[String] = Some("native")
   val mask = 0x0100
}

final case object ACC_INTERFACE extends AccessFlag {
   val javaName : Option[String] = None // this flag modifies the semantics of a class, but it is not an additional flag
   val mask = 0x0200
}

final case object ACC_ABSTRACT extends AccessFlag {
   val javaName : Option[String] = Some("abstract")
   val mask = 0x0400
}

final case object ACC_STRICT extends AccessFlag {
   val javaName : Option[String] = Some("strictfp")
   val mask = 0x0800
}

final case object ACC_SYNTHETIC extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x1000
}

final case object ACC_ANNOTATION extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x2000
}

final case object ACC_ENUM extends AccessFlag {
   val javaName : Option[String] = None
   val mask = 0x4000
}

