/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.components.internalstructs

import org.opendc.simulator.network.api.NodeId
import org.opendc.simulator.network.components.Node
import org.opendc.simulator.network.components.internalstructs.port.Port
import org.opendc.simulator.network.utils.RWLock

/**
 * Represents the routing table for the [Node] associated with the [ownerId] param.
 * @param[ownerId]  id of [Node] to which this table belongs.
 */
internal class RoutingTable(private val ownerId: NodeId) {
    private val rwLock = RWLock(2)

//    /**
//     * Maps the destination id to the cost (numOfHops) to that destination.
//     * It is used to share routing information to other [Node]s
//     */
//    val vector: Map<NodeId, Int>
//        get() {
//            rwLock.withRLock {
//                return table.map {
//                    it.key to (minNumOfHopsTo(it.key) ?: 0)
//                }.filterNot { it.second == 0 }.toMap()
//            }
//        }

    /**
     * This bool is updated each time a vector is merged in the routing table.
     * It is true if the merge resulted in some changes, false otherwise
     */
    var isVectChanged: Boolean = false
        private set

    var isTableChanged: Boolean = false
        private set

    /**
     * Routing table mapping [Node] id to its [PossiblePath], containing cost and nextHop.
     */
    private val table: HashMap<NodeId, MutableMap<NodeId, PossiblePath>> = HashMap()

    suspend fun getVect(): Map<NodeId, Int> =
        rwLock.withRLock {
            table.mapNotNull { (nextNodeId, _) ->
                nextNodeId to (minNumOfHopsTo(nextNodeId) ?: return@mapNotNull null)
            }.toMap()
        }

    /**
     * Adds [pathToAdd] to the routing table. If a path with
     * the same next hop is already present, it is substituted.
     * @param[pathToAdd] The possible path to add to the table.
     */
    private fun addOrReplacePath(pathToAdd: PossiblePath) {
        val destId: NodeId = pathToAdd.destinationId
        if (destId == ownerId) return

        val possiblePaths =
            table[destId] ?: let {
                table[destId] = mutableMapOf(pathToAdd.nextHop.id to pathToAdd)
                isTableChanged = true
                return
            }

        if (pathToAdd == possiblePaths[pathToAdd.nextHop.id]) return

        possiblePaths[pathToAdd.nextHop.id] = pathToAdd
        filterOutNonOptimal(destId)
        if (possiblePaths.containsKey(pathToAdd.nextHop.id)) {
            isTableChanged = true
        }
    }

    /**
     * Removes a [PossiblePath] form the routing table.
     * The combination of [destId] and [nextHopId] uniquely identifies a possible path in the table.
     * @param[destId]       final destination of the path to be removed.
     * @param[nextHopId]    the next hop [NodeId] of the path to be removed.
     */
    private fun rmPath(
        destId: NodeId,
        nextHopId: NodeId,
    ) {
        isTableChanged = (table[destId]?.remove(nextHopId) != null).or(isTableChanged)
        if (table[destId]?.isEmpty() == true) {
            table.remove(destId)
        }
    }

    /**
     * Returns possible minimal cost paths to a certain destination id.
     * @param[destId]   destination id whose possible minimal paths are to be returned.
     */
    suspend fun getPossiblePathsTo(destId: NodeId): Collection<PossiblePath> =
        rwLock.withRLock {
            table.getOrDefault(destId, mapOf()).values
        }

    /**
     * Merges routing vector of adjacent [Node].
     * @param[routingVector]    routing vector of adjacent [Node], mapping destination id to its cost (number of hops).
     */
    suspend fun mergeRoutingVector(
        routingVector: Map<NodeId, Int>,
        vectOwner: Node,
    ) {
        val routVectBefore = rwLock.withRLock { getVect() }
        rwLock.withWLock {
            isTableChanged = false
            addOrReplacePath(PossiblePath(vectOwner.id, numOfHops = 1, nextHop = vectOwner))

            val destToRemove: Set<NodeId> = table.keys.toSet() - routingVector.keys.toSet() - vectOwner.id
            destToRemove.forEach { rmPath(destId = it, nextHopId = vectOwner.id) }

            routingVector
                .forEach { (destId, numOfHops) ->
                    addOrReplacePath(
                        PossiblePath(
                            destinationId = destId,
                            numOfHops = numOfHops + 1,
                            nextHop = vectOwner,
                        ),
                    )
                }
        }

        return rwLock.withRLock {
            val routVectAfter = this.getVect()
            isVectChanged = routVectAfter != routVectBefore
            table
        }
    }

    /**
     * Removes all possible paths whose next hop is [node].
     */
    suspend fun removeNextHop(node: Node) =
        rwLock.withWLock {
            table.forEach { (_, possPaths) ->
                possPaths.remove(node.id)
            }
            table.values.removeAll { it.isEmpty() }
        }

    /**
     * Filters out non-optimal paths associated with destination
     * id [destId] if provided. Else it filters all destinations.
     */
    private fun filterOutNonOptimal(destId: NodeId? = null) {
        destId?.let {
            val minNumOfHops: Int = minNumOfHopsTo(destId) ?: return
            table[destId]?.values?.removeAll { path -> path.numOfHops > minNumOfHops } ?: false
            if (table[destId]?.isEmpty() == true) table.remove(destId)
        } ?: table.keys.forEach { filterOutNonOptimal(it) }
    }

    /**
     * Returns the minimum distance between ***this*** and the node with id [destId].
     */
    private fun minNumOfHopsTo(destId: NodeId): Int? = table[destId]?.minOf { (_, possPath) -> possPath.numOfHops }

    override fun toString(): String = table.toString()

    /**
     * Represents a possible path to the [Node] corresponding to [destinationId].
     * @param[destinationId]    id of the final destination.
     * @param[numOfHops]        number of [Link]s that need to be traversed.
     * @param[nextHop]          adjacent [Node] to which forward flows if this path is to be used.
     */
    data class PossiblePath(
        val destinationId: NodeId,
        val numOfHops: Int,
        val nextHop: Node,
    ) : Comparable<PossiblePath> {
        override fun compareTo(other: PossiblePath): Int = this.numOfHops compareTo other.numOfHops

        fun Node.associatedPort(): Port? = portToNode[this@PossiblePath.nextHop.id]
    }
}

/**
 * Type alias representing the routing vector of a [Node]. For improved readability.
 */
internal typealias RoutingVect = Map<NodeId, Int>
