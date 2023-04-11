package data.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

fun dummyTocItem(s: String): BookTocItem {
    return BookTocItem(titleJp = "ep-$s", titleZh = null, episodeId = "id-$s")
}

fun dummyLocalToc(): List<BookTocItem> {
    return (1..10).map { dummyTocItem(it.toString()) }
}

fun dummyRemoteTocWithAdd(localToc: List<BookTocItem>): List<BookTocItem> {
    return localToc.toMutableList().apply { add(dummyTocItem("new")) }
}

fun dummyRemoteTocWithDelete(localToc: List<BookTocItem>): List<BookTocItem> {
    return localToc.toMutableList().apply { removeAt(localToc.size / 2) }
}

class MergeTocKtTest : DescribeSpec({
    describe("mergeTocUnstable") {
        it("Unchanged") {
            val localToc = dummyLocalToc()
            val merged = mergeToc(localToc, localToc, false)
            merged.hasChanged.shouldBeFalse()
            merged.reviewReason.shouldBeNull()
        }

        it("Add") {
            val localToc = dummyLocalToc()
            val remoteToc = dummyRemoteTocWithAdd(localToc)
            val merged = mergeToc(remoteToc, localToc, false)
            merged.hasChanged.shouldBeTrue()
            merged.reviewReason.shouldBeNull()
        }

        it("Deleted") {
            val localToc = dummyLocalToc()
            val remoteToc = dummyRemoteTocWithDelete(localToc)
            val merged = mergeToc(remoteToc, localToc, false)
            merged.hasChanged.shouldBeTrue()
            merged.reviewReason.shouldNotBeNull()
        }

    }

    describe("mergeTocStable") {
        it("Unchanged") {
            val localToc = dummyLocalToc()
            val merged = mergeToc(localToc, localToc, true)
            merged.hasChanged.shouldBeFalse()
            merged.reviewReason.shouldBeNull()
        }

        it("Add") {
            val localToc = dummyLocalToc()
            val remoteToc = dummyRemoteTocWithAdd(localToc)
            val merged = mergeToc(remoteToc, localToc, true)
            merged.hasChanged.shouldBeTrue()
            merged.reviewReason.shouldBeNull()
        }

        it("Deleted") {
            val localToc = dummyLocalToc()
            val remoteToc = dummyRemoteTocWithDelete(localToc)
            val merged = mergeToc(remoteToc, localToc, true)
            merged.hasChanged.shouldBeTrue()
            merged.reviewReason.shouldNotBeNull()
        }

        it("TitleChanged") {
            val localToc = dummyLocalToc()
            val remoteToc = localToc.toMutableList().apply { set(1, dummyTocItem("changed")) }
            val merged = mergeToc(remoteToc, localToc, true)
            merged.toc[1].titleJp.shouldBe("ep-changed")
            merged.hasChanged.shouldBeFalse()
            merged.reviewReason.shouldNotBeNull()
        }
    }
})
