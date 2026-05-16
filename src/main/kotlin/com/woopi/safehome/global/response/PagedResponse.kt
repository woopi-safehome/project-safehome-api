package com.woopi.safehome.global.response

data class PagedResponse<T>(
    val items: List<T>,
    val pagination: PaginationInfo,
)
