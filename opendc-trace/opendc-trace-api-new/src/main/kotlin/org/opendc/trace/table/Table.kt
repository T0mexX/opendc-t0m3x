package org.opendc.trace.table

import org.opendc.trace.util.errAndNull
import org.opendc.trace.util.logger
import java.io.File
import java.nio.file.Path

public abstract class Table {

    /**
     * The artificially added columns.
     * @see[withArtificialColumn]
     */
    protected val artificialCols: MutableMap<String, Any> = mutableMapOf()

    public val columnNames: Set<String>
        get() = getReader().colNames

    /**
     * The name of the table.
     */
    public abstract val name: String

    /**
     * The size of the table in bytes.
     * If the table is a concatenation of multiple table, then it returns the sum of the sub-tables.
     */
    public abstract val sizeInBytes: Long

    /**
     * @return      A reader for the table, which enables stream-fashion reading, as well as automated.
     * @see[TableReader]
     */
    public fun getReader(): TableReader =
        getReader(this.artificialCols)

    protected abstract fun getReader(artificialColumns: Map<String, Any>): TableReader


    /**
     * Adds a (abstract) column to the table with each row of the column having value [value].
     * Useful to merge multiple tables whose origin has to be distinguished (an artificial id).
     * @param[columnName]   the new column name.
     * @param[value]        the value of each row of the new column.
     *
     * @return              the table with the new column if the addition was successful, null otherwise.
     *                      The addition can fail in case a column with the same name is already present.
     */
    public fun <T: Any> withArtificialColumn(columnName: String, value: T): Table? {
        if (columnName in getReader().colNames)
            return log.errAndNull("unable to add abstract column '$columnName', a column with the same name is already present")

        artificialCols[columnName] = value

        return this
    }





    public companion object {
        private val log by logger()

        /**
         * See overloaded method below.
         */
        public fun withNameFromFile(name: String, path: Path): PhysicalTable? =
            withNameFromFile(name = name, file = path.toFile())

        /**
         * See overloaded method below.
         */
        public fun withNameFromFile(name: String, path: String): PhysicalTable? =
            withNameFromFile(name = name, file = File(path))

        /**
         * @param[name]     name of the resulting table.
         * @param[file]     file with the table.
         * @return  a physical table (table taken directly from file) if [file] is a supported format, null otherwise.
         */
        public fun withNameFromFile(name: String, file: File): PhysicalTable? =
            // check if a table reader can be instantiated for the file
            TableReader.genericReaderFor(file)?.let {
                PhysicalTable(name = name, file = file)
            }
    }
}
