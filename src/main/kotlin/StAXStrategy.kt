import org.codehaus.stax2.XMLEventReader2
import org.codehaus.stax2.XMLInputFactory2
import java.io.File
import java.util.*
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.events.StartElement

object  StAXStrategy : Strategy {
    override fun getData(path: File): FictionBook? {
        return try {
            var title: String? = null
            val genres = arrayListOf<String>()
            var sequence: BookSequence? = null
            val authors = arrayListOf<Author>()
            val factory = XMLInputFactory2.newInstance() as XMLInputFactory2
            factory.configureForConvenience()
            val reader = factory.createXMLEventReader(path)
            while (reader.hasNext()) {
                val event = reader.nextEvent()
                when (event.eventType) {
                    START_ELEMENT -> {
                        val elem = event.asStartElement()
                        when (elem.name.localPart) {
                            "book-title" -> title = extractTitle(reader)
                            "sequence" -> sequence = parseSequence(elem)
                            "genre" -> genres.add(reader.nextEvent().asCharacters().data)
                            "author" -> {
                                val parseAuthor = parseAuthor(reader)
                                if (parseAuthor != null) authors.add(parseAuthor)
                            }
                        }
                    }
                    END_ELEMENT -> {
                        val element = event.asEndElement()
                        if (element.name.localPart == "title-info") break
                    }
                }
            }
            if (authors.isEmpty() || title == null || genres.isEmpty()) null
            else FictionBook(authors, genres, title, sequence)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseAuthor(reader: XMLEventReader2): Author? {
        var event = reader.nextEvent()
        val names = ArrayDeque<String?>()
        while (!(event.isEndElement && event.asEndElement().name.localPart == "author")) {
            if (event.isStartElement) {
                if (event.asStartElement().name.localPart == "last-name")
                    try {
                        names.addFirst(reader.nextEvent().asCharacters().data)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                else
                    try {
                        names.addLast(reader.nextEvent().asCharacters().data)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            }
            event = reader.nextEvent()
        }
        return names.toList().filterNot { it.isNullOrBlank() }.filterNotNull().takeIf { it.isNotEmpty() }
            ?.let { Author(it) }
    }

    private fun parseSequence(elem: StartElement): BookSequence? {
        val attrs = elem.attributes.asSequence().map { it.name.localPart to it.value }.toMap()
        if (attrs["name"] != null) {
            return BookSequence(attrs["name"]!!, attrs["number"]?.toIntOrNull())
        }
        return null
    }

    private fun extractTitle(reader: XMLEventReader2) =
        reader.nextEvent().asCharacters().data.takeIf { it.isNotBlank() }
}