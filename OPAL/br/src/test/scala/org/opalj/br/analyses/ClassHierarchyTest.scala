/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package analyses

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.opalj.bi.TestSupport.locateTestResources
import reader.Java8Framework.ClassFiles
import org.opalj.collection.immutable.UIDSet
import org.opalj.log.GlobalLogContext

/**
 * Basic tests of the class hierarchy.
 *
 * @author Michael Eichberg
 * @author Michael Reif
 */
@RunWith(classOf[JUnitRunner])
class ClassHierarchyTest extends FlatSpec with Matchers {

    // -----------------------------------------------------------------------------------
    //
    // TESTING THE SUBTYPE RELATION RELATED FUNCTIONALITY
    //
    // -----------------------------------------------------------------------------------

    //
    // Setup
    //
    val jlsCHFile = "ClassHierarchyJLS.ths"
    val jlsCHCreator = List(() ⇒ getClass.getResourceAsStream(jlsCHFile))
    val jlsCH = ClassHierarchy(Traversable.empty, jlsCHCreator)(GlobalLogContext)

    val preInitCH = ClassHierarchy.preInitializedClassHierarchy

    val javaLangCHFile = "JavaLangClassHierarchy.ths"
    val javaLangCHCreator = List(() ⇒ getClass.getResourceAsStream(javaLangCHFile))
    val javaLangCH = ClassHierarchy(Traversable.empty, javaLangCHCreator)(GlobalLogContext)

    val Object = ObjectType.Object
    val Throwable = ObjectType.Throwable
    val Exception = ObjectType.Exception
    val Error = ObjectType.Error
    val RuntimeException = ObjectType.RuntimeException
    val ArithmeticException = ObjectType.ArithmeticException
    val Cloneable = ObjectType.Cloneable
    val Serializable = ObjectType.Serializable
    val SeriablizableArray = ArrayType(Serializable)
    val SeriablizableArrayOfArray = ArrayType(SeriablizableArray)
    val AnUnknownType = ObjectType("myTest/AnUnknownType")
    val AnUnknownTypeArray = ArrayType(AnUnknownType)
    val CloneableArray = ArrayType(Cloneable)
    val ObjectArray = ArrayType.ArrayOfObjects
    val intArray = ArrayType(IntegerType)
    val arrayOfIntArray = ArrayType(ArrayType(IntegerType))
    val longArray = ArrayType(LongType)

    // Commonly used pacakge names
    val pgk = Some("classhierarchy/")

    val SimpleCTS = SimpleClassTypeSignature
    val CTS = ClassTypeSignature
    def CTS(cn: String, ptas: List[TypeArgument], suffix: List[SimpleClassTypeSignature] = Nil) =
        ClassTypeSignature(pgk, SimpleCTS(cn, ptas), suffix)

    def elementType(cts: ClassTypeSignature) =
        ProperTypeArgument(None, cts)

    def upperBoundType(cts: ClassTypeSignature) =
        ProperTypeArgument(Some(CovariantIndicator), cts)

    def lowerBoundType(cts: ClassTypeSignature) =
        ProperTypeArgument(Some(ContravariantIndicator), cts)
    /*SimpleClassTypeSignatures*/
    val baseSCTS = SimpleCTS("Base", Nil)
    val extBaseSCTS = SimpleCTS("ExtendedBase", Nil)
    val lvlTwoBaseSCTS = SimpleCTS("lvlTwoBase", Nil)
    val altBaseSCTS = SimpleCTS("AlternativeBase", Nil)
    val genericSCTS = SimpleCTS("SimpleGeneric", Nil)
    val unknownSCTS = SimpleCTS("Hidden", Nil)
    /*Nested ClassTypeSignatures*/
    val unknownCTS = CTS(pgk, unknownSCTS, Nil)
    val baseCTS = CTS(pgk, baseSCTS, Nil)
    val extBaseCTS = CTS(pgk, extBaseSCTS, Nil)
    val lvlTwoBaseCTS = CTS(pgk, lvlTwoBaseSCTS, Nil)
    val altBaseCTS = CTS(pgk, altBaseSCTS, Nil)
    val genericCTS = CTS(pgk, genericSCTS, Nil)
    val altInterfaceCTS = CTS(pgk, SimpleCTS("AltInterface", Nil), Nil)

    /** UContainer<UnknownType> */
    val unknownContainer = CTS("UContainer", List(elementType(CTS(pgk, SimpleCTS("UnknownType", Nil), Nil))))

    /** Interface<Base> */
    val iContainerWithBase = CTS("Interface", List(elementType(baseCTS)))

    /** Interface<AlternativeBase> */
    val iContainerWithAltBase = CTS("Interface", List(elementType(altBaseCTS)))

    /** BaseWithInterface<Base> // BaseWithInterface<E> implements Interface<E> */
    val IBaseContainerWithBase = CTS("BaseWithInterface", List(elementType(baseCTS)))

    /** BaseWithInterface<AlternativeBase> */
    val IBaseContainerWithAltBase = CTS("BaseWithInterface", List(elementType(altBaseCTS)))

    /** BaseWithConcreteInterface // BaseWithConcreteInterface implements Interface<Base> */
    val concreteInterfaceWithBase = CTS("BaseWithConcreteInterface", Nil)

    /** AltBaseWithConcreteInterface // AltBaseWithConcreteInterface implements Interface<AlternativeBase> */
    val concreteInterfaceWithAltBase = CTS("AltBaseWithConcreteInterface", Nil)

