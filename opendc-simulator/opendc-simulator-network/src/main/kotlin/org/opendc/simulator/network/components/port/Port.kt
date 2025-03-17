package org.opendc.simulator.network.components.port

import org.opendc.common.units.DataRate
import org.opendc.simulator.network.components.link.ReceiveLink2
import org.opendc.simulator.network.components.link.SendLink2
import org.opendc.simulator.network.policies.fairness.FairnessPolicy
import org.opendc.simulator.network.utils.IntId

internal interface Port: org.opendc.simulator.network.utils.notifiable.Notifiable<Port>,
    org.opendc.simulator.network.utils.statefull.Stateful<Port> {
    var rxLink: ReceiveLink2?
    var txLink: SendLink2?

    var fairnessPolicy: FairnessPolicy
    fun setTxDemand(txDemand: DataRate, entryId: IntId? = null): IntId

    val PROCESS: org.opendc.simulator.network.utils.notifiable.Notification<Port>


    companion object {
        val STABLE: org.opendc.simulator.network.utils.statefull.State<Port> = object :
            org.opendc.simulator.network.utils.statefull.State<Port> {}
        val PROCESSING: org.opendc.simulator.network.utils.statefull.State<Port> = object :
            org.opendc.simulator.network.utils.statefull.State<Port> {}
    }
}

