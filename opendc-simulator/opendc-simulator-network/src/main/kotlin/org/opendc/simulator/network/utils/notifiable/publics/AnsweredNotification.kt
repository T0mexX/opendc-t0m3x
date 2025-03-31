package org.opendc.simulator.network.utils.notifiable.publics

import kotlinx.coroutines.channels.Channel

public interface AnsweredNotification<T: Notifiable<T>, O>: Notification<T> {
    public val answerChl: Channel<O>
}
