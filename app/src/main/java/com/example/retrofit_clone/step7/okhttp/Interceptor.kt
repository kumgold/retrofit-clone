package com.example.retrofit_clone.step7.okhttp

// [MiniOkHttp] Interceptor.kt
// 실제 OkHttp: okhttp3.Interceptor
interface Interceptor {
    // intercept 함수
    // 설명: 요청을 가로채서(intercept) 작업을 수행하고, 결과(Response)를 반환해야 합니다.
    // chain: 다음 단계로 넘어갈 수 있는 열쇠입니다.
    fun intercept(chain: Chain): Response

    // Chain 인터페이스
    // 설명: 인터셉터들이 서로 연결될 수 있도록 하는 고리입니다.
    // 실제 OkHttp: okhttp3.Interceptor.Chain
    interface Chain {
        fun request(): Request // 현재 처리 중인 요청 정보를 확인
        fun proceed(request: Request): Response // "다음 인터셉터에게 일 넘기기" (중요)
    }
}