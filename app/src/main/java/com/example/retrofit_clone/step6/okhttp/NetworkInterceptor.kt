package com.example.retrofit_clone.step6.okhttp

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// [MiniOkHttp] NetworkInterceptor.kt
// 실제 OkHttp: okhttp3.internal.http.CallServerInterceptor
// 실제 OkHttp는 여기서 소켓(Socket)을 열고 Okio를 써서 바이트를 씁니다.
// 아래 코드 블록에서는 편의상 HttpURLConnection을 사용해 이를 흉내 냅니다.
// 체인의 맨 마지막에 위치하여, 실제 인터넷 세상과 만나는 역할을 합니다.
class NetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println("[MiniOkHttp] 실제 연결 시도: ${request.url}")

        // Java 표준 API인 HttpURLConnection 사용
        val connection = URL(request.url).openConnection() as HttpURLConnection

        // HTTP 메서드 설정 (GET, POST...)
        connection.requestMethod = request.method

        // 헤더 세팅 (Request 객체에 담긴 Map을 실제 통신 헤더에 넣음)
        request.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        // Body가 있으면 데이터를 쓴다.
        if (request.body != null) {
            connection.doOutput = true // "나 뭐 보낼거야!" 설정
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8") // 헤더 설정

            // 데이터를 Stream에 쓴다.
            connection.outputStream.use { os ->
                os.write(request.body.toByteArray(Charsets.UTF_8))
                os.flush()
            }
        }

        // --- 여기서 실제 서버로 요청이 날아갑니다. ---

        // 실제 응답 코드 수신 (200, 404...)
        val responseCode = connection.responseCode

        // Body 읽기
        // 성공(2xx)이면 inputStream, 에러면 errorStream을 읽습니다.
        // 실제 OkHttp는 'Source'라는 스트림 객체로 감싸서 줍니다.
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

        // 바이트를 String으로 변환
        val body = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

        println("[MiniOkHttp] 응답 수신 완료: $responseCode")

        // Response 객체에 담아서 반환
        // 이제 거슬러 올라가며 이전 인터셉터들에게 결과를 줍니다.
        return Response(
            code = responseCode,
            body = body,
            headers = connection.headerFields
        )
    }
}