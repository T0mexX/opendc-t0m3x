package org.opendc.simulator.network.utils

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle

internal suspend fun <T> withProgressBar(
    task: String = "In Progress...",
    max: Long = 0,
    block: suspend ProgressBar.() -> T
): T {
    val pb = ProgressBarBuilder()
        // Each step is building a node or adding a link.
        .setInitialMax(max)
        .setStyle(ProgressBarStyle.ASCII)
        .setTaskName(task)
        .build()

        return block(pb).also { pb.close() }
}

internal fun ProgressBar.increaseMax(by: Long) {
    this.maxHint(this.max + by)
}
