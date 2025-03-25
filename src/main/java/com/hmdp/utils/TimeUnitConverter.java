package com.hmdp.utils;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

public class TimeUnitConverter {

    /**
     * 将TimeUnit转换为对应的ChronoUnit（TemporalUnit的实现类）
     */
    public static TemporalUnit toTemporalUnit(TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:  return ChronoUnit.NANOS;
            case MICROSECONDS: return ChronoUnit.MICROS;
            case MILLISECONDS: return ChronoUnit.MILLIS;
            case SECONDS:      return ChronoUnit.SECONDS;
            case MINUTES:      return ChronoUnit.MINUTES;
            case HOURS:        return ChronoUnit.HOURS;
            case DAYS:         return ChronoUnit.DAYS;
            default:
                throw new IllegalArgumentException("Unsupported TimeUnit: " + timeUnit);
        }
    }

    /**
     * 将ChronoUnit（TemporalUnit的实现类）转换回TimeUnit
     */
    public static TimeUnit toTimeUnit(TemporalUnit temporalUnit) {
        if (!(temporalUnit instanceof ChronoUnit)) {
            throw new IllegalArgumentException("Unsupported TemporalUnit type: " + temporalUnit.getClass());
        }
        ChronoUnit chronoUnit = (ChronoUnit) temporalUnit;
        switch (chronoUnit) {
            case NANOS:    return TimeUnit.NANOSECONDS;
            case MICROS:   return TimeUnit.MICROSECONDS;
            case MILLIS:   return TimeUnit.MILLISECONDS;
            case SECONDS:  return TimeUnit.SECONDS;
            case MINUTES:  return TimeUnit.MINUTES;
            case HOURS:    return TimeUnit.HOURS;
            case DAYS:     return TimeUnit.DAYS;
            default:
                throw new IllegalArgumentException("Unsupported ChronoUnit: " + chronoUnit);
        }
    }
}