package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.application.port.outbound.PdfParserPort
import com.woopi.safehome.domain.deed.domain.model.DeedSections
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

@Component
class PdfBoxParserAdapter : PdfParserPort {

    override fun parse(content: ByteArray): DeedSections {
        val text = Loader.loadPDF(content).use {
            PDFTextStripper().getText(it)
        }
        return DeedSections(splitSections(text))
    }

    private fun splitSections(text: String): Map<String, List<String>> {
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
