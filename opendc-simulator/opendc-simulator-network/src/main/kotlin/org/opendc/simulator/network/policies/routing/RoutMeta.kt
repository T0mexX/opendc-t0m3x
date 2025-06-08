package org.opendc.simulator.network.policies.routing

/**
 * TODO
 * Metadata associated to a single flow for a specific routing algorithm,
 * stored in the flow itslef to avoid having to use a threasd safe map or something.
 */
internal interface RoutMeta<T: RoutPolicy>
