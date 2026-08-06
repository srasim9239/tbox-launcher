package vad.dashing.tbox

/** Theme cache roots used by [LauncherAppIconPaths] — standalone has no theme packs. */
object ThemeMaterialization {
    const val THEMES_ROOT_DIR = "themes"
    const val ICONS_DIR = "icons"
}

object ThemeCacheKeys {
    private val SAFE_KEY_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")

    fun sanitizeCacheKey(raw: String): String {
        val cleaned = raw
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_', '.')
            .take(64)
        if (cleaned.isEmpty()) return "theme"
        val first = cleaned.first()
        if (!first.isLetterOrDigit()) return "t_$cleaned"
        return cleaned
    }

    fun isLikelyCacheKey(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("://")) return false
        return SAFE_KEY_REGEX.matches(trimmed)
    }
}
