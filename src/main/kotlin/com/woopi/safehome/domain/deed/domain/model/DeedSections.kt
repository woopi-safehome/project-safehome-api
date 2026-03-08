package com.woopi.safehome.domain.deed.domain.model

data class DeedSections(
    val sections: Map<String, List<String>>  // {"표제부" -> [...], "갑구" -> [...], "을구" -> [...]}
) {
    fun get(name: String): List<String> = sections[name] ?: emptyList()
    fun hasSection(name: String): Boolean = sections.containsKey(name)
    fun sectionNames(): Set<String> = sections.keys
}
