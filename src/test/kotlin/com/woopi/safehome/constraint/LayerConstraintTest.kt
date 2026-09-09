package com.woopi.safehome.constraint

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.BehaviorSpec
import jakarta.persistence.Entity

/**
 * 헥사고날 계층 규칙을 실행 가능한 형태로 고정한다.
 *
 * 여기 있는 규칙은 전부 CLAUDE.md 와 domain/README.md 가 산문으로 적어 두던 것이다.
 * 산문은 읽어야 작동하고 조용히 낡는다. 여기로 옮긴 규칙은 어기면 빌드가 깨진다.
 */
class LayerConstraintTest : BehaviorSpec({

    val classes = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.woopi.safehome")

    Given("도메인 계층") {
        When("바깥 기술에 대한 의존을 검사하면") {
            Then("프레임워크와 영속성을 알지 못해야 한다") {
                noClasses()
                    .that().resideInAPackage("com.woopi.safehome.domain.*.domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta..")
                    .because(
                        "도메인이 바깥을 모르므로 저장소나 진입 방식을 바꿔도 규칙은 그대로다. " +
                            "한 줄이라도 들어오면 이 이점이 사라진다 — domain/README.md"
                    )
                    .check(classes)
            }
        }
    }

    Given("영속 엔티티") {
        When("위치를 검사하면") {
            Then("도메인 패키지 안에 있어야 한다") {
                classes()
                    .that().areAnnotatedWith(Entity::class.java)
                    .should().resideInAPackage("com.woopi.safehome.domain..")
                    .because(
                        "엔티티 스캔 대상이 도메인 패키지다. 밖에 두면 에러 없이 인식되지 않는다 — CLAUDE.md"
                    )
                    .check(classes)
            }
        }
    }

    Given("데이터소스 컨텍스트") {
        When("접근하는 곳을 검사하면") {
            Then("트랜잭션 인터셉터와 라우팅 데이터소스만 만져야 한다") {
                noClasses()
                    .that().resideOutsideOfPackages(
                        "com.woopi.safehome.global.aop..",
                        "com.woopi.safehome.global.datasource..",
                    )
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("com.woopi.safehome.global.datasource.DataSourceContextHolder")
                    .because(
                        "읽기/쓰기 선택은 트랜잭션 애노테이션 하나로만 결정돼야 한다. " +
                            "직접 조작하면 애노테이션과 실제 커넥션이 어긋나는데 동작은 정상으로 보인다 — global/README.md"
                    )
                    .check(classes)
            }
        }
    }

    Given("유스케이스 계층") {
        When("어댑터에 대한 의존을 검사하면") {
            Then("어댑터를 참조하지 않아야 한다") {
                noClasses()
                    .that().resideInAPackage("com.woopi.safehome.domain.*.application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.woopi.safehome.domain.*.adapter..")
                    .because(
                        "application 이 어댑터 구현체를 직접 참조하면 의존 방향이 뒤집히고, " +
                            "포트 자리에 대역을 끼워 테스트하는 성질이 사라진다 — domain/README.md"
                    )
                    .check(classes)
            }
        }
    }
})
