package `in`.miaolibrary.cataloging.capture

import android.content.SharedPreferences
import java.io.File

enum class RequiredPhoto(val label: String) {
    FRONT_COVER("Front cover"),
    TITLE_PAGE("Title page"),
    PUBLICATION_PAGE("Copyright / publication page"),
    ISBN_PAGE("ISBN / barcode page")
}

data class PhotoCaptureState(val photos: Map<RequiredPhoto, String> = emptyMap(), val optionalPhotos: List<String> = emptyList()) {
    fun isComplete() = RequiredPhoto.entries.all { photos[it] != null && File(photos[it]!!).exists() }
    fun next(): RequiredPhoto? = RequiredPhoto.entries.firstOrNull { photos[it] == null || !File(photos[it]!!).exists() }
    fun add(type: RequiredPhoto, path: String) = copy(photos = photos + (type to path))
    fun addOptional(path: String) = copy(optionalPhotos = optionalPhotos + path)
    fun allPhotos(): List<String> = photos.values.toList() + optionalPhotos

    fun saveTo(prefs: SharedPreferences) {
        val editor = prefs.edit()
        RequiredPhoto.entries.forEach { type -> editor.putString(KEY_PREFIX + type.name, photos[type]) }
        editor.putInt(OPTIONAL_COUNT, optionalPhotos.size)
        optionalPhotos.forEachIndexed { index, path -> editor.putString(OPTIONAL_PREFIX + index, path) }
        editor.apply()
    }

    fun clearSaved(prefs: SharedPreferences) {
        val editor = prefs.edit()
        RequiredPhoto.entries.forEach { editor.remove(KEY_PREFIX + it.name) }
        repeat(prefs.getInt(OPTIONAL_COUNT, 0)) { editor.remove(OPTIONAL_PREFIX + it) }
        editor.remove(OPTIONAL_COUNT)
        editor.apply()
    }

    companion object {
        private const val KEY_PREFIX = "photo_"
        private const val OPTIONAL_PREFIX = "optional_photo_"
        private const val OPTIONAL_COUNT = "optional_photo_count"

        fun restore(prefs: SharedPreferences): PhotoCaptureState {
            val restored = RequiredPhoto.entries.mapNotNull { type ->
                val path = prefs.getString(KEY_PREFIX + type.name, null)
                    ?.takeIf { it.isNotBlank() && File(it).exists() }
                path?.let { type to it }
            }.toMap()
            val optionalCount = prefs.getInt(OPTIONAL_COUNT, 0)
            val optional = (0 until optionalCount).mapNotNull { prefs.getString(OPTIONAL_PREFIX + it, null)?.takeIf { path -> File(path).exists() } }
            return PhotoCaptureState(restored, optional)
        }
    }
}
