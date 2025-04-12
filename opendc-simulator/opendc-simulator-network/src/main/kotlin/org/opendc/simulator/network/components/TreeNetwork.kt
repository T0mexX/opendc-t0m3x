package org.opendc.simulator.network.components

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.opendc.common.logger.logger
import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.HostNode.HostNodeSpecs
import org.opendc.simulator.network.components.Switch.SwitchSpecs
import org.opendc.simulator.network.utils.NonSerializable

/**
 * Naive tree topology where each node has [n] children.
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(NonSerializable::class)
internal class TreeNetwork(
    private val coreSpecs: SwitchSpecs,
    private val aggrSpecs: SwitchSpecs,
    private val accessSpecs: SwitchSpecs,
    private val hostNodeSpecs: HostNodeSpecs,
): Network() {
    override val nodesById: Map<NodeId, Node>
    override val endPointNodes: Map<NodeId, EndPointNode>
    override val internet: Internet

    val n: Int = minOf(coreSpecs.numOfPorts, aggrSpecs.numOfPorts, accessSpecs.numOfPorts) - 1
    val coreSwitches: List<CoreSwitch>
    val aggrSwitches: List<Switch>
    val accessSwitches: List<Switch>
    val hosts: List<HostNode>

    init {

        // to connect to abstract internet node.
        val coreSpecs = coreSpecs.copy(numOfPorts = coreSpecs.numOfPorts + 1)

        log.info("building tree with n=$n")

        // Build core layer.
        coreSwitches = buildList { repeat(1) { add(coreSpecs.buildCoreSwitchFromSpecs()) } }
        internet = Internet().connectedTo(coreSwitches)

        // Build aggregation layer.
        aggrSwitches = buildList {
            coreSwitches.forEach { crSw ->
                repeat(n) {
                    val aggrSw = aggrSpecs.build()
                    check(runBlocking { aggrSw.connect(crSw) })
                    add(aggrSw)
                }
            }
        }

        // Build access layer.
        accessSwitches = buildList {
            aggrSwitches.forEach { aggrSw ->
                repeat(n) {
                    val accessSw = accessSpecs.build()
                    check(runBlocking { accessSw.connect(aggrSw) })
                    add(accessSw)
                }
            }
        }

        // Build host layer.
        hosts = buildList {
            accessSwitches.forEach { accessSw ->
                repeat(n) {
                    val host = hostNodeSpecs.build()
                    check(runBlocking { host.connect(accessSw) })
                    add(host)
                }
            }
        }

        nodesById = buildMap {
            putAll((hosts + accessSwitches + aggrSwitches + coreSwitches).associateBy { it.id } )
            check(
                internet.id !in this,
            ) { "unable to create network: one node has id $INTERNET_ID, which is reserved for internet abstraction" }
            put(internet.id, internet)
        }

        endPointNodes = (coreSwitches + hosts + internet).associateBy { it.id }
    }

    override fun toSpecs(): Specs<Network> =
        TreeNetworkSpecs(
            coreSwitchSpecs = coreSpecs,
            aggrSwitchSpecs = aggrSpecs,
            torSwitchSpecs = accessSpecs,
            hostNodeSpecs = hostNodeSpecs,
        )

    companion object {
        private val log by logger()
    }

    @Serializable
    @SerialName("tree-specs")
    internal data class TreeNetworkSpecs(
        val name: String = "Default",
        val switchSpecs: SwitchSpecs? = null,
        val coreSwitchSpecs: SwitchSpecs? = null,
        val aggrSwitchSpecs: SwitchSpecs? = null,
        val torSwitchSpecs: SwitchSpecs? = null,
        val hostNodeSpecs: HostNodeSpecs,
    ) : Specs<TreeNetwork> {
        /**
         * Returns a [FatTreeNetwork] if the specs are valid, throws error otherwise.
         */
        override fun build(): TreeNetwork {
            val error by lazy {
                IllegalArgumentException(
                    "Unable to build Fat-Tree from specs. " +
                        "Either define all layers specs or provide a general switch specs",
                )
            }
            return TreeNetwork(
                coreSpecs = coreSwitchSpecs ?: run { switchSpecs ?: throw error },
                aggrSpecs = aggrSwitchSpecs ?: run { switchSpecs ?: throw error },
                accessSpecs = torSwitchSpecs ?: run { switchSpecs ?: throw error },
                hostNodeSpecs = hostNodeSpecs,
            )
        }
    }
}