    /** SubclassWithInterface // SubclassWithInterface extends SubGeneric implements Interface<AlternativeBase> */
    val subClassWithInterface = CTS("SubclassWithInterface", Nil)

    /** SubGeneric // SubGeneric extends SimpleGeneric<Base> */
    val concreteSubGeneric = CTS("SubGeneric", Nil)

    /** SimpleGeneric<Base> */
    val baseContainer = CTS("SimpleGeneric", List(elementType(baseCTS)))

    /** SimpleGeneric<AlternativeBase> */
    val altContainer = CTS("SimpleGeneric", List(elementType(altBaseCTS)))

    /** SimpleGeneric<lvlTwoBase>*/
    val lvlTwoContainer = CTS("SimpleGeneric", List(elementType(lvlTwoBaseCTS)))

    /** SimpleGeneric<Base> */
    val extBaseContainer = CTS("SimpleGeneric", List(elementType(extBaseCTS)))

    /** ExtendedGeneric<Base> */
    val extGenContainer = CTS("ExtendedGeneric", List(elementType(baseCTS)))

    /** SimpleGeneric<*> */
    val wildCardContainer = CTS("SimpleGeneric", List(Wildcard))

    /**  SimpleGeneric<? extends Base>*/
    val covariantContainer = CTS("SimpleGeneric", List(upperBoundType(baseCTS)))

    /**  SimpleGeneric<? super Base>*/
    val contravariantContainer = CTS("SimpleGeneric", List(lowerBoundType(extBaseCTS)))

    /**  SimpleGeneric<? super SimpleGenericBase>*/
    val contravariantWithContainer =
        CTS("SimpleGeneric", List(lowerBoundType(baseContainer)))

    /**  SimpleGeneric<? super Base> */
    val contravariantBaseContainer =
        CTS("SimpleGeneric", List(lowerBoundType(baseCTS)))

    /** SubGenericET<SimpleGeneric<Base>, SimpleGeneric<ExtendedBase>>*/
    val doubleContainerET =
        CTS("SubGenericET", List(elementType(baseCTS), elementType(extBaseCTS)))

    /** SubGenericTE<SimpleGeneric<ExtendedBaseBase>, SimpleGeneric<Base>>*/
    val doubleContainerTE =
        CTS("SubGenericTE", List(elementType(extBaseCTS), elementType(baseCTS)))

    /** IndependentSubclass<SimpleGeneric<ExtendedBaseBase>, SimpleGeneric<Base>>*/
    val doubleContainerBase =
        CTS("IndependentSubclass", List(elementType(extBaseCTS), elementType(baseCTS)))

    /** AltIndependentSubclass<SimpleGeneric<ExtendedBaseBase>, SimpleGeneric<Base>>*/
    val doubleContainerAltBase =
        CTS("AltIndependentSubclass", List(elementType(extBaseCTS), elementType(baseCTS)))

    /** SubGenericET<SimpleGeneric<ExtendedBaseBase>, SimpleGeneric<Base>>*/
    val wrongDoubleContainer =
        CTS("SubGenericET", List(elementType(extBaseCTS), elementType(baseCTS)))

    /** SimpleGeneric<SimpleGeneric<Base>> */
    val nestedBase =
        CTS("SimpleGeneric", List(elementType(baseContainer)))

    /** SimpleGeneric<SimpleGeneric<ExtendedBase>> */
    val nestedExtBase =
        CTS("SimpleGeneric", List(elementType(extBaseContainer)))

    /** SimpleGeneric<SimpleGeneric<lvlTwoContainer>> */
    val nestedLvlTwoBase =
        CTS("SimpleGeneric", List(elementType(lvlTwoContainer)))

    /** SimpleGeneric<SimpleGeneric<AlternativeBase>> */
    val nestedAltBase =
        CTS("SimpleGeneric", List(elementType(altContainer)))

    /** SimpleGeneric<ExtendedGeneric<Base>> */
    val nestedSubGenBase =
        CTS("SimpleGeneric", List(elementType(extGenContainer)))

    /** SimpleGeneric<SimpleGeneric<? extends Base>> */
    val nestedInnerCovariantContainer =
        CTS("SimpleGeneric", List(elementType(covariantContainer)))

    /** SimpleGeneric<? extends SimpleGeneric<Base>> */
    val nestedOutterCovariantContainer =
        CTS("SimpleGeneric", List(upperBoundType(baseContainer)))

    /** SimpleGeneric<? super SimpleGeneric<Base>> */
    val nestedContravariantContainer =
        CTS("SimpleGeneric", List(elementType(contravariantBaseContainer)))

    /**
     * GenericWithSuffix<Base>.Suffix1_1<Base>
     *
     *  public class GenericWithSuffix<E> {
     *      public class Suffix1_1<E>{}
     *  }
     *
     */
    val genericWithSuffix_publicSuffix1_1 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_2<Base> where Suffix1_2 does not implement interfaces or extend classes. */
    val genericWithSuffix_publicSuffix1_2 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_2", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_3 // Suffix1_3 extends Suffix1_2<E> where E is bound to the same type as GenericWithSuffix.*/
    val genericWithSuffix_Suffix1_3 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_3", Nil)))

