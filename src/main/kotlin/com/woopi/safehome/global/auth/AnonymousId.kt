package com.woopi.safehome.global.auth

/**
 * 익명 사용자 식별자를 받는다. **인증이 아니다** — 쿠키를 가진 브라우저를 구분할 뿐이다.
 * 이 인자를 받는 엔드포인트는 인증 없이 열리므로, 인증 제약 테스트의 공개 목록에 올려야 한다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnonymousId
