package org.opendc.simulator.network.utils.evntemitter.publics

import org.opendc.simulator.network.utils.flyweight.publics.FW

public interface Evnt<Self: Evnt<Self, T>, in T: EvntEmitter<in T>> : FW<Self>