    /** GenericWithSuffix<AlternativeBase>.Suffix1_1<AlternativeBase>.Suffix1_3 // Suffix1_3 extends Suffix1_2<E> where E is bound to the same type as GenericWithSuffix.*/
    val genericWithSuffix_altBase_Suffix1_3 = CTS("GenericWithSuffix", List(elementType(altBaseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix1_3", Nil)))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_4 // Suffix 1_4 implements Interface<Base> */
    val genericWithSuffix_Suffix1_4 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_4", Nil)))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_5<Base> // Suffix1_5<T> where T is not the FormalTypeParamter of the prefix of Suffix1_5.*/
    val genericWithSuffix_Suffix1_5 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_5", List(elementType(baseCTS)))))

    /** GenericWithSuffix<AlternativeBase>.Suffix1_1<AlternativeBase>.Suffix1_5<Base> // Suffix1_5<T> where T is not the FormalTypeParamter of the prefix of Suffix1_5.*/
    val genericWithSuffix_altBase_Suffix1_5 = CTS("GenericWithSuffix", List(elementType(altBaseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix1_5", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_5<AlternativeBase> // Suffix1_5<T> where T is not the FormalTypeParamter of the prefix of Suffix1_5.*/
    val genericWithSuffix_Suffix1_5_altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_5", List(elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_6 // Suffix1_6 extends Suffix1_5<Base> */
    val genericWithSuffix_Suffix1_6 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_6", Nil)))

    /** GenericWithSuffix<AlterantiveBase>.Suffix1_1<AlterantiveBase>.Suffix1_6 // Suffix1_6 extends Suffix1_5<Base> */
    val genericWithSuffix_altBase_Suffix1_6 = CTS("GenericWithSuffix", List(elementType(altBaseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix1_6", Nil)))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_7<T> // Suffix1_7 extends Base */
    val genericWithSuffix_Suffix1_7 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_7", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<AlternativeBase> */
    val genericWithSuffix_publicSuffix1_1_altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(altBaseCTS)))))
    /** GenericWithSuffix<Base>.Suffix1_1<AlternativeBase> */
    val genericWithSuffix_altBase_publicSuffix1_1 = CTS("GenericWithSuffix", List(elementType(altBaseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<Base>.Suffix1_2<Base> */
    val genericWithSuffix_publicSuffix1_1_Suffix1_2 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(baseCTS))), SimpleCTS("Suffix1_2", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix4_1<Base> // Suffix4_1<E> extends Suffix1_1<E> */
    val genericWithSuffix_publicSuffix4_1 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix4_1", List(elementType(baseCTS)))))

    /** GenericWithSuffix<Base>.Suffix1_1<AlternativeBase>.Suffix1_2 */
    val genericWithSuffix_publicSuffix1_1_Suffix1_2_altBase = CTS(
        "GenericWithSuffix",
        List(elementType(baseCTS)), List(SimpleCTS("Suffix1_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix1_1", List(elementType(baseCTS))))
    )

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_2<Base, AlternativeBase> */
    val genericWithSuffix_Suffix2_2 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(baseCTS))), SimpleCTS("Suffix2_2", List(elementType(baseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_2<AlternativeBase, Base> */
    val genericWithSuffix_Suffix2_2_l2altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(baseCTS))), SimpleCTS("Suffix2_2", List(elementType(altBaseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<altBase>.Suffix2_2<Base, AlternativeBase> */
    val genericWithSuffix_Suffix2_2_l1altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix2_2", List(elementType(baseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_3<Base, AlternativeBase> // Suffix2_3<V,W> extends Suffix2_2<V,W>*/
    val genericWithSuffix_Suffix2_3 = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(baseCTS))), SimpleCTS("Suffix2_3", List(elementType(baseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_3<AlternativeBase, AlternativeBase> // Suffix2_3<V,W> extends Suffix2_2<V,W>*/
    val genericWithSuffix_Suffix2_3_l2altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(baseCTS))), SimpleCTS("Suffix2_3", List(elementType(altBaseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_3<AlternativeBase, AlternativeBase> // Suffix2_3<S1 extends E, S2 extends T> where E is bound to GenericWithSuffix and T is bound to Suffix2_1 */
    val genericWithSuffix_Suffix2_4_base_altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix2_4", List(elementType(baseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_3<AlternativeBase, AlternativeBase> // Suffix2_3<S1 extends E, S2 extends T> where E is bound to GenericWithSuffix and T is bound to Suffix2_1 */
    val genericWithSuffix_Suffix2_4_altBase_altBase = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix2_4", List(elementType(altBaseCTS), elementType(altBaseCTS)))))

    /** GenericWithSuffix<Base>.Suffix2_1<Base>.Suffix2_3<AlternativeBase, Base> // Suffix2_3<S1 extends E, S2 extends T> where E is bound to GenericWithSuffix and T is bound to Suffix2_1 */
    val genericWithSuffix_Suffix2_4_altBase_base = CTS("GenericWithSuffix", List(elementType(baseCTS)), List(SimpleCTS("Suffix2_1", List(elementType(altBaseCTS))), SimpleCTS("Suffix2_4", List(elementType(altBaseCTS), elementType(baseCTS)))))

    //
    // Verify
    //

    behavior of "the default ClassHierarchy"

    it should "be upwards closed (complete)" in {
        if (preInitCH.rootTypes.size != 1) {
            fail(
                "The default class hierarchy has unexpected root types: "+
                    preInitCH.rootTypes.mkString(", ")
            )
        }
    }

    behavior of "the default ClassHierarchy's leafTypes method"

    it should "return all leaf types" in {
        jlsCH.leafTypes.toSet should be(Set(
            ObjectType("java/lang/String"),
            ObjectType("java/lang/Class"),
            ObjectType("java/lang/Cloneable"),
            ObjectType("java/lang/Comparable")
        ))
    }

    behavior of "the default ClassHierarchy's isKnown method"

    it should "return true for all known types" in {
        preInitCH.isKnown(Throwable) should be(true)
    }

    it should "return false for all unknown types" in {
        preInitCH.isKnown(AnUnknownType) should be(false)
    }

    behavior of "the default ClassHierarchy's isDirectSupertypeInformationComplete method"

    it should "return true if a type's super type information is definitive complete" in {
        javaLangCH.isDirectSuperclassTypeInformationComplete(Object) should be(true)
        javaLangCH.isDirectSuperclassTypeInformationComplete(Throwable) should be(true)
    }

    it should "return false if a type's super type information is not guaranteed to be complete" in {
        javaLangCH.isDirectSuperclassTypeInformationComplete(Serializable) should be(false)
        javaLangCH.isDirectSuperclassTypeInformationComplete(AnUnknownType) should be(false)
    }

    behavior of "the default ClassHierarchy's allSupertypesOf method w.r.t. class types"

    it should "identify the same set of class as allSupertypes if the bound contains only one element" in {
        val supertypesOfString = javaLangCH.allSupertypes(ObjectType.String, true)

        supertypesOfString should be(
            javaLangCH.allSupertypesOf(UIDSet(ObjectType.String), true)
        )
    }

    behavior of "the default ClassHierarchy's leafTypes method w.r.t. class types"

    it should "correctly return a class if we give it a class and all types it inherits from" in {
        val supertypesOfString = javaLangCH.allSupertypes(ObjectType.String, true)

        javaLangCH.leafTypes(supertypesOfString) should be(UIDSet(ObjectType.String))
    }

    it should "correctly return a class's direct supertypes if we give it all types the class inherits from" in {
        val supertypesOfString = javaLangCH.allSupertypes(ObjectType.String, false)

        javaLangCH.leafTypes(supertypesOfString) should be(
            UIDSet(List(
                ObjectType.Serializable,
                ObjectType("java/lang/Comparable"),
                ObjectType("java/lang/CharSequence")
            ))
        )
    }

    behavior of "the default ClassHierarchy's isSubtypeOf method w.r.t. class types"

    it should "return Unknown if the \"subtype\" is unknown" in {
        preInitCH.isSubtypeOf(AnUnknownType, Throwable) should be(Unknown)
    }

    it should "return Yes if a class-type indirectly inherits an interface-type" in {
        preInitCH.isSubtypeOf(ArithmeticException, Serializable) should be(Yes)
    }

    it should "always return Yes if both types are identical" in {
        preInitCH.isSubtypeOf(ArithmeticException, ArithmeticException) should be(Yes)
        preInitCH.isSubtypeOf(AnUnknownType, AnUnknownType) should be(Yes)
    }

    it should "return Yes for interface types when the given super type is Object even if the interface type's supertypes are not known" in {
        preInitCH.isSubtypeOf(Serializable, Object) should be(Yes)
    }

    it should "return No for a type that is not a subtype of another type and all type information is known" in {
        // "only" classes
        preInitCH.isSubtypeOf(Error, Exception) should be(No)
        preInitCH.isSubtypeOf(Exception, Error) should be(No)
        preInitCH.isSubtypeOf(Exception, RuntimeException) should be(No)

        // "only" interfaces
        preInitCH.isSubtypeOf(Serializable, Cloneable) should be(No)

        // class and interface
        preInitCH.isSubtypeOf(ArithmeticException, Cloneable) should be(No)
    }

    it should "return Unknown if two types are not in an inheritance relationship but the subtype's supertypes are not guaranteed to be known" in {
        javaLangCH.isSubtypeOf(Serializable, Cloneable) should be(Unknown)
    }

    behavior of "the preInitialized ClassHierarchy's isSubtypeOf method w.r.t. Exceptions"

    it should "correctly reflect the base exception hierarchy" in {

        preInitCH.isSubtypeOf(Throwable, Object) should be(Yes)
        preInitCH.isSubtypeOf(Error, Throwable) should be(Yes)
        preInitCH.isSubtypeOf(RuntimeException, Exception) should be(Yes)
        preInitCH.isSubtypeOf(Exception, Throwable) should be(Yes)

        preInitCH.isSubtypeOf(Object, Throwable) should be(No)

        preInitCH.isSubtypeOf(AnUnknownType, Object) should be(Yes)
        preInitCH.isSubtypeOf(Object, AnUnknownType) should be(No)

    }

    behavior of "the ClassHierarchy's isSubtypeOf method w.r.t. Arrays"

    it should "correctly reflect the basic type hierarchy related to Arrays" in {
        preInitCH.isSubtypeOf(ObjectArray, Object) should be(Yes)
        preInitCH.isSubtypeOf(SeriablizableArray, ObjectArray) should be(Yes)
        preInitCH.isSubtypeOf(CloneableArray, ObjectArray) should be(Yes)
        preInitCH.isSubtypeOf(ObjectArray, ObjectArray) should be(Yes)
        preInitCH.isSubtypeOf(SeriablizableArray, SeriablizableArray) should be(Yes)
        preInitCH.isSubtypeOf(AnUnknownTypeArray, AnUnknownTypeArray) should be(Yes)

        preInitCH.isSubtypeOf(Object, ObjectArray) should be(No)
        preInitCH.isSubtypeOf(CloneableArray, SeriablizableArray) should be(No)

        preInitCH.isSubtypeOf(AnUnknownTypeArray, SeriablizableArray) should be(Unknown)

        preInitCH.isSubtypeOf(SeriablizableArray, AnUnknownTypeArray) should be(No)
    }

    it should "correctly reflect the type hierarchy related to Arrays of primitives" in {
        preInitCH.isSubtypeOf(intArray, Object) should be(Yes)
        preInitCH.isSubtypeOf(intArray, Serializable) should be(Yes)
        preInitCH.isSubtypeOf(intArray, Cloneable) should be(Yes)
        preInitCH.isSubtypeOf(intArray, intArray) should be(Yes)

        preInitCH.isSubtypeOf(intArray, longArray) should be(No)
        preInitCH.isSubtypeOf(longArray, intArray) should be(No)

        preInitCH.isSubtypeOf(arrayOfIntArray, ObjectArray) should be(Yes)
        preInitCH.isSubtypeOf(arrayOfIntArray, SeriablizableArray) should be(Yes)
    }

    it should "correctly reflect the type hierarchy related to Arrays of Arrays" in {
        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, Object) should be(Yes)
        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, SeriablizableArrayOfArray) should be(Yes)

        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, SeriablizableArray) should be(Yes)
        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, ObjectArray) should be(Yes)
        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, CloneableArray) should be(Yes)

        preInitCH.isSubtypeOf(SeriablizableArrayOfArray, AnUnknownTypeArray) should be(No)
    }

    behavior of "the ClassHierarchy's directSubtypesOf(UpperTypeBound) method"

    val typesProject =
        Project(
            ClassFiles(locateTestResources("classfiles/types.jar", "br")),
            Traversable.empty,
            true
        )

    val cRootType = ObjectType("types/CRoot")
    val cRootAType = ObjectType("types/CRootA")
    val cRootAABType = ObjectType("types/CRootAAB")
    val cRootAAABBCType = ObjectType("types/CRootAAABBC")
    val iRootAType = ObjectType("types/IRootA")
    val iRootBType = ObjectType("types/IRootB")
    val iRootCType = ObjectType("types/IRootC")

    it should "return the given upper type bound if it just contains a single type" in {
        import typesProject.classHierarchy.directSubtypesOf
        directSubtypesOf(UIDSet[ObjectType](cRootType)) should be(Set(cRootType))
        directSubtypesOf(UIDSet[ObjectType](iRootAType)) should be(Set(iRootAType))
        directSubtypesOf(UIDSet[ObjectType](cRootAAABBCType)) should be(Set(cRootAAABBCType))
    }

    it should "return the type that is the subtype of all types of the bound" in {
        import typesProject.classHierarchy.directSubtypesOf
        directSubtypesOf(UIDSet[ObjectType](iRootAType, iRootBType)) should be(Set(cRootAABType))
        directSubtypesOf(UIDSet[ObjectType](cRootAType, iRootBType)) should be(Set(cRootAABType))
        directSubtypesOf(UIDSet[ObjectType](iRootAType, iRootCType)) should be(Set(cRootAAABBCType))
        directSubtypesOf(UIDSet[ObjectType](Seq(iRootAType, iRootBType, iRootCType))) should be(Set(cRootAAABBCType))
        directSubtypesOf(UIDSet[ObjectType](iRootBType, iRootCType)) should be(Set(cRootAAABBCType))
        directSubtypesOf(UIDSet[ObjectType](cRootAType, iRootCType)) should be(Set(cRootAAABBCType))
        directSubtypesOf(UIDSet[ObjectType](cRootAABType, iRootCType)) should be(Set(cRootAAABBCType))
    }

    it should "not fail if no common subtype exists" in {
        import typesProject.classHierarchy.directSubtypesOf
        directSubtypesOf(UIDSet[ObjectType](cRootType, iRootBType)) should be(Set.empty)
    }

    behavior of "the ClassHierarchy's allSubclassTypes method"

    it should "return the empty iterator if the type has no subclasses" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootAAABBCType, false).size should be(0)
    }

    it should "return the singleton iterator if the type has no subclasses but we want the relation to be reflexive" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootAAABBCType, true).size should be(1)
    }

    it should "return all subclasses of a leaf-type in the complete type hierarchy" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootType, true).toSet should be(Set(cRootType))
    }

