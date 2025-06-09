//package org.opendc.simulator.network.components.internalstructs
//
//import org.opendc.simulator.network.components.link.SendLink
//import org.opendc.simulator.network.components.node.Node
//import org.opendc.simulator.network.components.node.NodeId
//import org.opendc.simulator.network.components.node.isConnectedTo
//
//
//internal typealias RoutVect = Map<NodeId, Int>
///**
// * TODO
// */
//internal class RoutTbl(val owner: Node<*>) {
//
//    /**
//     * TODO
//     * Handled externally to decide whether a new version of the table has been shared.
//     */
//    internal var shared: Boolean = true
//
//    /**
//     * TODO
//     * only minimal path for every possible next hop -> Rout table entry max xontains nPorts entries
//     */
//    private val dest2Entry = mutableMapOf<NodeId, RoutTblEntry>()
//
//    /**
//     * TODO
//     * The min for the distance for rout vect
//     * Routing vector.
//     */
//    private val v = mutableMapOf<NodeId, RoutTblPath>()
//
////    private fun vect(forN: Node<*>): RoutTblVect =
////        RoutTblVect(
////            owner = owner,
////            dest2Entry.values.mapNotNull { e ->
////                val min = e.nextHop2Path.filterValues { it.nextHop !== forN }.values.minOrNull()?.distance
////                min?.let { e.destId to it }
////            }.toMap()
////        )
//
////    private fun distanceTo(destId: NodeId, excludingNextHop: NodeId): Int? =
////        v[destId]?.let { path ->
////            if (path.nextHop.id != excludingNextHop) path.distance
////            else dest2Entry[destId]!!.values.filter {
////                it.nextHop.id != excludingNextHop
////            }.min().distance
////        }
//
//    /**
//     * @return a [RoutVect] ready to be sent to [n],
//     * including only minimal paths that do not use [n] as next hop.
//     *
//     * TODO optimize
//     */
//    internal fun routVectFor(n: Node<*>): RoutVect {
//        // Assert `n` is in fact an adjacent node.
//        assert(owner isConnectedTo n)
//
//        return v.values.mapNotNull { p ->
//            // Exclude the distance to `n` itself.
//            if (p.destId == n.id) return@mapNotNull null
//
//            if (p.nextHop !== n) return@mapNotNull p.destId to p.distance
//
//
//            // If the minimum path to `destId` is with `n` as nextHop, replace it with the second smallest
//            dest2Entry[p.destId]!!.filterValues { it.nextHop !== n }.values.minOrNull()?.let { secondMin ->
//                p.destId to secondMin.distance
//            }
//        }.toMap()
//    }
//
//    /**
//     * TODO
//     */
//    private fun registerAdjacentNode(n: Node<*>) {
//        // The entry corresponding to the possible paths to destination node `n`.
//        val e = dest2Entry.getOrPut(n.id) { RoutTblEntry(destId = n.id) }
//
//        // Assert that `n` is in fact an adjacent node.
//        assert(owner isConnectedTo n)
//
//        // Assert that a possible path with `n` as next hop (directly connected)
//        // was not already present.
//        assert(n.id !in e)
//
//        e[n.id] = RoutTblPath(destId = n.id, nextHop = n, distance = 1)
//
//        // Set distance to `n` as 1 since adjacent in the routing vector.
//        v[n.id] = e[n.id]!!
//    }
//
//    internal fun deregisterAdjacentNode(n: Node<*>) {
//        // The entry corresponding to the possible paths to destination node `n`.
//        val e = dest2Entry[n.id]!!
//
//        // Assert there was a path with distance 1 (directly connected) to `n`.
//        assert(n.id in e)
//
//        // Remove the path of distance 1.
//        e.remove(n.id)
//
//        // If no other path is available, remove the entry for this destination entirely.
//        if (e.isEmpty()) dest2Entry.remove(n.id)
//    }
//
//    internal fun getPossiblePathsTo(nId: NodeId): Collection<RoutTblPath> =
//        dest2Entry[nId]?.values ?: emptyList()
//
//    /**
//     * TODO
//     * @param otherN An adjacent node to get routing information from.
//     * @param otherV The routing vector sent by [otherN].
//     * @return `true` if the merge changed this node routing vector, `false` otherwise.
//     */
//    internal fun updtWithInfoFrom(otherV: RoutVect, otherN: Node<*>): Boolean {
//        assert(owner isConnectedTo otherN)
//
//        // If at any point during merge distances change, this is set to `true`.
//        var changed = false
//
//        if (otherN.id !in v) {
//            changed = true
//            registerAdjacentNode(otherN)
//        }
//
//        otherV.keys.forEach { destId ->
//            assert(destId != owner.id)
//            dest2Entry.putIfAbsent(destId, RoutTblEntry(destId))
//        }
//
//        dest2Entry.keys.forEach { destId ->
//            assert(destId != owner.id)
//
//            // The entry in this table corresponding to `destId`.
//            val e = dest2Entry[destId]!!
//            // The path in this table corresponding to `destId` and `other.owner` as next hop.
//            val p = e[otherN.id]
//            // The distance of `other` from `destId` not including this node as nextHop.
//            val otherDistance = otherV[destId]
//
//            if (p == null && otherDistance == null) return@forEach
//
//            // If the path with `other.owner` as next hop is new.
//            if (p == null) {
//                e[otherN.id] = RoutTblPath(destId, nextHop = otherN, distance = otherDistance!! + 1)
//
//            } else if (otherDistance == null) {
//                // If not present in vector of `otherN` because `otherN` is the destination.
//                if (p.destId == otherN.id) return@forEach
//
//                e.remove(otherN.id)
//                if (e.isEmpty()) dest2Entry.remove(destId)
//
//            // Determine if the number of hops for this path with `other.owner` as next hop needs to be changed.
//            } else {
//                // If no changes.
//                if (p.distance == otherDistance + 1) return@forEach
//
//                p.distance = otherDistance + 1
//            }
//
//            // If this line is reached, changes have been made.
//            changed = true
//            // Update routing vector.
//            e.values.minOrNull()?.let { minDistance ->
//                v[destId] = minDistance
//            } ?: v.remove(destId)
//        }
////
////        (v.keys + otherV.keys).forEach { destId ->
////            assert(destId != owner.id)
////
////            // The entry in this table corresponding to `destId`.
////            val e = dest2Entry.getOrPut(destId) { RoutTblEntry(destId) }
////            // The path in this table corresponding to `destId` and `other.owner` as next hop.
////            val p = e[otherN.id]
////            // The distance of `other` from `destId` not including this node as nextHop.
////            val otherDistance = otherV[destId]
////
////            // If no previous or new path to `destId` with `otherN` as next hop.
////            if (p == null && otherDistance == null) return@forEach
////
////            // If the path with `other.owner` as next hop is new.
////            if (p == null) {
////                e[otherN.id] = RoutTblPath(destId, nextHop = otherN, distance = otherDistance!! + 1)
////
////            // If the path with `other.owner` as next hop does not exist anymore.
////            } else if (otherDistance == null) {
////                // If not present in vector of `otherN` because `otherN` is the destination.
////                if (p.destId == otherN.id) return@forEach
////
////                e.remove(otherN.id)
////                if (e.isEmpty()) dest2Entry.remove(destId)
////
////            // Determine if the number of hops for this path with `other.owner` as next hop needs to be changed.
////            } else {
////                // If no changes.
////                if (p.distance == otherDistance + 1) return@forEach
////
////                p.distance = otherDistance + 1
////            }
////
////            // If this line is reached, changes have been made.
////            changed = true
////            // Update routing vector.
////            e.values.minOrNull()?.let { minDistance ->
////                v[destId] = minDistance
////            } ?: v.remove(destId)
////        }
//
//        return changed
//    }
//
//    /**
//     * TODO
//     */
//    private data class RoutTblEntry(
//        val destId: NodeId,
//        private val nextHop2Path: MutableMap<NodeId, RoutTblPath> = mutableMapOf(),
//    ): MutableMap<NodeId, RoutTblPath> by nextHop2Path
//
//    /**
//     * TODO
//     */
//    data class RoutTblPath(
//        val destId: NodeId,
//        val nextHop: Node<*>,
//        var distance: Int = -1,
//    ): Comparable<RoutTblPath> {
//        override fun compareTo(other: RoutTblPath): Int = distance - other.distance
//
//        context(Node<*>)
//        internal fun associatedLink(): SendLink =
//            links.find { it?.receiverN === nextHop }!!
//    }
//}
