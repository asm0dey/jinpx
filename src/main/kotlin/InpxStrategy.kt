import net.lingala.zip4j.ZipFile
import java.io.File

object InpxStrategy : Strategy {

    private lateinit var data: MutableMap<Int, FictionBook>
    val size get() = data.size
    fun init(inpxPath: String) {
        val header = "AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;KEYWORDS".split(';')
            .mapIndexed { index, s -> s to index }.toMap()
        val zipFile = ZipFile(inpxPath)
        data = zipFile
            .fileHeaders
            .asSequence()
            .filter { it.fileName.endsWith(".inp") }
            .flatMap {
                zipFile.getInputStream(it).buffered().use { input ->
                    input.reader().useLines { seq -> seq.toList() }
                }
            }
            .map { it.split(0x04.toChar()) }
            .filterNot { it[header["TITLE"]!!].isBlank() }
            .filterNot { breakColonDelimied(it[header["AUTHOR"]!!]).all { it.isBlank() } }
            .filterNot { breakColonDelimied(it[header["AUTHOR"]!!]).flatMap { breakCommaDelimied(it) }.all { it.isBlank() } }
            .distinctBy { it[header["FILE"]!!].toInt() }
            .groupBy { it[header["FILE"]!!].toInt() }
            .mapValues { it.value.single() }
            .mapValues {
                val authorString = it.value[header["AUTHOR"]!!]
                val authors = breakColonDelimied(authorString).map { Author(breakCommaDelimied(it)) }
                val series = it.value[header["SERIES"]!!]
                val bookSequence = if (series.isNotBlank()) {
                    BookSequence(series, it.value[header["SERNO"]!!].toIntOrNull())
                } else null
                val genreString = it.value[header["GENRE"]!!]
                val genres = breakColonDelimied(genreString)
                val title = it.value[header["TITLE"]!!]
                FictionBook(authors, genres, title, bookSequence)
            }
            .toMutableMap()

    }

    private fun breakColonDelimied(str: String) = str.split(':').filter { it.isNotBlank() }.map { it.trim() }
    private fun breakCommaDelimied(str: String) = str.split(',').filter { it.isNotBlank() }.map { it.trim() }
    override fun getData(path: File): FictionBook? =
        path
            .takeIf { it.isFile }
            ?.name
            ?.removeSuffix(".fb2")
            ?.toIntOrNull()
            ?.let { data[it] }
}

