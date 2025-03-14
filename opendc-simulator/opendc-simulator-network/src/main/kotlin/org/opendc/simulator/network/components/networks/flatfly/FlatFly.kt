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

package org.opendc.simulator.network.components.networks.flatfly

import inet.ipaddr.ipv4.IPv4Address
import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.networks.NetworkImpl
import org.opendc.simulator.network.components.node.Node
import org.opendc.simulator.network.components.node.NodeId
import org.opendc.simulator.network.components.node.SenderNode
import org.opendc.simulator.network.components.node.inet.Internet
import org.opendc.simulator.network.components.node.switchh.Switch
import org.opendc.simulator.network.components.node.terminal.Terminal
import org.opendc.simulator.network.simscope.NetSimScope
import org.opendc.simulator.network.simscope.barrier.NetSimStabilityMode
import org.opendc.simulator.network.utils.NonSerializable
import org.opendc.simulator.network.utils.datastructures.MultiDimGrid
import org.opendc.simulator.network.utils.withProgressBar

/**
 * Represents a Flattened Butterfly (FlatFly) network topology.
 *
 * The Flattened Butterfly is a high-radix, direct network topology that
 * combines the low diameter and high bandwidth characteristics of
 * traditional butterfly networks into a single-stage flattened structure.
 * It reduces the number of hops and router stages compared to classic
 * multi-stage butterfly networks by directly connecting routers in a
 * multi-dimensional topology.
 * @property swGrid Grid of `specs.n` dimensions of `specs.k` size each.
 * Contains all the switches in the network. Enables to easily retrieve a switch given the coordinates.
 * @property hGrid Grid of `specs.n + 1` dimensions of `specs.k` size each
 * (except the last dimension with size `specs.c`).
 * Contains all the hosts in the grid. Hosts (terminals) connected to switch with coordinates (x, y, z) can be retrieved
 * with indices (x, y, z, [0..specs.c)).
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
internal class FlatFly private constructor(
    override val specs: FlatFlySpecs,
    val swGrid: MultiDimGrid<Switch>,
    val hGrid: MultiDimGrid<Terminal>,
    override val inet: Internet,
) : NetworkImpl<FlatFly>() {
    @Suppress("UNCHECKED_CAST")
    override val sendNodesById: MutableMap<NodeId, SenderNode<*>> =
        (hGrid.associateBy { it!!.id }.toMutableMap() + (inet.id to inet)) as MutableMap<NodeId, SenderNode<*>>

    @Suppress("UNCHECKED_CAST")
    override val nodesById: MutableMap<NodeId, Node<*>> =
        (sendNodesById + swGrid.associateBy { it!!.id }.toMutableMap()) as MutableMap<NodeId, Node<*>>

    context(NetSimScope)
    override suspend fun fmt(mode: NetSimStabilityMode): String {
        return super.fmt(mode) +
            """
            ${'\u200B'}
             | n (# dimensions): ${specs.n}
             | k (dimensions size): ${specs.k}
             | c (terminals per router): ${specs.c}
             | r (switch total radix): ${specs.r}
            """.trimIndent()
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Suspending Constructor
    // //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        /**
         * Suspending constructor.
         * @param specs The specs of the dragonfly topology to build.
         */
        context(NetSimScope)
        suspend operator fun invoke(specs: FlatFlySpecs): FlatFly =
            withProgressBar(task = "Building Flatten Butterfly...", max = specs.E_.toLong() + specs.V_) pb@{
                // Implemented only using subnets.
                if (this@NetSimScope.devConfig.netConfig.subnetOpt.not()) {
                    log.warn(
                        "'netSimConfig.devConfig.netConfig.subnetOptimization = false' detected. " +
                            "FlatFly topology requires 'subnetOptimization = true' to function correctly. " +
                            "This setting will be ignored.",
                    )
                }

                val inet = Internet()

                // Grid with `specs.n` dimensions each of size `specs.k`.
                val swGrid = MultiDimGrid<Switch>(List(specs.n) { specs.k })
                // Grid with `specs.n + 1` dimensions. Dimensions `1..specs.n` have size `specs.k`,
                // dimension `specs.n + 1` has size `specs.c` for the hosts.
                val hGrid = MultiDimGrid<Terminal>(MutableList(specs.n + 1) { specs.k }.also { it[it.size - 1] = specs.c })

                /**
                 * Executed recursively on the [FlatFlySpecs.n] dimensions.
                 * Builds all nodes.
                 */
                suspend fun buildNodes(
                    outerSubNet: IPv4Address,
                    vararg indices: Int,
                ) {
                    // The array of indices used for next dimension.
                    // A `0` is added at the end of the array and will be
                    // replaced for each recursive call.
                    val nextIndices = indices + 0
                    // If this is the last dimensions,
                    // build `k` switches in this dimension and `k` * `c` hosts,
                    // inside the current subnet.
                    if (indices.size == specs.n - 1) {
                        val hostIndices = nextIndices + 0
                        (0..<specs.k).forEach { sIdx ->
                            // Newly built switch.
                            val newS = Switch(subnet = outerSubNet, nPorts = specs.r)
                            nextIndices[nextIndices.size - 1] = sIdx
                            newS.topNodeMeta = FlatFlyNodeMeta(coord = nextIndices.copyOf())
                            swGrid.set(newS, *nextIndices)
                            this@pb.step()

                            // Build `specs.c` hosts (terminals) connected to the newly built switch.
                            (0..<specs.c).map {
                                Terminal(subnet = outerSubNet, nPorts = 1)
                            }.forEachIndexed { hIdx, newH ->
                                hostIndices[hostIndices.size - 2] = sIdx
                                hostIndices[hostIndices.size - 1] = hIdx
                                newH.topNodeMeta = FlatFlyNodeMeta(coord = hostIndices.copyOf())
                                hGrid.set(newH, *hostIndices)
                                newH.msgSyncConnect(other = newS)
                                this@pb.stepBy(2L)
                            }
                            barrier.awaitStability()
                        }

                        // If this is not the last dimension, then create subnet
                        // for each idx in this dimension and keep recursion.
                    } else {
                        val nextSubNetNSwitches = swGrid.subGridSize(*nextIndices)
                        val nextSubNetSz = nextSubNetNSwitches * specs.c + nextSubNetNSwitches
                        (0..<specs.k).forEach { idx ->
                            val nextSubNet = addrMngr.getNewSubNet(of = outerSubNet, nIps = nextSubNetSz)
                            nextIndices[nextIndices.size - 1] = idx
                            buildNodes(nextSubNet, *nextIndices)
                        }
                    }
                }

                buildNodes(outerSubNet = addrMngr.globalPrefix)

                // Establish connections between routers (switches).
                // Each rooter is connected to k-1 routers in each dimension.
                // E.g., router with coordinates (1, 2, 3, 4) will be connected to
                // - routers (x, 2, 3, 4) where x in [0, k-1) / 1
                // - routers (1, x, 3, 4) where x in [0, k-1) / 2
                // - etc.
                swGrid.forEach outer@{ sw ->
                    val coord = (sw!!.topNodeMeta!! as FlatFlyNodeMeta).coord
                    val targetCoord = coord.copyOf()
                    (0..<specs.n).forEach middle@{ dim ->
                        (0..<specs.k).forEach inner@{ dimCoord ->
                            // Avoid connecting router to itself.
                            if (dimCoord == coord[dim]) return@inner
                            targetCoord[dim] = dimCoord
                            val targetSw = swGrid.get(*targetCoord)!!
                            if (sw.isConnectedTo(targetSw)) return@inner
                            sw.msgSyncConnect(targetSw)
                            this@pb.step()
                        }
                        // Reset the coordinate that has been looped.
                        targetCoord[dim] = coord[dim]
                    }
                }

                FlatFly(
                    specs = specs,
                    swGrid = swGrid,
                    hGrid = hGrid,
                    inet = inet,
                ).also {
                    // Setup global routing policy if needed.
                    this@NetSimScope.config.routPolicy.setUp()

                    // Register the network in the simulationscope.
                    this@NetSimScope.registerNetwork(it)
                }
            }
    }
}
