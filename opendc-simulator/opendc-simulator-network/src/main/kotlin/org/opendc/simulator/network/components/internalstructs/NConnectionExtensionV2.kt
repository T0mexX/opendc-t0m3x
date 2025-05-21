package org.opendc.simulator.network.components.internalstructs

//import org.opendc.simulator.network.components.node.Node
//import org.opendc.simulator.network.components.shareRoutingVect



//
//internal suspend fun Node<*>.shareRoutInfo() {
//    this.forEachAdjN { adjN ->
//        // TODO: remove cast
//        (adjN.routTbl as RoutTbl).updtWithInfoFrom(this)
//            .let { changed ->
//                if (changed)
//            }
//
//    }
//}
//
//
//
///**
// * Merges [routVect] in the [this.routingTable], updating flowsById
// * and sharing its updated routing vector if needed.
// * @return its own routing vector.
// */
//internal suspend fun Node<*>.exchangeRoutVect(
//    routVect: RoutingVect,
//    vectOwner: Node<*>,
//    except: Set<Node<*>> = setOf(),
//): RoutingVect {
//    routTbl.mergeRoutingVector(routVect, vectOwner)
//
//    if (!routTbl.isTableChanged) return routTbl.getVect()
////    with(flowHandler) { updtAllRouts() }
//
////    updateAllFlows()
////    TODO()
//
//    if (!routTbl.isVectChanged) return routTbl.getVect()
//    shareRoutingVect(except = setOf(vectOwner) + except)
//
//    return routTbl.getVect()
//}
//
//
//private suspend fun Node<*>.forEachAdjN(block: suspend (Node<*>) -> Unit) {
//    ports.forEach { p ->
//        p.txLink ?: return@forEach
//        block(p.txLink!!.receiverNode)
//    }
//}
