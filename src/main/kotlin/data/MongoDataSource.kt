package data

import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

class MongoDataSource {
    val client = KMongo.createClient(
        System.getenv("MONGODB_URL") ?: "mongodb://localhost:27017"
    ).coroutine
    val database = client.getDatabase("main")
}