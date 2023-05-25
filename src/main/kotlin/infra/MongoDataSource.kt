package infra

import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

class MongoDataSource(url: String) {
    val client = KMongo.createClient(url).coroutine
    val database = client.getDatabase("main")
}