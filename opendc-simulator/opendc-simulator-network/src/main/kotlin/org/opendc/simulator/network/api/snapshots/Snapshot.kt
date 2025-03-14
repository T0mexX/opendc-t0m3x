/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.api.snapshots

import org.opendc.common.units.Percentage
import org.opendc.common.units.Percentage.Companion.ofRatio
import org.opendc.common.units.Percentage.Companion.percentageOf
import org.opendc.common.units.Unit
import org.opendc.simulator.network.utils.Flags

public abstract class Snapshot<T> {
    protected abstract val dfltColWidth: Int

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return the formatted string representing the snapshot information,
     * as either 1 or 2 lines with a column for each property.
     */
    public abstract fun fmt(flags: Flags<T> = Flags.all()): String

    /**
     * @param[flags]    flags representing which property
     * need to be included in the formatted string.
     * @return a string containing only the headers of the snapshot.
     */
    public abstract fun fmtHdr(flags: Flags<T> = Flags.all()): String

    protected fun StringBuilder.appendPad(
        obj: Any?,
        pad: Int = dfltColWidth,
    ) {
        obj?.let {
            append(obj.toString().padEnd(pad))
        } ?: append("N/A".padEnd(pad))
    }

    protected fun StringBuilder.appendPad(
        str: String,
        pad: Int = dfltColWidth,
    ) {
        append(str.padEnd(pad))
    }

    protected companion object {
        @JvmStatic
        protected infix fun Number.roundedPercentageOf(other: Number): Percentage =
            (this percentageOf other)
                .roundToIfWithinEpsilon(to = ofRatio(1.0))
                .roundToIfWithinEpsilon(to = ofRatio(.0))

        @JvmStatic
        protected infix fun <T : Unit<T>> T.roundedPercentageOf(other: T): Percentage =
            (this percentageOf other)
                .roundToIfWithinEpsilon(to = ofRatio(1.0))
                .roundToIfWithinEpsilon(to = ofRatio(.0))
    }
}
