package org.opendc.simulator.network.energy

import org.opendc.simulator.network.units.Power
import org.opendc.simulator.network.utils.OnChangeHandler
import kotlin.properties.Delegates

/**
 * Monitors the energy consumption of [monitored] of type `T`.
 * @param[monitored]    network component monitored by ***this***.
 * @param[enModel]      energy model to use to compute current energy consumption for [monitored] network component.
 * TODO: maybe store records of energy changes, this can be done on network level or both component level and network.
 */
internal class EnMonitor<T: EnergyConsumer<T>>(
    private val monitored: T,
    private val enModel: EnModel<T> = monitored.getDfltEnModel()
) {
    /**
     * Callback functions of the observers of the [currConsumpt] field.
     */
    private val obs: MutableList<OnChangeHandler<EnMonitor<T>, Power>> = mutableListOf()

    /**
     * The current energy consumption of the [monitored] network component.
     */
    private var currConsumpt: Power by Delegates.observable(Power.ZERO) { _, oldValue, newValue ->
        obs.forEach { it.handleChange(this, oldValue, newValue) }
    }

    /**
     * Adds an observer callback function.
     * @param[f]    callback function of the new observer.
     */
    fun addObserver(f: OnChangeHandler<EnMonitor<T>, Power>) { obs.add(f) }

    /**
     * Updates the energy consumption of [monitored].
     *
     * Ideally called by [monitored] whenever its state changes.
     */
    fun update() { currConsumpt = enModel.computeCurrConsumpt(monitored) }
}
