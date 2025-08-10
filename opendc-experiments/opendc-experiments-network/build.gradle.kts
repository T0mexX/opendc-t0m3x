/*
 * Copyright (c) 2022 AtLarge Research
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

description = "Support library for simulating network workloads with OpenDC"

plugins {
    `kotlin-library-conventions`
    `testing-conventions`
    `jacoco-conventions`
    distribution
    kotlin("plugin.serialization") version "1.9.22"
    application
}

private val kotlinxVersion = "1.6.0"
dependencies {
    implementation(projects.opendcCommon)
    implementation(projects.opendcSimulator.opendcSimulatorNetwork)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxVersion")

    implementation(project(":opendc-simulator:opendc-simulator-network")) {
        exclude(group = "com.github.ajalt", module = "clikt") // excludes 2.8.0
    }
    implementation(libs.clikt)
}

application {
    mainClass.set("org.opendc.experiments.network.runner.NetworkExpCliKt")
}


configurations.all {
    resolutionStrategy {
        force("com.github.ajalt.clikt:clikt:3.5.2")
    }
}


tasks.named<JavaExec>("run") {
    workingDir = file("$projectDir/src/main")
}
