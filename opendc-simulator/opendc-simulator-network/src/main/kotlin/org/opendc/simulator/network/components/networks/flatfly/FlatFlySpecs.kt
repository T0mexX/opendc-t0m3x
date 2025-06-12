package org.opendc.simulator.network.components.networks.flatfly

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetworkSpecs
import org.opendc.simulator.network.simscope.NetSimScope
import kotlin.math.pow

/**
 * Specification for building a [FlatFly] (*Flatten Butterfly*) network topology.
 *
 * @property n The number of dimensions in the network.
 * @property k The network radix, in this case, the number of routers (switches) per dimension.
 * @property c Concentration of the network, number of
 * endpoints/hosts (also called terminals) connected to each router.
 */
@Serializable
@SerialName("flatfly")
internal data class FlatFlySpecs(
    val n: Int,
    val k: Int,
    val c: Int = k,
): NetworkSpecs<FlatFly> {
    /**
     * Total radix of a switch (number of ports).
     */
    val r: Int = c + (k - 1) * n

    override val R_: Int = k.toDouble().pow(n).toInt()

    override val N_: Int = R_ * c

    override val V_: Int = R_ + N_

    override val E_: Int = ( ( (k - 1) * n * R_ ).also { assert(it % 2 == 0) } / 2 ) + (c * R_)

    context(NetSimScope) override suspend fun build(): FlatFly = FlatFly(this)
}
