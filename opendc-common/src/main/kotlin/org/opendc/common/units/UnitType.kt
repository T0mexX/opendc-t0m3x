package org.opendc.common.units

public interface UnitType<T: Unit<T>> {
    public val zero: T
    public val max: T
}
