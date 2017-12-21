package org.opalj.fpcf.fixtures.escape.cycles;

import org.opalj.fpcf.analyses.escape.InterProceduralEscapeAnalysis;
import org.opalj.fpcf.properties.escape.AtMostEscapeInCallee;

public enum ChronoField implements TemporalField {

    NANO_OF_SECOND,

    MICRO_OF_SECOND,

    MILLI_OF_SECOND,

    DAY_OF_WEEK,

    DAY_OF_MONTH,

    MONTH_OF_YEAR,

    PROLEPTIC_MONTH,

    YEAR_OF_ERA,

    YEAR,

    ERA,

    INSTANT_SECONDS,

    OFFSET_SECONDS;

    @Override
    public boolean isSupportedBy(@AtMostEscapeInCallee(
            value = "Type is accessible but all methods do not let the parameter escape",
            analyses = InterProceduralEscapeAnalysis.class) TemporalAccessor temporal
    ) {
        return temporal.isSupported(this);
    }

    @Override
    public boolean isDateBased() {
        return ordinal() < DAY_OF_WEEK.ordinal();
    }

    @Override
    public boolean isTimeBased() {
        return ordinal() >= DAY_OF_WEEK.ordinal() && ordinal() <= ERA.ordinal();
    }
}
