package com.example.retrofit_clone.step6.retrofit

// 비동기 통신 결과를 받기 위한 콜백 인터페이스
interface Callback<T> {
    // 통신 성공 시 호출됨 (서버가 응답을 줬을 때)
    // call: 요청했던 Call 객체, response: 변환된 결과 데이터
    fun onResponse(call: MiniCall<T>, response: T)
    // 통신 실패 시 호출됨 (네트워크 끊김, 타임아웃, 파싱 에러 등)
    // t: 발생한 예외 객체
    fun onFailure(call: MiniCall<T>, t: Throwable)
}