package com.woopi.safehome.domain.auth.adapter.outbound.persistence.jpa

import com.woopi.safehome.global.`object`.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "user_devices",
    indexes = [Index(name = "idx_user_devices_user_id", columnList = "user_id")],
)
class UserDeviceEntity(

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "fcm_token", nullable = false, unique = true, length = 512)
    var fcmToken: String,

) : BaseEntity()
