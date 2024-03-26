package infra.wenku

import infra.DataSourceFileSystem
import domain.entity.WenkuNovelVolumeList
import java.io.InputStream
import kotlin.io.path.Path

class WenkuNovelVolumeRepository(
    private val fs: DataSourceFileSystem,
) {
    private fun volumesDir(novelId: String) =
        Path("./data/files-wenku/${novelId}")

    suspend fun list(novelId: String): WenkuNovelVolumeList =
        fs.listVolumes(volumesDir(novelId))

    suspend fun createVolume(
        novelId: String,
        volumeId: String,
        inputStream: InputStream,
        unpack: Boolean,
    ) = fs.createVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
        inputStream = inputStream,
        unpack = unpack,
    )

    suspend fun deleteVolume(
        novelId: String,
        volumeId: String,
    ) = fs.deleteVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
    )

    suspend fun getVolume(
        novelId: String,
        volumeId: String,
    ) = fs.getVolume(
        volumesDir = volumesDir(novelId),
        volumeId = volumeId,
    )
}
