package org.opendc.simulator.engine.graph;

import org.jetbrains.annotations.Nullable;
import org.opendc.simulator.network.api.node.NetworkInterface;

public interface NetworkSupplier extends FlowSupplier {

    @Nullable NetworkInterface getNetworkInterface();
}
