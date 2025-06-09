package org.opendc.simulator.network.components.node


/**
 * Used especially for assertions disabled at compile time.
 */
internal infix fun Node<*>.isConnectedTo(otherN: Node<*>): Boolean =
    this.links.any { it?.receiverN === otherN }
