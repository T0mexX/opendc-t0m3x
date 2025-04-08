package org.opendc.simulator.network.components.node

import kotlinx.serialization.Serializable
import org.opendc.simulator.network.components.specs.WithSpecs
import org.opendc.simulator.network.utils.NonSerializable

/**
 * TODO
 * Fuck kotlin serialization
 */
@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = NonSerializable::class)
internal sealed interface SerializableNode : WithSpecs<SerializableNode> {
    fun asNode(): Node<*> = this as Node<*>
}
