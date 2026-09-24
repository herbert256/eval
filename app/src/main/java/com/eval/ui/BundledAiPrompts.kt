package com.eval.ui

import android.content.res.AssetManager
import com.google.gson.JsonParser

/** JSON files in the repository's assets directory supply both bundled prompt catalogs. */
internal object BundledAiPrompts {
    // Fingerprints of shipped long versions: update only untouched built-ins, never custom text.
    val previousSystemPromptTextHashes: Map<String, Set<String>> = mapOf(
        "bundled-system-prompt:professional-coach" to setOf("0d83fab40f0cc0186065bfb4a7455abe432229aeed441438c27dccbd77b44d74"),
        "bundled-system-prompt:friendly-talkative-chess-coach" to setOf("327f2a93e70b0dab059b5aea9c7cea3aca060e4dadae33abd8b6fcbd0f420e2a"),
        "bundled-system-prompt:grumpy-old-gm" to setOf("26d421e9a7f0412ad42465872f0bc9ee43327232fc80c67a57b2bae99516c596"),
        "bundled-system-prompt:youtuber" to setOf("1a8976a51021a8601cd23eb3f86358b5ebf332687b1aa53f6d00245f4a859c15"),
    )

    val previousReportPromptTextHashes: Map<String, Set<String>> = mapOf(
        "bundled-prompt:analyse-a-fen-position" to setOf("321dae8845176358fb2daeaf13fc6fb5e060e0052425e6ac75e59e8257bae8c3"),
        "bundled-prompt:annotate-a-chess-game" to setOf("c0bec2c8829ea65146d813a3883d707958b63948a5b84a60ed9d5d4bb08ba5d3"),
        "bundled-prompt:create-a-training-plan" to setOf("1063a307738f916bd08b793c50ab5a6addece3aa216e755278c829f97c46abba"),
        "bundled-prompt:explain-the-engine-choices" to setOf("4671993347ccd5e777cbad4934d50aac3dd16432eef78060343acf3e8d53e75b"),
        "bundled-prompt:find-tactical-opportunities" to setOf("ecd5c9b065553f54bde11dadda0b2e6e54f636c37e660cebbb5a1040ccfedc4b"),
        "bundled-prompt:make-a-strategic-plan" to setOf("7a36c105a16152021d2166197d11ccd13731a433d134ddf7633a373b63f3c403"),
        "bundled-prompt:review-mistakes-and-turning-points" to setOf("212fa6651f798ef19f6bdcae43d48fcb6ccf449cfe3b14610d19977d054ed385"),
    )

    fun loadSystemPrompts(assets: AssetManager): List<AiPromptEntry> =
        load(assets, "system_prompts", "bundled-system-prompt")

    fun loadReportPrompts(assets: AssetManager): List<AiPromptEntry> =
        load(assets, "prompts", "bundled-prompt")

    private fun load(assets: AssetManager, directory: String, idPrefix: String): List<AiPromptEntry> =
        assets.list(directory).orEmpty()
        .filter { it.endsWith(".json") }.sorted().map { file ->
            val json = assets.open("$directory/$file").bufferedReader().use { it.readText() }
            val entry = JsonParser().parse(json).asJsonObject
            require(entry.keySet() == setOf("title", "text")) { "$file must contain only title and text" }
            fun text(key: String): String {
                val value = entry.get(key)
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
                    "$file needs a non-empty $key"
                }
                return value.asString
            }
            AiPromptEntry(
                id = "$idPrefix:${file.removeSuffix(".json")}",
                name = text("title"), text = text("text")
            )
        }
}
