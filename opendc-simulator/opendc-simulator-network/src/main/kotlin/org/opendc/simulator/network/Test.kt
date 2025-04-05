package org.opendc.simulator.network

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter

internal fun main() {


    val process = ProcessBuilder("/home/t0m3x/.jdks/corretto-19.0.2/bin/java", "-cp", "out/production/classes", "org.opendc.simulator.network.repl.REPLKt")
        .redirectErrorStream(true)
        .start()

//    val inputStream = process.inputStream
//    val reader = BufferedReader(InputStreamReader(inputStream as InputStream))


    val writer = PrintWriter(process.outputStream)
    writer.println("Hello from the first main!")
    writer.flush()

    // Read output from the second main function
    val output = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    while (output.readLine().also { line = it } != null) {
        println("Second Main says: $line")
    }
}
