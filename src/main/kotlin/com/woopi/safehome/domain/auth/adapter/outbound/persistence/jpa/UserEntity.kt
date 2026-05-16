package com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa

import com.woopi.safehome.global.`object`.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(

    @Column(name = "kakao_id", nullable = false, unique = true)
    var kakaoId: Long,

    @Column(name = "nickname", nullable = false)
    var nickname: String,

    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,

) : BaseEntity()
