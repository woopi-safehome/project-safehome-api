package com.woopi.safehome.domain.auth.adapter.outbound.persistence

import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserRepository
import com.woopi.safehome.domain.auth.application.port.outbound.UserPersistencePort
import com.woopi.safehome.domain.auth.domain.model.User
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val userRepository: UserRepository,
) : UserPersistencePort {

    override fun findByKakaoId(kakaoId: Long): User.Data? =
        userRepository.findByKakaoIdAndIsDeletedFalse(kakaoId)
            ?.let { UserEntityMapper.toModel(it) }

    override fun findById(id: Long): User.Data? =
        userRepository.findByIdAndIsDeletedFalse(id)
            ?.let { UserEntityMapper.toModel(it) }

    override fun save(user: User.Create): User.Data =
        userRepository.save(UserEntityMapper.toEntity(user))
            .let { UserEntityMapper.toModel(it) }

    override fun deleteById(id: Long) {
        userRepository.findByIdAndIsDeletedFalse(id)?.delete()
    }
}
