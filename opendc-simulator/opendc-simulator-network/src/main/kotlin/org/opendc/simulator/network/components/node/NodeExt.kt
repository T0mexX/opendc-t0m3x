package org.opendc.simulator.network.components.node

import org.opendc.simulator.network.components.port.Port

/**
 *
 */


/**
 * Used especially for assertions disabled at compile time.
 */
internal infix fun Node<*>.isConnectedTo(otherN: Node<*>): Boolean =
    this.ports.any { it.connectedNode() === otherN }


internal fun Port.connectedNode(): Node<*>? =
    this.connectedPort()?.owner

internal fun Port.connectedPort(): Port? =
    this.txLink?.receiverPort
