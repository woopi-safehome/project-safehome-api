package com.woopi.safehome.global.config

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.io.File

/**
 * 설정이 가리키는 DB 초기화 스크립트가 실제로 실행되는지 확인한다.
 *
 * 테스트 컨텍스트는 프로파일 없이 떠서 초기화 스크립트를 돌리지 않는다. 그래서 스크립트가 비거나 깨져도
 * 테스트는 초록이고, 로컬·개발 서버만 기동 중에 죽는다 — 주석만 남은 데이터 스크립트가 실제로 그랬다.
 *
 * H2 를 쓰는 프로파일의 스크립트는 같은 접속 옵션의 메모리 DB 에서 실제로 돌린다.
 * 그 밖의 스크립트는 실행할 DB 가 없으므로 주석이 아닌 문장이 있는지만 본다 — 실제 실행은 이미지 스모크가 맡는다.
 *
 * 배경: resources/README.md 의 "조용히 깨지는 것들"
 */
class SqlInitScriptTest : BehaviorSpec({

    val config = File("src/main/resources/application-db.yml").readText()
    val location = Regex("""(?:schema|data)-locations:\s*classpath\*?:(\S+)""")
    val h2Options = Regex("""url:\s*jdbc:h2:[^;\s]+(;[^\s]+)?""")

    // 프로파일 블록마다 (스크립트 경로들, H2 접속 옵션 또는 null)
    val blocks = config.split(Regex("(?m)^---\\s*$"))
        .map { block ->
            val scripts = location.findAll(block).map { it.groupValues[1] }.toList()
            val options = if ("org.h2.Driver" in block) {
                h2Options.find(block)?.groupValues?.get(1)?.split(';')
                    ?.filter { it.startsWith("MODE=") || it.startsWith("DATABASE_TO_UPPER=") }
                    ?.joinToString(";", prefix = ";").orEmpty()
            } else null
            scripts to options
        }
        .filter { it.first.isNotEmpty() }

    Given("설정 파일이 가리키는 초기화 스크립트") {
        val all = blocks.flatMap { it.first }

        When("스크립트를 모으면") {
            Then("하나 이상 찾아야 한다") {
                withClue("초기화 스크립트를 찾지 못하면 아무것도 보지 않고 통과한다 — resources/README.md") {
                    all.size shouldNotBe 0
                }
            }

            Then("모두 존재하고 주석이 아닌 문장을 담아야 한다") {
                val problems = all.mapNotNull { path ->
                    val resource = ClassPathResource(path)
                    when {
                        !resource.exists() -> "$path 가 없다"
                        resource.inputStream.bufferedReader().readLines()
                            .none { it.isNotBlank() && !it.trimStart().startsWith("--") } -> "$path 에 실행할 문장이 없다"
                        else -> null
                    }
                }
                withClue("주석만 남은 스크립트는 기동 중에 초기화를 실패시킨다. 필요 없으면 설정에서 위치를 지운다 — resources/README.md") {
                    problems shouldBe emptyList()
                }
            }
        }

        When("H2 를 쓰는 프로파일의 스크립트를 순서대로 실행하면") {
            val failures = blocks.filter { it.second != null }.mapIndexedNotNull { i, (scripts, options) ->
                val dataSource = DriverManagerDataSource("jdbc:h2:mem:sql-init-$i;DB_CLOSE_DELAY=-1$options")
                val populator = ResourceDatabasePopulator(*scripts.map { ClassPathResource(it) }.toTypedArray())
                runCatching { populator.execute(dataSource) }.exceptionOrNull()?.let { "$scripts → ${it.message}" }
            }

            Then("H2 프로파일을 하나 이상 찾아야 한다") {
                withClue("H2 프로파일을 찾지 못하면 실행 확인이 아무것도 보지 않는다 — resources/README.md") {
                    blocks.count { it.second != null } shouldNotBe 0
                }
            }

            Then("오류 없이 실행돼야 한다") {
                withClue("여기서 실패하면 로컬 서버가 기동 중에 죽는다 — resources/README.md") {
                    failures shouldBe emptyList()
                }
            }
        }
    }
})
