/*
 * Copyright (c) 2025 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.common.units

/**
 * To be used as an enum-like object, to be passed
 * functions that do not have a parameter of the generic unit type
 * and cannot be reified.
 */
public interface UnitType<T : Unit<T>> {
    public val zero: T
    public val max: T

    /**
     * This method allows instantiating a [Unit] from a double, assuming the double is the base [Unit] representation.
     * This is especially useful when complete/reified types are necessary and `T` in `Unit<T> is not reified/complete
     * (e.g., if working with arrays for performance reasons), without knowing what [Unit] it is.
     *
     * ```kotlin
     * fun <T: Unit<T>> arrayWork(unit: T) {
     *    // Step 1: retrieve `UnitType`
     *    val unitType: UnitType<*> = unit.unitType
     *    // Step 2: convert to double
     *    val unitBase: Double = unit.toBase()
     *    // Step 3: perform some array operation
     *    // (note: this is not possible without complete/reified type)
     *    val myArray = Array(250) { unitBase }
     *    ⋮
     *    val outputUnitBase: Double = <something>
     *    // Step 4: convert to original UnitType
     * }
     * ```
     * @see Unit.toBase
     */
    @Unit.UnsafeUnitOperation
    public fun ofBase(value: Double): T
}
