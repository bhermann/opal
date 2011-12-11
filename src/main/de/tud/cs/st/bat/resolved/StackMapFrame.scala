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
package resolved

/**
 * Part of the Java 6 stack map table attribute.
 *
 * @author Michael Eichberg
 */
sealed trait StackMapFrame /*extends de.tud.cs.st.bat.StackMapFrame*/ {
    type VerificationTypeInfo = de.tud.cs.st.bat.resolved.VerificationTypeInfo
    // [DOCUMENTATION ONLY]	type VerificationTypeInfoLocals = IndexedSeq[VerificationTypeInfo]
    // [DOCUMENTATION ONLY]	type VerificationTypeInfoStack = IndexedSeq[VerificationTypeInfo]

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //
    def toXML: scala.xml.Node

    def frameType: Int

    //final def frame_type = frameType 
}

case class SameFrame(frameType: Int)
        extends /* de.tud.cs.st.bat.SameFrame with */ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML = <same_frame tag={ frameType.toString }/>
}

case class SameLocals1StackItemFrame(frameType: Int,
                                     verificationTypeInfoStackItem: VerificationTypeInfo)
        extends /* de.tud.cs.st.bat.SameLocals1StackItemFrame with*/ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <same_locals_1_stack_item_frame tag={ frameType.toString }>
			{ verificationTypeInfoStackItem.toXML }
		</same_locals_1_stack_item_frame>
}

case class SameLocals1StackItemFrameExtended(frameType: Int,
                                             offsetDelta: Int,
                                             verificationTypeInfoStackItem: VerificationTypeInfo)
        extends /*de.tud.cs.st.bat.SameLocals1StackItemFrameExtended with */ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <same_locals_1_stack_item_frame_extended tag={ frameType.toString } offset_delta={ offsetDelta.toString }>
			{ verificationTypeInfoStackItem.toXML }
		</same_locals_1_stack_item_frame_extended>
}

case class ChopFrame(frameType: Int,
                     offsetDelta: Int)
        extends /*de.tud.cs.st.bat.ChopFrame with*/ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <chop_frame tag={ frameType.toString } offset_delta={ offsetDelta.toString }/>
}

case class SameFrameExtended(frameType: Int,
                             offsetDelta: Int)
        extends /* de.tud.cs.st.bat.SameFrameExtended with */ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <same_frame_extended tag={ frameType.toString } offset_delta={ offsetDelta.toString }/>
}

case class AppendFrame(frameType: Int,
                       offsetDelta: Int,
                       verificationTypeInfoLocals: VerificationTypeInfoLocals)
        extends /*de.tud.cs.st.bat.AppendFrame with*/ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <append_frame tag={ frameType.toString } offset_delta={ offsetDelta.toString }>
			<locals>{ for (local ← verificationTypeInfoLocals) yield local.toXML }</locals>
		</append_frame>
}

case class FullFrame(frameType: Int,
                     offsetDelta: Int,
                     verificationTypeInfoLocals: VerificationTypeInfoLocals,
                     verificationTypeInfoStack: VerificationTypeInfoStack)
        extends /*de.tud.cs.st.bat.FullFrame with*/ StackMapFrame {

    //
    //
    // SUPPORT FOR SPECIAL REPRESENTATIONS
    //
    //

    def toXML =
        <append_frame tag={ frameType.toString } offset_delta={ offsetDelta.toString }>
			<locals>{ for (local ← verificationTypeInfoLocals) yield local.toXML }</locals>
			<stack>{ for (item ← verificationTypeInfoStack) yield item.toXML }</stack>
		</append_frame>
}
