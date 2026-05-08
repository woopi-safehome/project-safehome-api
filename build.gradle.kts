plugins {
	kotlin("jvm") version "2.1.0"
	kotlin("plugin.spring") version "2.1.0"
	id("org.springframework.boot") version "3.5.8"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.1.0"
	id("io.sentry.jvm.gradle") version "6.6.0"
}

group = "com.woopi"
version = "0.0.1-SNAPSHOT"
description = "woopi's project, safehome"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

val commonLang3Version = "3.18.0"
val springdocVersion = "2.8.9"
val kotestVersion = "5.9.1"
val kotestSpringExtensionVersion = "1.3.0"

val coroutineCoreVersion = "1.10.2"
val coroutineReactorVersion = "1.10.2"

val embeddedRedisVersion = "0.7.3"

val pdfBoxVersion = "3.0.3"

val sentryVersion = "7.14.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// Coroutines (비동기)
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineCoreVersion")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$coroutineReactorVersion")

	// Apache Commons Lang
	implementation("org.apache.commons:commons-lang3:$commonLang3Version")

	// Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

	// h2 database (로컬)
	runtimeOnly("com.h2database:h2")

	// PostgreSQL (개발/운영)
	runtimeOnly("org.postgresql:postgresql")

	// Redis, (embedded redis - 로깅 충돌을 일으킬 수 있는 의존성 제외)
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("it.ozimov:embedded-redis:${embeddedRedisVersion}") {
		exclude(group = "org.slf4j", module = "slf4j-simple")
		exclude(group = "commons-logging", module = "commons-logging")
	}

	// Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

	// PDFBox
	implementation("org.apache.pdfbox:pdfbox:$pdfBoxVersion")

	// Sentry
	implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")

	// kotest
	testImplementation(platform("io.kotest:kotest-bom:$kotestVersion"))
	testImplementation("io.kotest:kotest-framework-engine")
	testImplementation("io.kotest:kotest-framework-api")
	testImplementation("io.kotest:kotest-runner-junit5")
	testImplementation("io.kotest:kotest-assertions-core")
	testImplementation("io.kotest.extensions:kotest-extensions-spring:$kotestSpringExtensionVersion")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
		jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

springBoot {
	mainClass.set("com.woopi.safehome.SafehomeApplicationKt")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

sentry {
	includeSourceContext.set(true)
	org.set("woopii")
	projectName.set("safehome-api")
	authToken.set(System.getenv("SENTRY_AUTH_TOKEN"))
}

