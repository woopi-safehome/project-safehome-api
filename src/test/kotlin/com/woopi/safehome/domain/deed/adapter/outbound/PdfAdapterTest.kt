package com.woopi.safehome.domain.deed.adapter.outbound

import com.woopi.safehome.domain.deed.domain.exception.InvalidPdfException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/**
 * 문서 검증과 섹션 분리를 고정한다.
 *
 * 여기서 실패하면 사용자에게 보이는 실패가 된다. 반대로 분리를 잘못하면
 * 아무 에러 없이 빈 섹션이 분석 서버로 넘어간다.
 *
 * 배경: domain/deed/README.md 의 "실패를 어떻게 다루는가"
 */
class PdfAdapterTest : BehaviorSpec({

    fun pdfOf(vararg lines: String): ByteArray {
        val doc = PDDocument()
        val page = PDPage()
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.beginText()
            cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
            cs.setLeading(14f)
            cs.newLineAtOffset(20f, 750f)
            lines.forEach { cs.showText(it); cs.newLine() }
            cs.endText()
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    Given("업로드 검증") {
        val validator = PdfValidationAdapter()

        When("빈 파일이면") {
            Then("사용자에게 보이는 실패로 끊는다") {
                shouldThrow<InvalidPdfException> { validator.validate(ByteArray(0), "application/pdf") }
            }
        }

        When("PDF 가 아니면") {
            Then("끊는다") {
                shouldThrow<InvalidPdfException> { validator.validate(byteArrayOf(1), "image/png") }
            }
        }

        When("형식이 비어 있으면") {
            Then("끊는다") {
                // 형식을 모르면 통과시키지 않는다. 안전한 쪽이 기본이다.
                shouldThrow<InvalidPdfException> { validator.validate(byteArrayOf(1), null) }
            }
        }

        When("올바른 PDF 면") {
            Then("통과시킨다") {
                validator.validate(byteArrayOf(1), "application/pdf")
            }
        }
    }

    Given("섹션 분리") {
        val parser = PdfBoxParserAdapter()

        When("섹션 표시가 없는 문서를 받으면") {
            val sections = parser.parse(pdfOf("no markers here", "just text")).sections

            Then("예외 없이 빈 섹션이 된다") {
                // 여기서 끊기지 않는다. 빈 섹션이 그대로 분석 서버로 넘어간다 —
                // 등기부가 아닌 문서는 분석 서버가 판정한다.
                sections.isEmpty() shouldBe true
            }
        }
    }

})
