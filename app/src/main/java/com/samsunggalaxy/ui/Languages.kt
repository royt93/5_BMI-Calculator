package com.samsunggalaxy.ui

data class Language(val code: String, val name: String)

object Languages {
    val ALL: List<Language> = listOf(
        Language("en", "🇺🇸 English"),
        Language("vi", "🇻🇳 Tiếng Việt"),
        Language("es", "🇪🇸 Español"),
        Language("pt", "🇧🇷 Português (Brasil)"),
        Language("ar", "🇸🇦 العربية"),
        Language("hi", "🇮🇳 हिन्दी"),
        Language("zh", "🇨🇳 中文(简体)"),
        Language("id", "🇮🇩 Bahasa Indonesia"),
        Language("tr", "🇹🇷 Türkçe"),
        Language("ru", "🇷🇺 Русский"),
        Language("it", "🇮🇹 Italiano"),
        Language("nl", "🇳🇱 Nederlands"),
        Language("fr", "🇫🇷 Français"),
        Language("de", "🇩🇪 Deutsch"),
        Language("ja", "🇯🇵 日本語"),
        Language("ko", "🇰🇷 한국어"),
        Language("th", "🇹🇭 ภาษาไทย"),
    )

    private val byCode: Map<String, Language> = ALL.associateBy { it.code }

    fun displayName(code: String): String = byCode[code]?.name?.substringAfter(' ') ?: "English"
}
