package com.eval.ui

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** Validate external settings before Gson coercion or any preference writes. */
internal object SettingsImportValidation {
    private data class NumericRule(val path: String, val key: String, val min: Double, val max: Double)
    private val numericRules = listOf(
        NumericRule("stockfishSettings.previewStage.secondsForMove", "preview_seconds", 0.01, 0.5),
        NumericRule("stockfishSettings.previewStage.threads", "preview_threads", 1.0, 4.0),
        NumericRule("stockfishSettings.previewStage.hashMb", "preview_hash", 8.0, 64.0),
        NumericRule("stockfishSettings.analyseStage.secondsForMove", "analyse_seconds", 0.5, 10.0),
        NumericRule("stockfishSettings.analyseStage.threads", "analyse_threads", 1.0, 8.0),
        NumericRule("stockfishSettings.analyseStage.hashMb", "analyse_hash", 16.0, 256.0),
        NumericRule("stockfishSettings.manualStage.depth", "manual_depth", 16.0, 64.0),
        NumericRule("stockfishSettings.manualStage.threads", "manual_threads", 1.0, 16.0),
        NumericRule("stockfishSettings.manualStage.hashMb", "manual_hash", 32.0, 512.0),
        NumericRule("stockfishSettings.manualStage.multiPv", "manual_multipv", 1.0, 32.0),
        NumericRule("stockfishSettings.manualStage.numArrows", "manual_numarrows", 1.0, 8.0),
        NumericRule("boardLayoutSettings.evalBarRange", "eval_bar_range", 1.0, 10.0),
        NumericRule("graphSettings.lineGraphRange", "graph_line_range", 1.0, 10.0),
        NumericRule("graphSettings.barGraphRange", "graph_bar_range", 1.0, 10.0),
        NumericRule("graphSettings.lineGraphScale", "graph_line_scale", 50.0, 300.0),
        NumericRule("graphSettings.barGraphScale", "graph_bar_scale", 50.0, 300.0),
        NumericRule("lichessMaxGames", "lichess_max_games", 1.0, 25.0)
    )
    private val shape = Gson().toJsonTree(SettingsSnapshotV3()).asJsonObject

    fun typed(root: JsonObject) {
        validateShape(root, shape)
        for (key in listOf("lastServerUser", "lastServerName")) {
            root.get(key)?.takeUnless { it.isJsonNull }?.let { requireString(it) }
        }
        for (key in listOf("aiInstructions", "aiPrompts")) {
            root.get(key)?.let { entries ->
                require(entries.isJsonArray)
                entries.asJsonArray.forEach { entry ->
                    require(entry.isJsonObject)
                    for (field in listOf("id", "name", "instructions", "email")) {
                        entry.asJsonObject.get(field)?.let { requireString(it) }
                    }
                }
            }
        }
        root.get("fenHistory")?.asJsonArray?.forEach { requireString(it) }
        for (rule in numericRules) {
            var value: JsonElement? = root
            for (field in rule.path.split('.')) value = value?.asJsonObject?.get(field)
            value?.let { require(it.asDouble in rule.min..rule.max) }
        }
    }

    private fun validateShape(value: JsonElement, default: JsonElement) {
        when {
            default.isJsonObject -> {
                require(value.isJsonObject)
                for ((key, expected) in default.asJsonObject.entrySet()) {
                    value.asJsonObject.get(key)?.let { validateShape(it, expected) }
                }
            }
            default.isJsonArray -> require(value.isJsonArray)
            else -> {
                require(value.isJsonPrimitive)
                val expected = default.asJsonPrimitive
                val actual = value.asJsonPrimitive
                when {
                    expected.isBoolean -> require(actual.isBoolean)
                    expected.isString -> require(actual.isString)
                    expected.isNumber -> {
                        require(actual.isNumber && actual.asDouble.isFinite())
                        if (expected.asNumber is Float || expected.asNumber is Double) {
                            require(actual.asFloat.isFinite())
                        } else {
                            val number = actual.asBigDecimal.longValueExact()
                            if (expected.asNumber !is Long) {
                                require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                            }
                        }
                    }
                }
            }
        }
    }

    fun legacy(key: String, type: String, value: JsonElement) {
        legacyTypes[key]?.let { require(it == type) }
        when (type) {
            "String" -> requireString(value)
            "Boolean" -> require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
            "Int", "Long", "Float" -> {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
                if (type == "Float") require(value.asFloat.isFinite())
                else {
                    val number = value.asBigDecimal.longValueExact()
                    if (type == "Int") require(number in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                }
                numericRules.firstOrNull { it.key == key }?.let { require(value.asDouble in it.min..it.max) }
            }
            "StringSet" -> {
                require(value.isJsonArray)
                value.asJsonArray.forEach { requireString(it) }
            }
            else -> error("Unsupported preference type")
        }
        if (key == "fen_history") {
            val history = com.google.gson.JsonParser().parse(value.asString)
            require(history.isJsonArray)
            history.asJsonArray.forEach { requireString(it) }
        }
        if (key == "ai_instructions_list" || key == "ai_prompts_list") {
            typed(JsonObject().apply {
                add(if (key == "ai_prompts_list") "aiPrompts" else "aiInstructions",
                    com.google.gson.JsonParser().parse(value.asString))
            })
        }
    }

    private fun requireString(value: JsonElement) = require(value.isJsonPrimitive && value.asJsonPrimitive.isString)

    private val legacyTypes = buildMap {
        fun fields(type: String, vararg keys: String) = keys.forEach { put(it, type) }
        fields("String", "ai_instructions_list", "ai_prompts_list", "chesscom_username", "fen_history",
            "last_server_name", "last_server_user", "lichess_username", "manual_arrow_mode")
        fields("Boolean", "ai_app_dont_ask_again", "analyse_nnue", "analyse_vis_board", "analyse_vis_gameinfo",
            "analyse_vis_movelist", "analyse_vis_pgn", "analyse_vis_resultbar", "analyse_vis_scorebarsgraph",
            "analyse_vis_scorelinegraph", "analyse_vis_stockfishanalyse", "board_red_border_player_to_move",
            "board_show_coordinates", "board_show_last_move", "manual_nnue", "manual_shownumbers",
            "manual_vis_gameinfo", "manual_vis_movelist", "manual_vis_openingexplorer", "manual_vis_openingname",
            "manual_vis_pgn", "manual_vis_rawstockfishscore", "manual_vis_resultbar", "manual_vis_scorebarsgraph",
            "manual_vis_scorelinegraph", "manual_vis_timegraph", "move_sounds_enabled", "preview_nnue",
            "preview_vis_board", "preview_vis_movelist", "preview_vis_pgn", "preview_vis_resultbar", "preview_vis_scorebarsgraph")
        fields("Int", "analyse_hash", "analyse_threads", "board_player_bar_mode", "eval_bar_position", "eval_bar_range",
            "graph_bar_range", "graph_bar_scale", "graph_line_range", "graph_line_scale", "lichess_max_games",
            "manual_depth", "manual_hash", "manual_multipv", "manual_numarrows", "manual_threads", "preview_hash", "preview_threads")
        fields("Long", "board_black_piece_color", "board_black_square_color", "board_white_piece_color",
            "board_white_square_color", "eval_bar_color_1", "eval_bar_color_2", "first_game_retrieved_version",
            "graph_analyse_line_color", "graph_background_color", "graph_negative_score_color", "graph_plus_score_color",
            "graph_vertical_line_color", "manual_black_arrow_color", "manual_multilines_arrow_color", "manual_white_arrow_color")
        fields("Float", "analyse_seconds", "preview_seconds")
    }
}
