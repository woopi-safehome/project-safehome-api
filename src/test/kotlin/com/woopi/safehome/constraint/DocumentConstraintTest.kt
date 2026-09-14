package com.woopi.safehome.constraint

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * 문서가 가리키는 곳이 실제로 있는지 검사한다.
 *
 * 문서는 어긋나도 빌드가 멀쩡하다. 여기서는 그중 기계가 판정할 수 있는 것만 본다 —
 * 링크, 인용한 절, 계약 표식, 머리글과 문서 지도, 제약 테스트가 실패 메시지에 적은 근거 문서.
 * 문장이 코드 동작에 대해 사실인지는 보지 못한다. 그것은 CLAUDE.md 의 필수 절차가 맡는다.
 *
 * 검사 대상을 목록으로 적지 않고 저장소를 걸어서 찾는다. 목록은 또 하나의 사본이 되어 갈라진다.
 * 그래서 검사마다 대상을 하나 이상 찾았는지 먼저 본다. 찾지 못하면 문제 목록이 비어
 * 아무것도 보지 않은 채 통과한다.
 */
class DocumentConstraintTest : BehaviorSpec({

    val root = File(".").canonicalFile
    val readme = File(root, "README.md").canonicalFile

    fun filesUnder(dir: File, suffix: String): List<File> = dir.walkTopDown()
        .onEnter { it == dir || !(it.name.startsWith(".") || it.name == "build" || it.name == "node_modules") }
        .filter { it.isFile && it.name.endsWith(suffix) }
        .map { it.canonicalFile }
        .toList()

    fun rel(file: File) = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
    fun inRepo(file: File) = file.canonicalFile.toPath().startsWith(root.toPath())

    val docs = filesUnder(root, ".md")
    val testSources = filesUnder(File(root, "src/test"), ".kt")

    // 이 테스트가 속한 패키지가 제약 테스트의 자리다
    val constraintDir = File(root, "src/test/kotlin/" + DocumentConstraintTest::class.java.packageName.replace('.', '/'))
    val constraintTests = filesUnder(constraintDir, ".kt")

    // 저장소 밖을 가리키는 경로는 워크스페이스에 함께 있을 때만 유효하므로 검사하지 않는다
    fun leavesRepo(ref: String, from: File) = !inRepo(File(from.parentFile, ref))

    // 적힌 자리 기준, 저장소 루트 기준, 경로 끝이 일치하는 문서가 하나뿐인 경우 순으로 찾는다
    fun resolveDoc(ref: String, from: File? = null): File? =
        from?.let { File(it.parentFile, ref).canonicalFile }?.takeIf { it.isFile }
            ?: File(root, ref).canonicalFile.takeIf { it.isFile }
            ?: docs.filter { rel(it).endsWith("/$ref") }.singleOrNull()

    fun clean(s: String) = s.replace("**", "").replace("`", "").trim()

    val headingLine = Regex("""^(#{1,6})\s+(.+)$""")

    // 코드 블록 밖의 제목들: (줄 번호, 단계, 제목)
    fun headingsOf(lines: List<String>): List<Triple<Int, Int, String>> {
        var fenced = false
        return lines.mapIndexedNotNull { i, line ->
            val fence = line.trimStart().startsWith("```")
            if (fence) fenced = !fenced
            if (fence || fenced) null
            else headingLine.find(line)?.let { Triple(i, it.groupValues[1].length, clean(it.groupValues[2])) }
        }
    }

    fun sectionEnd(lines: List<String>, headings: List<Triple<Int, Int, String>>, start: Triple<Int, Int, String>) =
        headings.firstOrNull { it.first > start.first && it.second <= start.second }?.first ?: lines.size

    // "절 - 항목" 형태면 앞은 제목에, 뒤는 본문 어딘가에 있어야 한다
    fun cites(doc: File, section: String): Boolean {
        val parts = clean(section).split(" - ")
        val lines = doc.readLines()
        return headingsOf(lines).any { parts.first() in it.third } &&
            parts.drop(1).all { part -> lines.any { part in it } }
    }

    val link = Regex("""\]\(([^)\s]+)\)""")
    val sectionName = """(?:"([^"]+)"|\*\*([^*]+)\*\*|([^\s"*]+(?: [^\s"*]+)?) 절(?!차))"""
    val docCitation = Regex("""([A-Za-z0-9_./-]+\.md)[`)]? ?의 $sectionName""")
    val readmeCitation = Regex("""README ?의 $sectionName""")
    val localCitation = Regex("""(?:위|아래) (?:"([^"]+)"|\*\*([^*]+)\*\*)""")

    fun MatchResult.cited(from: Int) = groupValues.drop(from).first { it.isNotEmpty() }

    Given("문서의 링크") {
        When("가리키는 곳을 따라가면") {
            val internal = docs.flatMap { doc ->
                link.findAll(doc.readText()).map { it.groupValues[1] }
                    .filterNot { raw ->
                        val target = raw.substringBefore('#')
                        target.isEmpty() || listOf("http:", "https:", "mailto:").any { target.startsWith(it) } ||
                            leavesRepo(target, doc)
                    }
                    .map { doc to it }
                    .toList()
            }
            val broken = internal
                .filterNot { (doc, raw) -> File(doc.parentFile, raw.substringBefore('#')).exists() }
                .map { (doc, raw) -> "${rel(doc)} → $raw" }

            Then("검사할 링크를 찾아야 한다") {
                withClue("링크를 하나도 찾지 못하면 아무것도 보지 않고 통과한다 — CLAUDE.md") {
                    internal.size shouldNotBe 0
                }
            }

            Then("파일이 있어야 한다") {
                withClue("경로를 옮기면 그곳을 가리키는 문서도 함께 고친다 — CLAUDE.md") {
                    broken shouldBe emptyList()
                }
            }
        }
    }

    Given("문서 경로") {
        When("문서와 테스트에 적힌 경로를 찾으면") {
            val backticked = Regex("""`([A-Za-z0-9_./-]+\.md)`""")
            val bare = Regex("""[A-Za-z0-9_./-]+\.md""")

            val docRefs = docs.flatMap { doc ->
                backticked.findAll(doc.readText()).map { doc to it.groupValues[1] }
                    .filterNot { (d, ref) -> leavesRepo(ref, d) }
                    .toList()
            }
            val testRefs = testSources.flatMap { src ->
                bare.findAll(src.readText()).map { src to it.value }
                    .filterNot { (_, ref) -> ref.startsWith("../") }
                    .toList()
            }
            val missing =
                docRefs.filter { (d, ref) -> resolveDoc(ref, d) == null }.map { (d, ref) -> "${rel(d)} → $ref" } +
                    testRefs.filter { (_, ref) -> resolveDoc(ref) == null }.map { (s, ref) -> "${rel(s)} → $ref" }

            Then("검사할 경로를 찾아야 한다") {
                withClue("문서 경로를 하나도 찾지 못하면 아무것도 보지 않고 통과한다 — CLAUDE.md") {
                    (docRefs.size + testRefs.size) shouldNotBe 0
                }
            }

            Then("그 문서가 있어야 한다") {
                withClue("문서를 옮기거나 지우면 이름을 적은 곳도 함께 고친다 — CLAUDE.md") {
                    missing shouldBe emptyList()
                }
            }
        }
    }

    Given("인용한 절") {
        When("문서와 테스트가 인용한 절을 찾으면") {
            val problems = mutableListOf<String>()
            var examined = 0

            fun verifyCitation(at: String, ref: String, target: File?, section: String) {
                examined++
                when {
                    target == null -> problems += "$at → $ref 가 없다"
                    !cites(target, section) -> problems += "$at → $ref 에 \"$section\" 절이 없다"
                }
            }

            docs.forEach { doc ->
                doc.readLines().forEachIndexed { i, line ->
                    val at = "${rel(doc)}:${i + 1}"
                    docCitation.findAll(line)
                        .filterNot { leavesRepo(it.groupValues[1], doc) }
                        .forEach { verifyCitation(at, it.groupValues[1], resolveDoc(it.groupValues[1], doc), it.cited(2)) }
                    readmeCitation.findAll(line).forEach { verifyCitation(at, "README.md", readme, it.cited(1)) }
                    localCitation.findAll(line).forEach { verifyCitation(at, rel(doc), doc, it.cited(1)) }
                }
            }
            testSources.forEach { src ->
                src.readLines().forEachIndexed { i, line ->
                    docCitation.findAll(line).forEach {
                        verifyCitation("${rel(src)}:${i + 1}", it.groupValues[1], resolveDoc(it.groupValues[1]), it.cited(2))
                    }
                }
            }

            Then("검사할 인용을 찾아야 한다") {
                withClue("인용을 하나도 찾지 못하면 아무것도 보지 않고 통과한다. 인용 형식이 바뀌었을 수 있다 — CLAUDE.md") {
                    examined shouldNotBe 0
                }
            }

            Then("그 절이 문서에 있어야 한다") {
                withClue("절 이름을 바꾸면 인용한 곳도 함께 고친다 — CLAUDE.md") {
                    problems shouldBe emptyList()
                }
            }
        }
    }

    Given("계약 절") {
        When("제목에 계약이 들어간 절을 찾으면") {
            val contracts = docs.flatMap { doc ->
                val lines = doc.readLines()
                val headings = headingsOf(lines)
                headings.filter { "계약" in it.third }.map { h ->
                    val marked = lines.subList(h.first, sectionEnd(lines, headings, h)).any { "이 절은 계약이다" in it }
                    Triple(doc, h.third, marked)
                }
            }
            val unmarked = contracts.filterNot { it.third }.map { "${rel(it.first)} → ${it.second}" }

            Then("계약 절을 찾아야 한다") {
                withClue("이 저장소는 클라이언트가 맞추는 계약을 제공한다. 하나도 찾지 못하면 표식 검사가 아무것도 보지 않는다 — README.md") {
                    contracts.size shouldNotBe 0
                }
            }

            Then("스스로 계약임을 밝혀야 한다") {
                withClue("표식이 없으면 코드를 뒤따르는 설명으로 읽혀, 어긋났을 때 코드가 아니라 문서를 고치게 된다 — CLAUDE.md") {
                    unmarked shouldBe emptyList()
                }
            }
        }
    }

    Given("문서 지도와 머리글") {
        val lines = readme.readLines()
        val headings = headingsOf(lines)
        val mapped = headings.firstOrNull { "문서 지도" in it.third }?.let { start ->
            lines.subList(start.first, sectionEnd(lines, headings, start))
                .flatMap { l -> link.findAll(l).map { File(root, it.groupValues[1].substringBefore('#')).canonicalFile }.toList() }
                .filter { it.name != "CLAUDE.md" }
        }

        When("스스로 문서임을 밝힌 문서를 모으면") {
            val declared = docs.filter { d -> d != readme && d.readLines().take(15).any { it.startsWith("> **범위**") } }
            val unmapped = if (mapped == null) listOf("README.md 에 문서 지도 절이 없다")
            else declared.filter { it !in mapped }.map { rel(it) }

            Then("모듈 문서를 찾아야 한다") {
                withClue("머리글로 문서임을 밝힌 모듈 문서를 하나도 찾지 못하면 문서 지도 검사가 아무것도 보지 않는다 — CLAUDE.md") {
                    declared.size shouldNotBe 0
                }
            }

            Then("저장소 README 를 뺀 전부가 문서 지도에 있어야 한다") {
                withClue("문서를 만들면 문서 지도에 올린다. 찾아갈 길이 없는 문서는 읽히지 않는다 — CLAUDE.md") {
                    unmapped shouldBe emptyList()
                }
            }
        }

        When("머리글을 보면") {
            val problems = (listOf(readme) + mapped.orEmpty()).filter { it.isFile }.flatMap { d ->
                val head = d.readLines().take(15).filter { it.startsWith(">") }.joinToString(" ")
                listOfNotNull(
                    "범위".takeIf { "**범위**" !in head },
                    "여기 없는 것".takeIf { "**여기 없는 것**" !in head },
                    "상위".takeIf { d != readme && "**상위**" !in head },
                ).map { "${rel(d)} → $it" }
            }

            Then("범위와 여기 없는 것을, 모듈 문서라면 상위도 밝혀야 한다") {
                withClue("머리글이 어디까지 믿고 어디부터 코드를 볼지 알려준다 — CLAUDE.md") {
                    problems shouldBe emptyList()
                }
            }
        }
    }

    Given("제약 테스트") {
        When("검사마다 실패 메시지를 보면") {
            val site = Regex("""\.check\(|\bshould[A-Z]\w*""")
            // 주석은 실패 출력에 나오지 않고, import 는 검사가 아니므로 빼고 본다
            val ignored = Regex("""/\*[\s\S]*?\*/|(?:^|\s)//[^\n]*|^import [^\n]*""", RegexOption.MULTILINE)

            var sites = 0
            val problems = constraintTests.flatMap { t ->
                // 줄 번호를 지키려고 줄바꿈은 남긴다
                val code = ignored.replace(t.readText()) { m -> m.value.filter { it == '\n' } }
                var from = 0
                site.findAll(code).mapNotNull { m ->
                    sites++
                    val segment = code.substring(from, m.range.first)
                    from = m.range.last + 1
                    val line = code.substring(0, m.range.first).count { it == '\n' } + 1
                    if (".md" in segment) null else "${rel(t)}:$line → ${m.value}"
                }.toList()
            }

            Then("이 파일 말고도 제약 테스트를 찾아야 한다") {
                withClue("제약 테스트를 찾지 못하면 근거 문서 검사가 아무것도 보지 않고 통과한다 — CLAUDE.md") {
                    constraintTests.count { it.name != "DocumentConstraintTest.kt" } shouldNotBe 0
                }
            }

            Then("검사 지점을 찾아야 한다") {
                withClue("검사 지점을 하나도 찾지 못하면 아무것도 보지 않고 통과한다. 검사 방식이 바뀌었을 수 있다 — CLAUDE.md") {
                    sites shouldNotBe 0
                }
            }

            Then("근거 문서를 담아야 한다") {
                withClue("어긴 순간에 문서가 도착해야 한다. 주석이 아니라 실패 메시지에 적는다 — CLAUDE.md") {
                    problems shouldBe emptyList()
                }
            }
        }
    }
})
