package infra.user

import infra.DataSourceFileSystem
import infra.model.WenkuNovelVolumeJp
import java.io.InputStream
import kotlin.io.path.Path

class UserPersonalVolumeRepository(
    private val fs: DataSourceFileSystem,
) {
    private fun volumesDir(userId: String) =
        Path("./data/files-wenku/user-${userId}")

    suspend fun list(novelId: String): List<WenkuNovelVolumeJp> =
        fs.listVolumes(volumesDir(novelId)).jp

    suspend fun createVolume(
        userId: String,
        volumeId: String,
        inputStream: InputStream,
    ) = fs.createVolume(
        volumesDir = volumesDir(userId),
        volumeId = volumeId,
        inputStream = inputStream,
        unpack = true,
    )

    suspend fun deleteVolume(
        userId: String,
        volumeId: String,
    ) = fs.deleteVolume(
        volumesDir = volumesDir(userId),
        volumeId = volumeId,
    )

    suspend fun getVolume(
        userId: String,
        volumeId: String,
    ) = fs.getVolume(
        volumesDir = volumesDir(userId),
        volumeId = volumeId,
    )
}
