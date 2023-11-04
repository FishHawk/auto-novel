package sakura

import infra.web.providers.json
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import util.UnreachableException

suspend fun sakuraTranslate(
    client: HttpClient,
    endpoint: String,
    input: List<String>,
    onDegradation: suspend (prompt: String, result: String) -> Unit,
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
        .flatMap { seg ->
            sakuraTranslateSeg(
                client = client,
                endpoint = endpoint,
                seg = seg,
                onDegradation = onDegradation,
            ).apply { println(this) }
        }
    val recoveredOutput = recoverOutput(input, output)

    if (recoveredOutput.size != input.size) {
        throw UnreachableException()
    }

    return recoveredOutput
}

private fun JsonObjectBuilder.simpleParam() {
    put("seed", -1)
    put("do_sample", true)
    put("temperature", 0.1)
    put("top_p", 0.3)
    put("top_k", 40)
    put("num_beams", 1)
    put("repetition_penalty", 1.0)
}

private fun JsonObjectBuilder.freeParam() {
    put("seed", -1)
    put("do_sample", true)
    put("temperature", 0.1)
    put("top_p", 0.3)
    put("top_k", 40)
    put("num_beams", 1)
    put("repetition_penalty", 1.0)

    put("frequency_penalty", 0.2)
}

private fun makePrompt(textToTranslate: String) =
    "<reserved_106>将下面的日文文本翻译成中文：${textToTranslate}<reserved_107>";

suspend fun sakuraTranslateSeg(
    client: HttpClient,
    endpoint: String,
    seg: List<String>,
    onDegradation: suspend (prompt: String, result: String) -> Unit,
): List<String> {

    var retry = 0
    val maxNewTokens = 1024

    while (retry < 2) {
        val prompt = makePrompt(seg.joinToString("\n"))
        val (text, hasDegradation) = sakuraTranslatePrompt(
            client = client,
            endpoint = endpoint,
            prompt = prompt,
            maxNewTokens = maxNewTokens,
        ) {
            if (retry == 0) {
                simpleParam()
            } else {
                freeParam()
            }
        }

        val splitText = text.split("\n")

        if (!hasDegradation && splitText.size == seg.size) {
            return splitText
        } else {
            if (retry == 1) {
                onDegradation(prompt, text)
            }
            retry += 1
        }
    }

    // 进入逐句翻译模式
    val perLine = seg.map {
        val prompt = makePrompt(it)
        val (text, hasDegradation) = sakuraTranslatePrompt(
            client = client,
            endpoint = endpoint,
            prompt = prompt,
            maxNewTokens = maxNewTokens,
        ) {
            freeParam()
        }
        if (hasDegradation) it else text
    }
    return perLine
}


suspend fun sakuraTranslatePrompt(
    client: HttpClient,
    endpoint: String,
    prompt: String,
    maxNewTokens: Int = 1024,
    params: JsonObjectBuilder.() -> Unit
): Pair<String, Boolean> {
    val obj = client.post(endpoint) {
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("prompt", prompt)
                put("preset", "None")
                put("max_new_tokens", maxNewTokens)
                params()
            }
        )
    }.json()

    val (text, newToken) = obj["results"]!!
        .jsonArray[0]
        .jsonObject.let { result ->
            val text = result["text"]!!.jsonPrimitive.content
            val newToken = result["new_token"]!!.jsonPrimitive.int
            text to newToken
        }

    val hasDegradation = newToken >= maxNewTokens
    return text to hasDegradation
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
