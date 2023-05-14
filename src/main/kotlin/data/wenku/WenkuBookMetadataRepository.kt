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

class WenkuBookMetadataRepository(
    private val mongo: MongoDataSource,
) {
    private val col
        get() = mongo.database.getCollection<WenkuMetadata>("wenku-metadata")

    companion object {
        private fun byId(id: String): Bson = WenkuMetadata::id eq ObjectId(id)
    }

    suspend fun exist(bookId: String): Boolean {
        return col.countDocuments(byId(bookId), CountOptions().limit(1)) != 0L
    }

    @Serializable
    data class WenkuMetadata(
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

    suspend fun findOne(id: String): WenkuMetadata? {
        return col.findOne(byId(id))
    }

    suspend fun findOneAndIncreaseVisited(id: String): WenkuMetadata? {
        return col.findOneAndUpdate(
            byId(id),
            inc(WenkuMetadata::visited, 1),
            FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        )
    }

    suspend fun findOneAndUpdate(
        id: String,
        title: String,
        titleZh: String,
        titleZhAlias: List<String>,
        cover: String,
        coverSmall: String,
        authors: List<String>,
        artists: List<String>,
        keywords: List<String>,
        introduction: String,
    ): WenkuMetadata? {
        return col.findOneAndUpdate(
            byId(id),
            combine(
                listOf(
                    setValue(WenkuMetadata::title, title),
                    setValue(WenkuMetadata::titleZh, titleZh),
                    setValue(WenkuMetadata::titleZhAlias, titleZhAlias),
                    setValue(WenkuMetadata::cover, cover),
                    setValue(WenkuMetadata::coverSmall, coverSmall),
                    setValue(WenkuMetadata::authors, authors),
                    setValue(WenkuMetadata::artists, artists),
                    setValue(WenkuMetadata::keywords, keywords),
                    setValue(WenkuMetadata::introduction, introduction),
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
            WenkuMetadata(
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