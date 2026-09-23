package com.woopi.safehome.domain.deed.application.port.outbound

/**
 * 비회원의 하루 사용량. 회원과 달리 셀 기준이 없어 요청 주소를 단서로 쓴다.
 *
 * **셀 수 없으면 `null` 을 돌려준다.** 저장소가 흔들렸다는 뜻이고, 그때 막을지 통과시킬지는
 * 부르는 쪽이 정한다 — 여기서 0 이나 큰 수로 둘러대면 그 판단을 빼앗는다.
 *
 * **세는 것과 늘리는 것을 나누지 않는다.** 나누면 두 요청이 같은 값을 읽고 둘 다 통과한다.
 */
interface AnonymousUsagePort {

    /** 이 주소의 오늘 사용량을 1 늘리고, 늘린 뒤의 값을 돌려준다. */
    fun increaseClientUsage(clientAddress: String): Long?

    /** 비회원 전체의 오늘 사용량을 1 늘리고, 늘린 뒤의 값을 돌려준다. */
    fun increaseTotalUsage(): Long?

    /**
     * 늘렸던 1회를 되돌린다 — 주소가 있으면 그 주소와 전체 모두. 주소가 없었으면 전체만 센 것이므로 전체만.
     *
     * **실패해도 조용히 넘어간다.** 되돌리지 못하면 사용자가 1회를 잃을 뿐이고,
     * 그 때문에 이미 기록된 실패를 흔들면 안 된다.
     */
    fun refund(clientAddress: String?)
}
