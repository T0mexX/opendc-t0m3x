package org.opendc.simulator.network.units

import org.opendc.simulator.network.utils.InternalUse

@JvmInline
public value class Data private constructor(
    override val value: Double
): Unit<Data> {

    @InternalUse
    override fun new(value: Double): Data = Data(value)

    public fun toBytes(): Double = value * 1024.0
    public fun toKB(): Double = value
    public fun toMB(): Double = value / 1024
    public fun toGB(): Double = value / 1024 / 1024

    override fun toString(): String {
        val `100Bytes` = 1.0 / 1024 * 100
        val `100KB` = 100.0
        val `100MB` = 100.0 * 1024

        return when(value) {
            in (Double.MIN_VALUE..`100Bytes`) -> "${toBytes()} Bytes"
            in (`100Bytes`..`100KB`) -> "${toKB()} KB"
            in (`100KB`..`100MB`) -> "${toMB()} MB"
            else -> "${toGB()} GB"
        }
    }


    public companion object {
        public val ZERO: Data = Data(.0)

        public fun ofBytes(bytes: Number): Data = Data(bytes.toDouble() / 1024)
        public fun ofKB(kB: Number): Data = Data(kB.toDouble())
        public fun ofMB(mB: Number): Data = Data(mB.toDouble() * 1024)
        public fun ofGB(gB: Number): Data = Data(gB.toDouble() * 1024 * 1024)
    }
}
