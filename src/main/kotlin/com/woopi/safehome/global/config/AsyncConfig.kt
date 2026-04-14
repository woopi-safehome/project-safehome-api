package com.woopi.safehome.global.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableAsync
class AsyncConfig : WebMvcConfigurer {

    override fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
        configurer.setDefaultTimeout(300_000) // 5분 타임아웃
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
    }

}