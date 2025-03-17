package org.opendc.simulator.network.utils


@RequiresOptIn(message = "part of api for implementation reasons, but shouldn't be used externally", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
public annotation class InternalOdcNetworkApi
