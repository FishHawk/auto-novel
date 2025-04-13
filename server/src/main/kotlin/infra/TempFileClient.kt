package infra

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

enum class TempFileType(val value: String) {
    Web("web"),
    Wenku("wenku"),
}

class TempFileClient {
    private val path = Path("./data/files-temp")

    init {
        for (dirName in listOf(
            TempFileType.Web.value,
            TempFileType.Wenku.value,
            "trash",
        )) {
            val dirPath = path / dirName
            if (dirPath.notExists()) {
                dirPath.createDirectories()
            }
        }
    }

    fun isFileModifiedAfter(type: TempFileType, filename: String, instant: Instant): Boolean {
        val filepath = path / type.value / filename

        if (!filepath.exists()) {
            return false
        }

        val lastModifier = filepath.readAttributes<BasicFileAttributes>()
            .creationTime()
            .toInstant()
            .toKotlinInstant()
        return lastModifier > instant
    }

    fun createFile(type: TempFileType, filename: String): Path {
        val filepath = path / type.value / filename
        if (!filepath.exists()) {
            filepath.createFile()
        }
        return filepath
    }

    fun trash(filepath: Path) {
        val trashFilepath = path / "trash" / filepath.fileName
        trashFilepath.deleteIfExists()
        filepath.moveTo(trashFilepath)
    }
}