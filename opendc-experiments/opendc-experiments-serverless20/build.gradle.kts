/*
 * Copyright (c) 2020 AtLarge Research
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

description = "Experiments for OpenDC Serverless"

/* Build configuration */
plugins {
    `kotlin-library-conventions`
    `experiment-conventions`
    `testing-conventions`
}

dependencies {
    api(platform(project(":opendc-platform")))
    api(project(":opendc-harness:opendc-harness-engine"))
    implementation(project(":opendc-serverless:opendc-serverless-service"))
    implementation(project(":opendc-serverless:opendc-serverless-simulator"))
    implementation(project(":opendc-telemetry:opendc-telemetry-sdk"))
    implementation(project(":opendc-harness:opendc-harness-cli"))
    implementation("io.github.microutils:kotlin-logging")
    implementation("com.typesafe:config")

    implementation("org.apache.parquet:parquet-avro:${versions["parquet-avro"]}")
    implementation("org.apache.hadoop:hadoop-client:${versions["hadoop-client"]}") {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j")
    }
}
