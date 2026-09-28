package `in`.miaolibrary.cataloging.model

import android.content.Context
import kotlinx.serialization.json.Json

class CatalogDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("cataloging_draft", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(record: CatalogRecord) {
        prefs.edit().putString(KEY, json.encodeToString(CatalogRecord.serializer(), record)).apply()
    }

    fun get(): CatalogRecord? = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(CatalogRecord.serializer(), it) }
    }.getOrNull()

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "record"
    }
}
