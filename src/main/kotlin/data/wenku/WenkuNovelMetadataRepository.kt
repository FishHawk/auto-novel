package data.wenku

import com.mongodb.client.model.CountOptions
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import data.MongoDataSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.litote.kmongo.combine
import org.litote.kmongo.eq
import org.litote.kmongo.inc
import org.litote.kmongo.setValue

class WenkuNovelMetadataRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<NovelMetadata>("wenku-metadata")

    companion object {
        private fun byId(id: String): Bson = NovelMetadata::id eq ObjectId(id)
    }

    suspend fun exist(novelId: String): Boolean {
        return col.countDocuments(byId(novelId), CountOptions().limit(1)) != 0L
    }

    @Serializable
    data class NovelMetadata(
        @Contextual @SerialName("_id") val id: ObjectId,
        val title: String,
        val titleZh: String,
        val titleZhAlias: List<String>,
        val cover: String,
        val coverSmall: String,
        val authors: List<String>,
        val artists: List<String>,
        val keywords: List<String>,
        val introduction: String,
        val visited: Long,
    )

    suspend fun findOne(novelId: String): NovelMetadata? {
        return col.findOne(byId(novelId))
    }

    suspend fun findOneAndIncreaseVisited(novelId: String): NovelMetadata? {
        return col.findOneAndUpdate(
            byId(novelId),
            inc(NovelMetadata::visited, 1),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
    }

    suspend fun findOneAndUpdate(
        novelId: String,
        title: String,
        titleZh: String,
        titleZhAlias: List<String>,
        cover: String,
        coverSmall: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
        introduction: String,
    ): NovelMetadata? {
        return col.findOneAndUpdate(
            byId(novelId),
            combine(
                listOf(
                    setValue(NovelMetadata::title, title),
                    setValue(NovelMetadata::titleZh, titleZh),
                    setValue(NovelMetadata::titleZhAlias, titleZhAlias),
                    setValue(NovelMetadata::cover, cover),
                    setValue(NovelMetadata::coverSmall, coverSmall),
                    setValue(NovelMetadata::authors, authors),
                    setValue(NovelMetadata::artists, artists),
                    setValue(NovelMetadata::keywords, keywords),
                    setValue(NovelMetadata::introduction, introduction),
                )
            ),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
    }

    suspend fun insertOne(
        title: String,
        titleZh: String,
        titleZhAlias: List<String>,
        cover: String,
        coverSmall: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
        introduction: String,
    ): String {
        val insertResult = col.insertOne(
            NovelMetadata(
                id = ObjectId(),
                title = title,
                titleZh = titleZh,
                titleZhAlias = titleZhAlias,
                cover = cover,
                coverSmall = coverSmall,
                authors = authors,
                artists = artists,
                keywords = keywords,
                introduction = introduction,
                visited = 0,
            )
        )
        return insertResult.insertedId!!.asObjectId().value.toHexString()
    }
}