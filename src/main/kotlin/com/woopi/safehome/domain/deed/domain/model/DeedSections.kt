package com.woopi.safehome.domain.deed.domain.model

data class DeedSections(
    val sections: Map<String, List<String>>  // {"표제부" -> [...], "갑구" -> [...], "을구" -> [...]}
) {
    fun get(name: String): List<String> = sections[name] ?: emptyList()
    fun hasSection(name: String): Boolean = sections.containsKey(name)
    fun sectionNames(): Set<String> = sections.keys
    fun isEmpty(): Boolean = sections.isEmpty()

    /**
     * **본문을 내놓지 않는다.** 등기부에는 소유자 이름과 주소가 있다.
     *
     * data class 의 기본 문자열은 필드를 통째로 찍으므로, 이 객체를 로그에 넘기는 순간
     * 개인정보가 로그로 나간다 — 실제로 그렇게 나가고 있었다. 로그 줄 하나를 고치는 대신
     * 여기서 막아, 어디서 찍히든 섹션 이름과 분량만 남게 한다.
     */
    override fun toString(): String =
        sections.entries.joinToString(prefix = "DeedSections(", postfix = ")") { (name, blocks) ->
            "$name=${blocks.sumOf { it.length }}자"
        }
}
