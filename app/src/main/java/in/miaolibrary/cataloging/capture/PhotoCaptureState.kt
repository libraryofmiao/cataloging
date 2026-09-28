package `in`.miaolibrary.cataloging.capture

import android.content.SharedPreferences
import java.io.File

enum class RequiredPhoto(val label: String) {
    FRONT_COVER("Front cover"),
    TITLE_PAGE("Title page"),
    PUBLICATION_PAGE("Copyright / publication page"),
    ISBN_PAGE("ISBN / barcode page")
}

data class PhotoCaptureState(val photos: Map<RequiredPhoto, String> = emptyMap()) {
    fun isComplete() = RequiredPhoto.entries.all { photos[it] != null && File(photos[it]!!).exists() }
    fun next(): RequiredPhoto? = RequiredPhoto.entries.firstOrNull { photos[it] == null || !File(photos[it]!!).exists() }
    fun add(type: RequiredPhoto, path: String) = copy(photos = photos + (type to path))

    fun saveTo(prefs: SharedPreferences) {
        val editor = prefs.edit()
        RequiredPhoto.entries.forEach { type ->
            editor.putString(KEY_PREFIX + type.name, photos[type])
        }
        editor.apply()
    }

    fun clearSaved(prefs: SharedPreferences) {
        val editor = prefs.edit()
        RequiredPhoto.entries.forEach { editor.remove(KEY_PREFIX + it.name) }
        editor.apply()
    }

    companion object {
        private const val KEY_PREFIX = "photo_"

        fun restore(prefs: SharedPreferences): PhotoCaptureState {
            val restored = RequiredPhoto.entries.mapNotNull { type ->
                val path = prefs.getString(KEY_PREFIX + type.name, null)
                    ?.takeIf { it.isNotBlank() && File(it).exists() }
                path?.let { type to it }
            }.toMap()
            return PhotoCaptureState(restored)
        }
    }
}
