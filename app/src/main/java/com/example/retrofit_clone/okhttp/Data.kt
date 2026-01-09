package com.example.retrofit_clone.okhttp

// 요청 객체
data class Request(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

// 응답 객체
data class Response(
    val code: Int,
    val body: String,
    val headers: Map<String, List<String>>
)