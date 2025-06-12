package org.opendc.simulator.network.components.networks.polarfly

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.sqrt

/**
 * @property q Prime power defining the finite field GF(q) and the polar graph ER(q)
 * @property h Terminals per router.
 *
 *
 * See: https://ieeexplore.ieee.org/abstract/document/10046084/
 */
@Serializable
internal data class PolarFlySpecs(
    val q: Int,
    val h: Int,
): NetworkSpecs<PolarFly> {
    init {
        require(q.isPrime()) { "q must be prime" }
    }

    override val R_: Int
        get() = TODO("Not yet implemented")
    override val N_: Int
        get() = TODO("Not yet implemented")
    override val V_: Int
        get() = TODO("Not yet implemented")
    override val E_: Int
        get() = TODO("Not yet implemented")

    private fun Int.isPrime(): Boolean {
        if (this < 2) return false
        val sqrt = sqrt(this.toDouble()).toInt()
        return (2..sqrt).none { this % it == 0 }
    }

    context(NetSimScope) override suspend fun build(): PolarFly = TODO()
}
