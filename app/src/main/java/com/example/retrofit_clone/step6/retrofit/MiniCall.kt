package com.example.retrofit_clone.step6.retrofit

// 네트워크 요청을 실행하는 단위 객체
interface MiniCall<T> {
    // 동기 실행: 호출한 스레드를 멈추고 결과를 기다림 (Blocking)
    fun execute(): T

    // 비동기 실행: 호출 즉시 리턴되며, 결과는 나중에 Callback으로 알려줌 (Non-Blocking)
    fun enqueue(callback: Callback<T>)

    // 복제: 동일한 요청을 다시 보낼 때 사용 (Retrofit 스펙)
    fun clone(): MiniCall<T>
}