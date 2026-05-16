package com.woopi.safehome.domain.auth.adapter.outbound.persistence

import com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa.UserEntity
import com.woopi.safehome.domain.auth.domain.model.User

object UserEntityMapper {

    fun toModel(entity: UserEntity): User.Data = User.Data(
        id = entity.id!!,
        kakaoId = entity.kakaoId,
        nickname = entity.nickname,
        profileImageUrl = entity.profileImageUrl,
    )

    fun toEntity(user: User.Create): UserEntity = UserEntity(
        kakaoId = user.kakaoId,
        nickname = user.nickname,
        profileImageUrl = user.profileImageUrl,
    )
}
