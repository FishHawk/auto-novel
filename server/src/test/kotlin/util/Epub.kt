package util

import io.kotest.core.spec.style.DescribeSpec
import util.epub.EpubBook

class EpubTest : DescribeSpec({
    it("package") {
        val epub = EpubBook()
        val identifier = "id"
        epub.addIdentifier(identifier, true)

        epub.addTitle("title")
        epub.addLanguage("ja")
        epub.addDescription("balabala")
        val doc = EpubBook.Writer(epub).createPackageDocument()
        println(doc)
    }
})