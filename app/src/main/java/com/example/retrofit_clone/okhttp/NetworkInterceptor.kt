package com.example.retrofit_clone.okhttp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// 실제 통신을 담당하는 마지막 인터셉터
class NetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println("[MiniOkHttp] 실제 연결 시도: ${request.url}")

        // Java 표준 API인 HttpURLConnection 사용
        val connection = URL(request.url).openConnection() as HttpURLConnection
        connection.requestMethod = request.method

        // 헤더 세팅
        request.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        // 실제 요청 보내기 및 응답 받기
        val responseCode = connection.responseCode
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

        val body = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

        println("[MiniOkHttp] 응답 수신 완료: $responseCode")

        return Response(
            code = responseCode,
            body = body,
            headers = connection.headerFields
        )
    }
}