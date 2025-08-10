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

package org.opendc.common

import me.tongfei.progressbar.DelegatingProgressBarConsumer
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import java.util.function.Consumer

/**
 * Intuitive and short way to have a progress bar to execute a certain block of code.
 * The progress bar will be automatically created with the given parameters,
 * and closed at the end of the execution of [block].
 *
 * ```kotlin
 * val result = withProgressBar(task = "Computing factorial of 20...", max = 100) pb@ {
 *    (2..100).fold { acc, num ->
 *       println(num)
 *       this@pb.step() // or simply `step()`
 *       acc * num
 *    }
 * }
 * ```
 *
 * For more complex operations or a chain of operations, the progressbar can
 * be passed as context to another function, instead of creating a new one,
 * and that function is able to increase the number of steps required to completion.
 * ```koltin
 * withProgressBar(task = "Some Task...", max = 0) pb@ {
 *   operation1()
 *   operation2()
 * }
 *
 * context(ProgressBar) fun operation1() {
 *    this@ProgrssBar.increaseMax(20) // or simply `increaseMax(20)
 *    // Perform 20 steps.
 * }
 * context(ProgressBar) fun operation1() {
 *    this@ProgrssBar.increaseMax(30)
 *    // Perform 30 steps.
 * }
 * ```
 *
 * @param task Name displayed in console.
 * @param max The initial target number of steps.
 * @param block The block to be executed with a progressbar.
 */
public suspend fun <T> withProgressBarSus(
    task: String = "In Progress...",
    max: Long = -1, // Default: unknown total
    tty: Boolean = true,
    block: suspend ProgressBar.() -> T,
): T {
    // Set up the progress bar.
    val pb = pb(max, task, tty)

    // Ensure that the progress bar is closed after [block] executed.
    return pb.use { pb ->
        block(pb)
    }
}


/**
 * Non suspending version of [withProgressBarSus].
 * @see withProgressBarSus
 */
@JvmOverloads
public fun <T> withProgressBar(
    task: String = "In Progress...",
    max: Long = -1, // Default: unknown total
    tty: Boolean = true,
    block: ProgressBar.() -> T,
): T {
    // Set up the progress bar.
    val pb = pb(max, task, tty)

    // Ensure that the progress bar is closed after [block] executed.
    return pb.use { pb ->
        block(pb)
    }
}

/**
 * Unit returning version the one above. To avoid inserting last line
 * `return null` when the generic version is invoked from java and the type parameter is [Unit].
 */
@JvmOverloads
public fun withProgressBar(
    task: String = "In Progress...",
    max: Long = -1,
    tty: Boolean = true,
    block: Consumer<ProgressBar>,
) {
    val pb = pb(max, task, tty)

    pb.use { block.accept(it) }
}

public fun ProgressBar.increaseMax(by: Long) {
    this.maxHint(this.max + by)
}

private fun pb(max: Long, task: String, tty: Boolean): ProgressBar {
    return ProgressBarBuilder()
        .setInitialMax(max)
        .setStyle(ProgressBarStyle.ASCII)
        .setTaskName(task)
        .also {
            if (!tty) {
                it.setConsumer(DelegatingProgressBarConsumer { str -> println(str) })
            }
        }
        .build()
}