    it should "return all subclasses (non-reflexive) of a leaf-type in the complete type hierarchy" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootType, false).toSet should be(Set())
    }

    it should "return all subclasses in the complete type hierarchy" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootAType, true).toSet should be(Set(cRootAType, cRootAABType, cRootAAABBCType))
    }

    it should "return all subclasses (non-reflexive) in the complete type hierarchy" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(cRootAType, false).toSet should be(Set(cRootAABType, cRootAAABBCType))
    }

    it should "return all subclasses (non-reflexive) of a class with multiple direct subclasses" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(ObjectType("java/lang/IndexOutOfBoundsException"), false).toSet should be(
            Set(
                ObjectType("java/lang/ArrayIndexOutOfBoundsException"),
                ObjectType("java/lang/StringIndexOutOfBoundsException")
            )
        )
    }

    it should "return all subclasses (reflexive) of a class with multiple direct subclasses" in {
        import typesProject.classHierarchy.allSubclassTypes
        allSubclassTypes(ObjectType("java/lang/IndexOutOfBoundsException"), true).toSet should be(
            Set(
                ObjectType("java/lang/IndexOutOfBoundsException"),
                ObjectType("java/lang/ArrayIndexOutOfBoundsException"),
                ObjectType("java/lang/StringIndexOutOfBoundsException")
            )
        )
    }

    // -----------------------------------------------------------------------------------
    //
    // TESTING THE HANDLING OF GENERICS
    //
    // -----------------------------------------------------------------------------------

    behavior of "isSubTypeOf method w.r.t. concrete generics"

    it should "return YES iff the type arguments do match considering variance indicators and wildcards" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(baseContainer, baseContainer) should be(Yes)
        isSubtypeOf(baseContainer, wildCardContainer) should be(Yes)
        isSubtypeOf(concreteSubGeneric, baseContainer) should be(Yes)
        isSubtypeOf(wildCardContainer, wildCardContainer) should be(Yes)
        isSubtypeOf(extBaseContainer, covariantContainer) should be(Yes)
        isSubtypeOf(baseContainer, covariantContainer) should be(Yes)
        isSubtypeOf(baseContainer, contravariantContainer) should be(Yes)
        isSubtypeOf(doubleContainerET, baseContainer) should be(Yes)
        isSubtypeOf(doubleContainerTE, baseContainer) should be(Yes)
        isSubtypeOf(doubleContainerBase, baseContainer) should be(Yes)
    }

    it should "return NO if the type arguments doesn't match considering variance indicators and wildcards" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(baseContainer, extBaseContainer) should be(No)
        isSubtypeOf(concreteSubGeneric, altContainer) should be(No)
        isSubtypeOf(wildCardContainer, baseContainer) should be(No)
        isSubtypeOf(altContainer, contravariantContainer) should be(No)
        isSubtypeOf(extBaseContainer, contravariantBaseContainer) should be(No)
        isSubtypeOf(altContainer, covariantContainer) should be(No)
        isSubtypeOf(baseContainer, doubleContainerET) should be(No)
        isSubtypeOf(baseContainer, doubleContainerTE) should be(No)
        isSubtypeOf(wrongDoubleContainer, baseContainer) should be(No)
        isSubtypeOf(doubleContainerAltBase, baseContainer) should be(No)
    }

    behavior of "isSubTypeOf method w.r.t. generics with interface types"

    it should "return YES iff the subtype directly implements the interface with matching type arguments" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(concreteInterfaceWithBase, iContainerWithBase) should be(Yes)
        isSubtypeOf(IBaseContainerWithBase, iContainerWithBase) should be(Yes)

    }

    it should "return NO if the subtype doesn't directly implement the interface with matching type arguments" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(IBaseContainerWithAltBase, iContainerWithBase) should be(No)
        isSubtypeOf(concreteInterfaceWithAltBase, IBaseContainerWithBase) should be(No)
        isSubtypeOf(concreteInterfaceWithBase, concreteInterfaceWithAltBase) should be(No)
    }

    it should "return YES iff the subtype implements the given interface with matching type arguments through some supertype" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(subClassWithInterface, iContainerWithAltBase) should be(Yes)
        isSubtypeOf(subClassWithInterface, concreteSubGeneric) should be(Yes)
        isSubtypeOf(subClassWithInterface, baseContainer) should be(Yes)
    }

    it should "return NO if the subtype doesn't implement the given interface with matching type arguments through some supertype" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(subClassWithInterface, iContainerWithBase) should be(No)
    }

    it should "return UNKNOWN if one of the arguments is an unknown type" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(unknownContainer, baseContainer) should be(Unknown)
    }

    behavior of "isSubTypeOf method w.r.t. generics with nested types"

    it should "return YES iff if nested type arguments of the supertype and the subtype do match" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(nestedInnerCovariantContainer, nestedInnerCovariantContainer) should be(Yes)
        isSubtypeOf(nestedExtBase, nestedInnerCovariantContainer) should be(Yes)
        isSubtypeOf(nestedBase, nestedContravariantContainer) should be(Yes)
        isSubtypeOf(nestedBase, contravariantWithContainer) should be(Yes)
        isSubtypeOf(nestedBase, nestedOutterCovariantContainer) should be(Yes)
    }

    it should "return NO if nested type arguments of the subtype and the supertype doesn't match" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(nestedBase, nestedAltBase) should be(No)
        isSubtypeOf(nestedAltBase, nestedInnerCovariantContainer) should be(No)
        isSubtypeOf(nestedLvlTwoBase, nestedContravariantContainer) should be(No)
        isSubtypeOf(nestedSubGenBase, nestedContravariantContainer) should be(No)
    }

    behavior of "isSubTypeOf method w.r.t. generics with class suffix (e.g. by inner classes)"

    it should "return YES iff the class suffixes of a ClassTypeSignature of inner classes also match when considering generic type arguments" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf

        isSubtypeOf(genericWithSuffix_Suffix1_7, baseCTS) should be(Yes)
        isSubtypeOf(genericWithSuffix_publicSuffix1_1, genericWithSuffix_publicSuffix1_1) should be(Yes)
        isSubtypeOf(genericWithSuffix_publicSuffix4_1, genericWithSuffix_publicSuffix1_1) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix1_4, iContainerWithBase) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix1_3, genericWithSuffix_publicSuffix1_2) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix1_6, genericWithSuffix_Suffix1_5) should be(Yes)
        isSubtypeOf(genericWithSuffix_altBase_Suffix1_6, genericWithSuffix_altBase_Suffix1_5) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix2_3, genericWithSuffix_Suffix2_2) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix2_4_base_altBase, genericWithSuffix_Suffix2_4_base_altBase) should be(Yes)
    }

    it should "return NO if the class suffixes of a ClassTypeSignature of inner classes doesn't match when considering generic type arguments " in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(genericWithSuffix_publicSuffix1_1, genericWithSuffix_publicSuffix1_1_altBase) should be(No)
        isSubtypeOf(genericWithSuffix_altBase_publicSuffix1_1, genericWithSuffix_publicSuffix1_1) should be(No)
        isSubtypeOf(genericWithSuffix_publicSuffix1_1_Suffix1_2, genericWithSuffix_publicSuffix1_1) should be(No)
        isSubtypeOf(genericWithSuffix_publicSuffix1_1_Suffix1_2, genericWithSuffix_publicSuffix1_1_Suffix1_2_altBase) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix1_4, iContainerWithAltBase) should be(No)
        isSubtypeOf(genericWithSuffix_altBase_Suffix1_3, iContainerWithBase) should be(No)
        isSubtypeOf(genericWithSuffix_altBase_Suffix1_3, genericWithSuffix_publicSuffix1_2) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix1_6, genericWithSuffix_altBase_Suffix1_5) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix1_6, genericWithSuffix_Suffix1_5_altBase) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix2_2, genericWithSuffix_Suffix2_2_l2altBase) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix2_2, genericWithSuffix_Suffix2_2_l1altBase) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix2_3_l2altBase, genericWithSuffix_Suffix2_2) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix2_4_base_altBase, genericWithSuffix_Suffix2_4_altBase_altBase) should be(No)
    }

    behavior of "isSubTypeOf method w.r.t. generics specified by formal type parameters"

    it should "return YES iff the subtype extends the class and implements all declared interfaces of the FormalTypeParameter" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(extBaseCTS, FormalTypeParameter("X", Some(baseCTS), Nil)) should be(Yes)
        isSubtypeOf(subClassWithInterface, FormalTypeParameter("T", Some(concreteSubGeneric), List(iContainerWithAltBase))) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix1_7, FormalTypeParameter("T", Some(baseCTS), Nil)) should be(Yes)
        isSubtypeOf(genericWithSuffix_Suffix1_4, FormalTypeParameter("T", None, List(iContainerWithBase))) should be(Yes)
    }

    it should "return NO if the subtype doesn't extends the class and implements all declared interfaces of the FormalTypeParameter" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(altBaseCTS, FormalTypeParameter("X", Some(baseCTS), Nil)) should be(No)
        isSubtypeOf(subClassWithInterface, FormalTypeParameter("T", Some(concreteSubGeneric), List(iContainerWithAltBase, altInterfaceCTS))) should be(No)
        isSubtypeOf(genericWithSuffix_Suffix1_4, FormalTypeParameter("T", None, List(iContainerWithAltBase))) should be(No)
    }

    it should "return UNKNOWN if an unknown type is encountered" in {
        implicit val genericProject = ClassHierarchyTest.genericProject
        import genericProject.classHierarchy.isSubtypeOf
        isSubtypeOf(unknownContainer, FormalTypeParameter("X", Some(baseCTS), Nil)) should be(Unknown)
    }

    // -----------------------------------------------------------------------------------
    //
    // TESTS IF WE HAVE AN INCOMPLETE CLASS HIERARCHY
    //
    // -----------------------------------------------------------------------------------

    val apacheANTCH =
        ClassHierarchy(
            Traversable.empty,
            List(() ⇒ getClass.getResourceAsStream("ApacheANT1.7.1.ClassHierarchy.ths"))
        )(GlobalLogContext)

    it should "be possible to get all supertypes, even if not all information is available" in {

        val mi = ObjectType("org/apache/tools/ant/taskdefs/MacroInstance")
        apacheANTCH.allSupertypes(mi) should be(Set(
            ObjectType("org/apache/tools/ant/Task"),
            ObjectType("org/apache/tools/ant/TaskContainer"),
            ObjectType("org/apache/tools/ant/DynamicAttribute")
        ))
    }

    // -----------------------------------------------------------------------------------
    //
    // TESTING THE TRAVERSAL OF THE CLASS HIERARCHY
    //
    // -----------------------------------------------------------------------------------

    val clusteringProject =
        Project(
            ClassFiles(locateTestResources("classfiles/ClusteringTestProject.jar", "bi")),
            Traversable.empty,
            true
        )

    behavior of "the ClassHierarchy's method to traverse the class hierarchy"

    it should "correctly find all suptyes of an interface" in {
        import clusteringProject.classHierarchy

        val window = ObjectType("pattern/decorator/example1/Window")
        val simpleWindow = ObjectType("pattern/decorator/example1/SimpleWindow")

        classHierarchy.isKnown(window) should be(true)
        classHierarchy.isKnown(simpleWindow) should be(true)

        classHierarchy.isSubtypeOf(window, simpleWindow) should be(No)
        classHierarchy.isSubtypeOf(simpleWindow, window) should be(Yes)

        // check if the SimpleWindow is in the Set of all subtypes of Window
        var subtypes = Set.empty[ObjectType]
        classHierarchy.foreachSubtype(window) { subtypes += _ }
        if (!subtypes.contains(simpleWindow))
            fail(s"SimpleWindow is not among the subtypes: $subtypes; "+
                s"SimpleWindow <: ${classHierarchy.allSupertypes(simpleWindow)}; "+
                s"Window >: ${classHierarchy.allSubtypes(window, false)}\n"+
                classHierarchy.structure)

        clusteringProject.classFile(simpleWindow).get.methods.find(method ⇒
            method.name == "draw" &&
                method.descriptor == MethodDescriptor.NoArgsAndReturnVoid) should be('defined)

        classHierarchy.lookupImplementingMethods(
            window,
            "draw",
            MethodDescriptor.NoArgsAndReturnVoid,
            clusteringProject,
            (cf) ⇒ true
        ) should be('nonEmpty)
    }

    // -----------------------------------------------------------------------------------
    //
    // TESTING THE RESOLVING OF FIELD REFERENCES
    //
    // -----------------------------------------------------------------------------------

    val fieldsProject =
        Project(
            ClassFiles(locateTestResources("classfiles/Fields.jar", "bi")),
            Traversable.empty,
            true
        )
    import fieldsProject.classFile

    val SuperSuperType = ObjectType("fields/SuperSuper")
    val SuperSuperClass = classFile(SuperSuperType).get
    val SuperType = ObjectType("fields/Super")
    val SuperClass = classFile(SuperType).get

    val SuperIType = ObjectType("fields/SuperI")
    val SuperIClass = classFile(SuperIType).get
    val SubIType = ObjectType("fields/SubI")
    val SubIClass = classFile(SubIType).get

    val SubType = ObjectType("fields/Sub")
    val SubClass = classFile(SubType).get
    val SubSubType = ObjectType("fields/SubSub")
    val SubSubClass = classFile(SubSubType).get

    behavior of "the ClassHierarchy's method to resolve field references"

    import fieldsProject.classHierarchy.resolveFieldReference

    it should "correctly resolve a reference to a static field in a superclass" in {
        resolveFieldReference(SuperType, "x", IntegerType, fieldsProject) should be(
            Some(SuperSuperClass.fields(0))
        )
    }

    it should "correctly resolve a reference to a field defined in an interface" in {
        resolveFieldReference(SubIType, "THE_SUB_I", IntegerType, fieldsProject) should be(
            Some(SubIClass.fields(0))
        )
    }

    it should "correctly resolve a reference to a field defined in a superinterface of an interface" in {
        resolveFieldReference(SubIType, "THE_I", IntegerType, fieldsProject) should be(
            Some(SuperIClass.fields(0))
        )
    }

    it should "correctly resolve a reference to a field defined in a superinterface" in {
        resolveFieldReference(SubType, "THE_I", IntegerType, fieldsProject) should be(
            Some(SuperIClass.fields(0))
        )
    }

    it should "correctly resolve a reference to a field defined in a superclass" in {
        resolveFieldReference(SubSubType, "x", IntegerType, fieldsProject) should be(
            Some(SubClass.fields(0))
        )
    }

    it should "correctly resolve a reference to a private field defined in a superclass" in {
        resolveFieldReference(SubSubType, "y", IntegerType, fieldsProject) should be(
            Some(SuperClass.fields(0))
        )
    }

    it should "not fail (throw an exception) if the field cannot be found" in {
        resolveFieldReference(SubSubType, "NOT_DEFINED", IntegerType, fieldsProject) should be(
            None
        )
    }

    it should "not fail if the type cannot be found" in {
        resolveFieldReference(
            ObjectType("NOT/DEFINED"),
            "NOT_DEFINED",
            IntegerType,
            fieldsProject
        ) should be(None)
    }

    // -----------------------------------------------------------------------------------
    //
    // TESTING THE RESOLVING OF METHOD REFERENCES
    //
    // -----------------------------------------------------------------------------------

    val methodsProject =
        Project(
            ClassFiles(locateTestResources("classfiles/Methods.jar", "bi")),
            Traversable.empty,
            true
        )

    val superI = ObjectType("methods/b/SuperI")
    val directSub = ObjectType("methods/b/DirectSub")
    val directSubClassFile = methodsProject.classFile(directSub).get

    behavior of "the ClassHierarchy's methods to resolve method references"

    it should "handle the case if an interface has no implementing class" in {
        val implementingMethods =
            methodsProject.classHierarchy.lookupImplementingMethods(
                superI,
                "someMethod",
                MethodDescriptor.NoArgsAndReturnVoid,
                methodsProject,
                (cf) ⇒ true
            )

        implementingMethods.size should be(0)
    }

    it should "find a method in a super class" in {
        val classType = ObjectType("methods/b/B")
        val implementingMethods =
            methodsProject.classHierarchy.lookupImplementingMethods(
                classType,
                "publicMethod",
                MethodDescriptor.NoArgsAndReturnVoid,
                methodsProject,
                (cf) ⇒ true
            )

        implementingMethods.size should be(1)
        implementingMethods.head should have(
            'name("publicMethod"),
            'descriptor(MethodDescriptor.NoArgsAndReturnVoid)
        )
    }
}

object ClassHierarchyTest {

    val generics = locateTestResources("classfiles/genericTypes.jar", "br")
    val genericProject = Project(ClassFiles(generics), Traversable.empty, true)

}
