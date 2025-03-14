/*
 * Copyright (c) 2024 AtLarge Research
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

package org.opendc.simulator.network.input

import org.apache.hadoop.conf.Configuration
import org.apache.parquet.hadoop.api.InitContext
import org.apache.parquet.hadoop.api.ReadSupport
import org.apache.parquet.io.api.RecordMaterializer
import org.apache.parquet.schema.MessageType
import org.apache.parquet.schema.Type
import org.apache.parquet.schema.Types
import org.opendc.common.logger.infoNewLine
import org.opendc.simulator.network.api.workload.SimNetWorkload

internal class ImprintReadSupp : ReadSupport<NetEventImprint>() {
    private lateinit var readingSchema: MessageType

    override fun init(context: InitContext): ReadContext {
        // Builds the reading schema, with all the compatible columns
        // that are found in the file schema
        val msgBuilder = Types.buildMessage()
        val fileFields = context.fileSchema.fields

        // Check mandatory fields are contained in the file schema.
        mandatoryColumns.forEach {
            check(it presentIn fileFields) {
                "column $it should be in schema but was not found\nschema found: $fileFields"
            }
            msgBuilder.addField(it)
        }

        // Checks which non-mandatory columns are contained in the file schema.
        notMandatoryColumns.forEach {
            if (it presentIn fileFields) {
                msgBuilder.addField(it)
            }
        }

        readingSchema = msgBuilder.named("net_wl_reading_schema")

        SimNetWorkload.LOG.infoNewLine(
            "| === network workload reading schema ===\n$readingSchema"
                .trimEnd()
                .replace(
                    oldValue = "\n",
                    newValue = "\n| ",
                ),
        )

        return ReadContext(readingSchema)
    }

    override fun prepareForRead(
        configuration: Configuration?,
        keyValueMetaData: MutableMap<String, String>?,
        fileSchema: MessageType,
        readContext: ReadContext,
    ): RecordMaterializer<NetEventImprint> = ImprintMaterializer(readContext.requestedSchema)
}

/**
 * @return `true` if [this] (or a more restrictive version of this) is present in [list], otherwise `false`.
 * This method allows columns that can be optional to be required in the file schema.
 */
private infix fun Type.presentIn(list: List<Type>): Boolean =
    list.any { it == this } ||
        list.any {
            it.name == this.name &&
                it.asPrimitiveType().primitiveTypeName == this.asPrimitiveType().primitiveTypeName &&
                it.repetition.isMoreRestrictiveThan(this.repetition)
        }
