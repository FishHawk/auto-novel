package sakura

import infra.web.providers.json
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import util.UnreachableException

class SakuraDegenerateException : Exception("Sakura翻译结果退化")
class SakuraNetworkException : Exception("Sakura网络请求错误")

suspend fun sakuraTranslate(
    client: HttpClient,
    endpoint: String,
    input: List<String>,
    onSakuraFail: suspend (prompt: String, result: String) -> Unit,
): List<String> {
    fun splitByLength(paragraphs: List<String>, limit: Int): List<List<String>> {
        val segs = mutableListOf<List<String>>()
        var seg = mutableListOf<String>()
        var total = 0
        paragraphs.forEach {
            if (total + it.length > limit) {
                segs.add(seg)
                seg = mutableListOf(it)
                total = it.length
            } else {
                seg.add(it)
                total += it.length
            }
        }
        if (seg.isNotEmpty()) {
            segs.add(seg)
        }
        return segs
    }

    val filteredInput = filterInput(input)
    val output = splitByLength(filteredInput, 500)
        .flatMap { seg -> translateSeg(client, endpoint, seg, onSakuraFail) }
    val recoveredOutput = recoverOutput(input, output)

    if (recoveredOutput.size != input.size) {
        throw UnreachableException()
    }

    return recoveredOutput
}

private suspend fun translateSeg(
    client: HttpClient,
    endpoint: String,
    seg: List<String>,
    onSakuraFail: suspend (prompt: String, result: String) -> Unit,
): List<String> {
    fun JsonObjectBuilder.simpleParam() {
        put("do_sample", true)
        put("temperature", 0.1)
        put("top_p", 0.3)
        put("top_k", 40)
        put("num_beams", 1)
        put("repetition_penalty", 1.0)
    }

    fun JsonObjectBuilder.contrastiveSearchParam() {
        put("do_sample", false)
        put("top_k", 4)
        put("penalty_alpha", 0.3)
    }

    var retry = 0

    val prompt = "<reserved_106>将下面的日文文本翻译成中文：${seg.joinToString("\n")}<reserved_107>";

    while (retry < 2) {
        val obj = runCatching {
            client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("prompt", prompt)
                        put("preset", "None")
                        put("max_new_tokens", 1024)
                        if (retry == 0) {
                            simpleParam()
                        } else {
                            contrastiveSearchParam()
                        }
                    }
                )
            }.json()
        }.getOrElse {
            throw SakuraNetworkException()
        }
        val result = obj["results"]!!
            .jsonArray[0]
            .jsonObject["text"]!!
            .jsonPrimitive
            .content
        val splitResult = result.split("\n")

        if (splitResult.size == seg.size) {
            return splitResult
        } else {
            if (retry == 0) {
                // 第一次失败时记录数据
                onSakuraFail(prompt, result)
            }
            retry += 1
        }
    }
    throw SakuraDegenerateException()
}

private fun filterInput(input: List<String>): List<String> {
    return input.map {
        it
            .replace("/\r?\n|\r/g".toRegex(), "")
            .replace("　", " ")
            .trim()
    }.filterNot {
        it.isBlank() || it.startsWith("<图片>")
    }
}

private fun recoverOutput(input: List<String>, output: List<String>): List<String> {
    val mutableOutput = output.toMutableList()
    val recoveredOutput = mutableListOf<String>()
    input.forEach {
        val realLine = it
            .replace("/\r?\n|\r/g".toRegex(), "")
            .replace("　", " ")
            .trim()
        if (realLine.isBlank() || realLine.startsWith("<图片>")) {
            recoveredOutput.add(it);
        } else {
            recoveredOutput.add(
                mutableOutput.removeFirst()
            )
        }
    }
    return recoveredOutput;
}
