package org.opendc.simulator.network.simscope

import org.opendc.simulator.network.components.networks.Network

/**
 * Contains configuration for a specific [Network] about
 * possible optimizations during the building process, for routing etc.,
 * that do not inherently belong to the topology specification.
 */
internal interface NetConfig<T: Network>
