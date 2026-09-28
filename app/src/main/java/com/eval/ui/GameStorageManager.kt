package com.eval.ui

import android.content.Context
import android.content.SharedPreferences
import com.eval.data.ChessServer
import com.eval.data.LichessGame
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

/**
 * Helper class for managing game storage operations via SharedPreferences.
 * Handles storing and loading retrieved games lists and analysed games.
 *
 * Games live in their own preferences file ([PREFS_NAME]): they are large, and sharing
 * a file with the settings made every settings change rewrite megabytes of game JSON.
 */
class GameStorageManager(
    private val prefs: SharedPreferences,
    private val gson: Gson
) {
    companion object {
        const val PREFS_NAME = "eval_games"
        /** Games kept per retrieved account; paging further re-fetches from Lichess. */
        const val MAX_STORED_GAMES_PER_RETRIEVE = 100

        fun create(context: Context, gson: Gson): GameStorageManager {
            val games = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            migrateFromSettings(context.getSharedPreferences(SettingsPreferences.PREFS_NAME, Context.MODE_PRIVATE), games)
            return GameStorageManager(games, gson)
        }

        /** One-time move of game blobs out of the settings file (older versions stored them there). */
        internal fun migrateFromSettings(settings: SharedPreferences, games: SharedPreferences) {
            val legacy = settings.all.filterKeys { SettingsPreferences.isGameStorageKey(it) }
            if (legacy.isEmpty()) return
            val editor = games.edit()
            legacy.forEach { (key, value) -> if (value is String && !games.contains(key)) editor.putString(key, value) }
            // apply(), not commit(): this runs while the ViewModel is created on the main thread and
            // the games can be megabytes. Both files are written in order on QueuedWork's single
            // thread, so the removal below never reaches disk before the copy; if the process dies
            // in between, the next start finds the keys in both files and only removes them here.
            editor.apply()
            settings.edit().apply { legacy.keys.forEach { remove(it) } }.apply()
        }
    }

    /**
     * Generic helper to load a JSON list from SharedPreferences.
     * Returns emptyList() on missing key or parse failure.
     */
    private inline fun <reified T> loadJsonList(key: String): List<T> = loadJsonListOrNull(key) ?: emptyList()

    /** Like [loadJsonList], but null when the stored JSON exists and can't be read. */
    private inline fun <reified T> loadJsonListOrNull(key: String): List<T>? {
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.w("GameStorageManager", "Unreadable stored list $key: ${e.message}")
            null
        }
    }

    // ============================================================================
    // Retrieved Games Storage (List of Lists)
    // ============================================================================

    /**
     * Generate the SharedPreferences key for a specific retrieve entry.
     */
    fun getRetrievedGamesKey(accountName: String, server: ChessServer): String {
        val serverPrefix = server.name.lowercase()
        return "${SettingsPreferences.KEY_RETRIEVED_GAMES_PREFIX}${serverPrefix}_${accountName.lowercase()}"
    }

    private fun getRetrievedGamesKey(entry: RetrievedGamesEntry): String =
        entry.cachedGamesKey?.takeIf { it.startsWith(SettingsPreferences.KEY_RETRIEVED_GAMES_PREFIX) }
            ?: getRetrievedGamesKey(entry.accountName, entry.server)

    /**
     * Store retrieved games for a specific account.
     */
    fun storeRetrievedGames(games: List<LichessGame>, username: String, server: ChessServer) = synchronized(writeLock) {
        // Load existing retrieves list
        val retrievesList = loadRetrievesList().toMutableList()

        // Create new entry
        val newEntry = RetrievedGamesEntry(accountName = username, server = server)

        // Remove any existing entry with same account/server
        retrievesList.removeAll { it.accountName.equals(username, ignoreCase = true) && it.server == server }

        // Add new entry at the beginning
        retrievesList.add(0, newEntry)

        val trimmedKeys = mutableListOf<String>()
        // Trim to max size
        while (retrievesList.size > SettingsPreferences.MAX_RETRIEVES) {
            val removed = retrievesList.removeAt(retrievesList.size - 1)
            trimmedKeys.add(getRetrievedGamesKey(removed))
        }

        // Single atomic editor apply — previously the three writes (remove old
        // entries, write index, write payload) were independent apply() calls,
        // so an app kill between them could leave the index pointing at a key
        // whose games blob hadn't been written yet, or referencing a game blob
        // that had already been evicted.
        val editor = prefs.edit()
        for (key in trimmedKeys) editor.remove(key)
        editor.putString(SettingsPreferences.KEY_RETRIEVES_LIST, gson.toJson(retrievesList))
        editor.putString(getRetrievedGamesKey(username, server), gson.toJson(games.take(MAX_STORED_GAMES_PER_RETRIEVE)))
        editor.apply()
    }

    // Serialises read-modify-write for game-list keys so concurrent callers
    // (e.g. fast successive retrieves across tabs) don't silently drop each
    // other's updates between load and save.
    private val writeLock = Any()

    /**
     * Load the list of previous retrieves.
     */
    fun loadRetrievesList(): List<RetrievedGamesEntry> {
        val json = prefs.getString(SettingsPreferences.KEY_RETRIEVES_LIST, null) ?: return emptyList()
        return try {
            JsonParser().parse(json).asJsonArray.mapNotNull { element ->
                // Retired sources remain usable as local history. Keep their original
                // payload key instead of relabelling them as online Lichess games.
                runCatching {
                    val entry = element.asJsonObject
                    val account = entry.get("accountName").asString
                    val source = entry.get("server").asString
                    if (source != ChessServer.LICHESS.name && source != ChessServer.LOCAL.name) {
                        entry.addProperty("cachedGamesKey",
                            "${SettingsPreferences.KEY_RETRIEVED_GAMES_PREFIX}${source.lowercase()}_${account.lowercase()}")
                        entry.addProperty("server", ChessServer.LOCAL.name)
                    }
                    gson.fromJson(entry, RetrievedGamesEntry::class.java)
                }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Load games for a specific retrieve entry.
     */
    fun loadGamesForRetrieve(entry: RetrievedGamesEntry): List<LichessGame> {
        val key = getRetrievedGamesKey(entry)
        return loadJsonList(key)
    }

    // ============================================================================
    // Manual Stage Game Auto-Restore
    // ============================================================================

    /**
     * Save the current game when entering Manual stage for auto-restore on next startup.
     */
    fun saveManualStageGame(analysedGame: AnalysedGame) {
        val json = gson.toJson(analysedGame)
        prefs.edit().putString(SettingsPreferences.KEY_CURRENT_MANUAL_GAME, json).apply()
    }

    /**
     * Load the manual stage game for auto-restore on startup.
     * Returns null if no game is stored.
     */
    fun loadManualStageGame(): AnalysedGame? {
        val json = prefs.getString(SettingsPreferences.KEY_CURRENT_MANUAL_GAME, null) ?: return null
        return try {
            gson.fromJson(json, AnalysedGame::class.java)
        } catch (e: Exception) {
            android.util.Log.e("GameStorageManager", "loadManualStageGame: Failed to parse JSON", e)
            null
        }
    }

    /**
     * Clear the manual stage game (called when a new game is loaded).
     */
    fun clearManualStageGame() {
        prefs.edit().remove(SettingsPreferences.KEY_CURRENT_MANUAL_GAME).apply()
    }

    // ============================================================================
    // Analysed Games List (Previously Analysed Games)
    // ============================================================================

    /**
     * Store a game to the manual games list when entering Manual stage.
     * Deduplicates by whiteName+blackName+pgn, adds at front, trims to MAX_MANUAL_GAMES.
     */
    fun storeManualGameToList(analysedGame: AnalysedGame) = synchronized(writeLock) {
        val key = SettingsPreferences.KEY_LIST_MANUAL_GAMES
        val list = loadJsonListOrNull<AnalysedGame>(key)?.toMutableList() ?: run {
            // Never overwrite an unreadable history with a one-game list: keep the raw data aside.
            prefs.edit().putString("${key}_unreadable_${System.currentTimeMillis()}", prefs.getString(key, null)).apply()
            mutableListOf()
        }
        // Remove duplicate (same white, black, pgn)
        list.removeAll {
            it.whiteName == analysedGame.whiteName &&
            it.blackName == analysedGame.blackName &&
            it.pgn == analysedGame.pgn
        }
        // Add at front
        list.add(0, analysedGame)
        // Trim to max
        while (list.size > SettingsPreferences.MAX_MANUAL_GAMES) {
            list.removeAt(list.size - 1)
        }
        val json = gson.toJson(list)
        prefs.edit().putString(SettingsPreferences.KEY_LIST_MANUAL_GAMES, json).apply()
    }

    /**
     * Load the full list of previously analysed games.
     */
    fun loadManualGamesList(): List<AnalysedGame> {
        return loadJsonList(SettingsPreferences.KEY_LIST_MANUAL_GAMES)
    }

    /**
     * Quick check if there are any previously analysed games.
     */
    fun hasManualGames(): Boolean {
        val json = prefs.getString(SettingsPreferences.KEY_LIST_MANUAL_GAMES, null)
        return json != null && json != "[]"
    }

}
