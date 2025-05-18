package org.opendc.simulator.network.components.specs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.DragonFly
import org.opendc.simulator.network.simscope.NetSimScope

/**
 * @param a Number of routers in each group.
 * @param g Number of groups in the network.
 * @param p Number of terminals connected to each router.
 * @param h Number of intergroup connections for each router.
 * Default is maximum given the other parameters.
 *
 * Source: https://dl.acm.org/doi/abs/10.1145/1394608.1382129
 *
 * The names of the parameters are those used in the paper.
 */
@Serializable
@SerialName("dragonfly")
internal data class DragonFlySpecs(
    val a: Int,
    val g: Int,
    val p: Int = a/2,
    val h: Int = a/2,
    val globalSwitchesPerGroup: Int = 1,
    val switchSpecs: SwitchSpecs,
    val hostSpecs: HostNodeSpecs,
): NetworkSpecs<DragonFly> {
    /**
     * Number of routers (switches) in the network.
     */
    override val R_: Int = a * g

    /**
     * Number of terminals (hosts) in the network.
     */
    override val N_: Int = a * p * g

    /**
     * Number of vertices (nodes) in the network.
     */
    override val V_: Int = R_ + R_ * p

    /**
     * Number of edges (links) in the network.
     */
    override val E_: Int = let {
        val intraGroupsSw2Sw = (a *  (a - 1) / 2) * g
        val intraGroupH2Sw = a * p * g
        val interGroup = R_ * h / 2

        intraGroupH2Sw + intraGroupsSw2Sw + interGroup
    }

    /**
     * Property of dragonfly. `true` if there is exactly one connection between each pair of groups
     */
    val isMaxSize: Boolean = N_ == a * p * (a * h + 1)

    init {
        // "The network should be balanced so that a ≥ 2h, 2p ≥ 2h"
        require(a >= 2 * h && 2 * p >= 2 * h)
        // The number of global switches (inet connection) per group needs
        // to be lower or equal to the number of switches in each group.
        require(globalSwitchesPerGroup <= a)

        require(  (a * g) % 2 == 0 )
    }

    context(NetSimScope) override suspend fun build(): DragonFly = DragonFly(specs = this)
}
