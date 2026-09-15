package com.woopi.safehome.global.datasource

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

/**
 * 읽기 전용 트랜잭션이 실제로 어느 인스턴스를 쓰는지 실행해서 고정한다.
 *
 * 설정에는 읽기/쓰기 인스턴스가 나뉘어 있고 트랜잭션의 읽기 여부를 보는 인터셉터도 있어서,
 * 코드만 읽으면 분리가 동작하는 것처럼 보인다. 실행해 보면 모든 연결이 쓰기 인스턴스로 간다.
 *
 * 분리를 켜는 변경을 하면 이 테스트가 깨진다. 그때 복제 지연을 모르는 조회 — 트랜잭션 밖에서
 * 방금 쓴 작업을 다시 읽는 분석 비동기 처리부 같은 곳 — 를 먼저 다루고 문서를 함께 고친다.
 *
 * 배경: global/README.md 의 "읽기 전용 표시는 인스턴스를 고르지 않는다"
 */
@SpringBootTest
@Import(DataSourceRoutingTest.ProbeConfig::class)
class DataSourceRoutingTest(
    @Autowired private val methodProbe: ReadOnlyMethodProbe,
    @Autowired private val classProbe: ReadOnlyClassProbe,
    @Autowired @Qualifier("writeDataSource") private val writeDataSource: DataSource,
    @Autowired @Qualifier("readDataSource") private val readDataSource: DataSource,
) : BehaviorSpec({

    Given("읽기와 쓰기 인스턴스가 나뉜 설정") {
        val writeUrl = writeDataSource.connection.use { it.metaData.url }
        val readUrl = readDataSource.connection.use { it.metaData.url }

        When("트랜잭션 안에서 실제로 잡힌 연결을 보면") {
            Then("두 인스턴스가 구분돼야 한다") {
                withClue("구분되지 않으면 아래 확인이 아무것도 가리지 못한다 — global/README.md") {
                    writeUrl shouldNotBe readUrl
                }
            }

            Then("메서드에 읽기 전용을 선언해도 쓰기 인스턴스를 쓴다") {
                withClue("분리가 켜졌다면 복제 지연을 모르는 조회부터 다루고 문서를 고친다 — global/README.md") {
                    methodProbe.connectedUrl() shouldBe writeUrl
                }
            }

            Then("클래스에 읽기 전용을 선언해도 쓰기 인스턴스를 쓴다") {
                withClue("분리가 켜졌다면 복제 지연을 모르는 조회부터 다루고 문서를 고친다 — global/README.md") {
                    classProbe.connectedUrl() shouldBe writeUrl
                }
            }
        }
    }
}) {
    @TestConfiguration
    class ProbeConfig {
        @Bean
        fun readOnlyMethodProbe(dataSource: DataSource) = ReadOnlyMethodProbe(dataSource)

        @Bean
        fun readOnlyClassProbe(dataSource: DataSource) = ReadOnlyClassProbe(dataSource)
    }
}

open class ReadOnlyMethodProbe(private val dataSource: DataSource) {
    @Transactional(readOnly = true)
    open fun connectedUrl(): String = DataSourceUtils.getConnection(dataSource).metaData.url
}

@Transactional(readOnly = true)
open class ReadOnlyClassProbe(private val dataSource: DataSource) {
    open fun connectedUrl(): String = DataSourceUtils.getConnection(dataSource).metaData.url
}
