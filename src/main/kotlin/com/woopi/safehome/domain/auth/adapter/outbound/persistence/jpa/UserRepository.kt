package com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long> {

    fun findByKakaoIdAndIsDeletedFalse(kakaoId: Long): UserEntity?

    fun findByIdAndIsDeletedFalse(id: Long): UserEntity?
}
