package org.opendc.simulator.network.components.networks.polarfly

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * @property q Prime power defining the finite field GF(q).
 * @property p Router radix, total number of ports per router (e.g., 16, 32).
 * @property h Terminals per router.
 *
 *
 * See: https://ieeexplore.ieee.org/abstract/document/10046084/
 */
@Serializable
internal data class PolarFlySpecs(
    val q: Int,
    val p: Int,
    val h: Int,
) {
    init {
        require(q.isPrime()) { "q must be prime" }
    }


    private fun Int.isPrime(): Boolean {
        if (this < 2) return false
        val sqrt = sqrt(this.toDouble()).toInt()
        return (2..sqrt).none { this % it == 0 }
    }
}
