package com.woopi.safehome.domain.auth.application.port.outbound

import com.woopi.safehome.domain.auth.domain.model.User

interface UserPersistencePort {

    fun findByKakaoId(kakaoId: Long): User.Data?

    fun findById(id: Long): User.Data?

    fun save(user: User.Create): User.Data

    fun deleteById(id: Long)
}
