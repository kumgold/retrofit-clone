package com.example.retrofit_clone.step5.retrofit

// 실제 Retrofit의 Call 인터페이스 단순화 버전
interface MiniCall<T> {
    fun execute(): T // 동기적으로 실행해서 결과 반환
}