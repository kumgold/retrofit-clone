package com.example.retrofit_clone.step7.okhttp

// [MiniOkHttp] Request.kt
// 실제 OkHttp: okhttp3.Request (Builder 패턴 사용)
data class Request(
    val url: String,                     // 요청 보낼 주소 (https://...)
    val method: String = "GET",          // HTTP 메서드 (GET, POST, PUT...)
    val headers: Map<String, String> = emptyMap(), // HTTP 헤더 (User-Agent, Auth 등)
    val body: String? = null             // POST 요청 시 보낼 데이터 (JSON 등)
)

// [MiniOkHttp] Response.kt
// 실제 OkHttp: okhttp3.Response (ResponseBody, Handshake 등 더 많은 정보 포함)
data class Response(
    val code: Int,                       // 응답 코드 (200: 성공, 404: 없음, 500: 서버 에러)
    val body: String,                    // 서버가 준 실제 데이터 (JSON 문자열)
    val headers: Map<String, List<String>> // 서버가 준 헤더 (Set-Cookie, Content-Type 등)
)