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

package org.opendc.simulator.network.utils

import org.opendc.common.units.DataRate
import org.opendc.common.units.DataSize
import org.opendc.common.units.Time
import org.opendc.common.units.Unit
import org.opendc.common.utils.ifNaN

internal inline fun <reified T : Unit<T>> T?.ifNull0(): T =
    this
        ?: when (T::class) {
            DataRate::class -> DataRate.ZERO as T
            DataSize::class -> DataSize.ZERO as T
            Time::class -> Time.ZERO as T
            else -> throw RuntimeException("change") // TODO: add others
        }

// TODO: refactor to use commons.units.percentage
internal fun Double.ratioToPerc(fmt: String): String = "${String.format(fmt, (this * 100).ifNaN(.0))}%"

internal fun Double.fmt(fmt: String): String = String.format(fmt, this)
