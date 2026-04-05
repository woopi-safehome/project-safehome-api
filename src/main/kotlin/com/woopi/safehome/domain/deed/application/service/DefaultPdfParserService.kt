package com.woopi.safehome.domain.deed.application.service

import com.woopi.safehome.domain.deed.domain.model.DeedSections
import com.woopi.safehome.domain.deed.domain.service.PdfParserService
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service

@Service
class DefaultPdfParserService : PdfParserService {
    override fun parse(content: ByteArray): DeedSections {
        val text = Loader.loadPDF(content).use {
            PDFTextStripper().getText(it)
        }
        return DeedSections(splitSections(text))
    }

    fun splitSections(text: String): Map<String, List<String>> {
        // '-- 이 하 여 백 --' 이후는 불필요한 공백 표시이므로 제거 (띄어쓰기 유동적 처리)
        val endMarker = Regex("""--\s*이\s*하\s*여\s*백\s*--""")
        val effectiveText = endMarker.find(text)?.let { text.substring(0, it.range.first) } ?: text

        val sectionPattern = Regex("""【([^】]*)】""")
        val matches = sectionPattern.findAll(effectiveText).toList()

        val result = LinkedHashMap<String, MutableList<String>>()

        matches.forEachIndexed { index, match ->
            val key = match.groupValues[1].replace(" ", "")
            val startPos = match.range.first
            val endPos = if (index + 1 < matches.size) matches[index + 1].range.first else effectiveText.length
            val content = effectiveText.substring(startPos, endPos).trim()

            result.getOrPut(key) { mutableListOf() }.add(content)
        }

        return result
    }
}
