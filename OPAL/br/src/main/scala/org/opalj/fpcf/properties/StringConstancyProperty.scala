/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.properties
import org.opalj.br.Field
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EPS
import org.opalj.fpcf.FallbackReason
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyKey
import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.string_definition.properties.StringConstancyInformation
import org.opalj.fpcf.string_definition.properties.StringConstancyLevel
import org.opalj.fpcf.string_definition.properties.StringConstancyType

sealed trait StringConstancyPropertyMetaInformation extends PropertyMetaInformation {
    type Self = StringConstancyProperty
}

class StringConstancyProperty(
        val stringConstancyInformation: List[StringConstancyInformation]
) extends Property with StringConstancyPropertyMetaInformation {

    final def key: PropertyKey[StringConstancyProperty] = StringConstancyProperty.key

    override def toString: String = {
        val levels = stringConstancyInformation.map(
            _.constancyLevel.toString.toLowerCase
        ).mkString("{", ",", "}")
        val strings = stringConstancyInformation.map(_.possibleStrings).mkString("{", ",", "}")
        s"Levels: $levels, Strings: $strings"
    }

}

object StringConstancyProperty extends StringConstancyPropertyMetaInformation {

    final val PropertyKeyName = "StringConstancy"

    final val key: PropertyKey[StringConstancyProperty] = {
        PropertyKey.create(
            PropertyKeyName,
            (_: PropertyStore, _: FallbackReason, _: Entity) ⇒ {
                // TODO: Using simple heuristics, return a better value for some easy cases
                lowerBound
            },
            (_, eps: EPS[Field, StringConstancyProperty]) ⇒ eps.ub,
            (_: PropertyStore, _: Entity) ⇒ None
        )
    }

    def apply(
        stringConstancyInformation: List[StringConstancyInformation]
    ): StringConstancyProperty = new StringConstancyProperty(stringConstancyInformation)

    /**
     * @return Returns the upper bound from a lattice-point of view.
     */
    def upperBound: StringConstancyProperty =
        StringConstancyProperty(List(StringConstancyInformation(
            StringConstancyLevel.CONSTANT, StringConstancyType.APPEND
        )))

    /**
     * @return Returns the lower bound from a lattice-point of view.
     */
    def lowerBound: StringConstancyProperty =
        StringConstancyProperty(List(StringConstancyInformation(
            StringConstancyLevel.DYNAMIC,
            StringConstancyType.APPEND,
            StringConstancyInformation.UnknownWordSymbol
        )))

}
