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

import mu.KotlinLogging
import org.slf4j.Logger

/**
 * Returns a [Logger] with the class name of the caller, even if the caller is a companion object.
 */
internal fun <T : Any> T.logger(name: String? = null): Lazy<Logger> {
    return lazy {
        KotlinLogging.logger(
            name
                ?: runCatching { this::class.java.enclosingClass.simpleName }
                    .getOrNull()
                ?: "unknown",
        )
    }
}

internal fun Logger.errAndNull(msg: String): Nothing? {
    this.error(msg)
    return null
}

public fun Logger.warnAndNull(msg: String): Nothing? {
    this.warn(msg)
    return null
}

internal fun <T> Logger.withWarn(
    obj: T,
    msg: String,
): T {
    this.warn(msg)
    return obj
}

internal fun <T> Logger.withErr(
    obj: T,
    msg: String,
): T {
    this.error(msg)
    return obj
}

internal fun Logger.infoNewLn(msg: String) {
    info("\n" + msg)
}
