import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBar
import java.io.File
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div


object Main : CliktCommand() {
    val skip by option("-s", "--skip", help = "whether skip already existing files")
        .flag("-S", "--do-not-skip", default = true)
    val inpx by option().file(canBeDir = false, mustBeReadable = true, canBeSymlink = false).required()
    val searchDir by option().file(canBeFile = false, canBeDir = true, mustBeReadable = true).required()
    val dest by option(help = "Directory where subdirs will be created").file(
        canBeFile = false,
        canBeDir = true,
        mustBeReadable = true,
        mustBeWritable = true
    )
        .required()

    @ExperimentalPathApi
    override fun run() {
        InpxStrategy.init(inpx.absolutePath)
        ProgressBar("Building links", 0).apply { maxHint(InpxStrategy.size.toLong()) }.use {
            for (target in searchDir.walkTopDown().filter { it.isFile }) {
                val info = InpxStrategy.getData(target) ?: StAXStrategy.getData(target)
                if (info != null) {
                    createLinks(info, target, dest, skip)
                }
                it.step()
            }
        }
    }
}

fun main(args: Array<String>) = Main.main(args)

@ExperimentalPathApi
fun createLinks(fb2: FictionBook, target: File, destBase: File, skip: Boolean) {
    authorLinks(fb2, destBase.toPath(), target, skip)
    sequenceLinks(fb2, destBase.toPath(), target, skip)
    nameLinks(fb2, destBase.toPath(), target, skip)
}

@ExperimentalPathApi
fun nameLinks(fb2: FictionBook, toPath: Path, target: File, skip: Boolean) {
    val nameLink = toPath
        .resolve("by-name")
        .subPaths(punctRegex.replace(fb2.title, ""))
    val fileLink = nameLink.resolve(fb2.fullFileName).determineSuitableName(nameLink, skip, fb2.fullFileName)
    val source = target.toPath()
    createRelativeLink(fileLink, source, skip)
}

val punctRegex = Regex("^[\\p{Punct}\\s«»]+")

@ExperimentalPathApi
private fun authorLinks(fb2: FictionBook, baseLink: Path, file: File, skip: Boolean) {
    for (author in fb2.authors) {
        val full = author.fullName.replace(punctRegex, "")
        val last = author.lastName.replace(punctRegex, "")
        val authorLink = baseLink
            .resolve("by-author")
            .subPaths(if (last.isBlank()) full else last)
            .resolve(author.fullName)
        val fileLink = authorLink.resolve(fb2.fileName).determineSuitableName(authorLink, skip, fb2.fileName)
        val source = file.toPath()
        createRelativeLink(fileLink, source, skip)
        if (fb2.bookSequence?.name != null) {
            val seqName = fb2.bookSequence.name
            val seqParentLink = authorLink
                .resolve("by-sequence")
                .resolve(seqName)
            val seqFileLink = seqParentLink
                .resolve(fb2.fileName)
                .determineSuitableName(seqParentLink, skip, fb2.fileName)
            createRelativeLink(seqFileLink, source, skip)
        }
    }
}

/**
 * @receiver Path with default name
 * @param parent where link should leave
 * @param fb2 info about book
 * @param skip if we don't care about potentially existing paths we should set this to true
 *
 * @return Path which does not exist in fs or receiver is `skip` is true
 */
private fun Path.determineSuitableName(parent: Path, skip: Boolean, name: String): Path {
    if (skip) return this
    var result = this
    var counter = 2
    while (Files.exists(result)) {
        result = parent.resolve(name.replace(".fb2", "_$counter.fb2"))
        counter++
    }
    return result
}

@ExperimentalPathApi
private fun sequenceLinks(fb2: FictionBook, baseLink: Path, file: File, skip: Boolean) {
    if (fb2.bookSequence?.name != null) {
        val sequenceName = fb2.bookSequence.name.replace(Regex("^[\\p{Punct}\\s«»]+"), "")
        val sequenceLink = (baseLink / "by-sequence").subPaths(sequenceName) / fb2.bookSequence.name
        val fileLink = (sequenceLink / fb2.fileName).determineSuitableName(sequenceLink, skip, fb2.fileName)
        val source = file.toPath()
        createRelativeLink(fileLink, source, skip)
        for (author in fb2.authors) {
            val authParentLink = sequenceLink.resolve("by-author").resolve(author.fullName)
            val authFileLink = authParentLink.resolve(fb2.fileName).determineSuitableName(
                authParentLink,
                skip,
                fb2.fileName
            )
            createRelativeLink(authFileLink, source, skip)
        }
    }
}
/**
 * @param link file which will be link to target
 * @param target target of the link
 * @param skip if error should be thrown if file already exists
 */
private fun createRelativeLink(link: Path, target: Path, skip: Boolean) {
    val exists = Files.exists(link)
    if (!exists || (exists && !skip)) {
        link.parent.toFile().mkdirs()
        try {
            Files.createSymbolicLink(link, link.parent.toAbsolutePath().relativize(target.toAbsolutePath()))
        } catch (e: FileSystemException) {
        }
    }
}

@ExperimentalPathApi
private fun Path.subPaths(name: String): Path {
    var base = this
    for (subLength in 1..4) {
        if (name.length < subLength) break
        val subName = name
            .substring(0 until subLength)
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        base = base / subName
    }
    return base
}
