package com.example.retrofit_clone.step2

import com.example.retrofit_clone.step2.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.step2.okhttp.Request
import com.example.retrofit_clone.step2.retrofit.GET
import com.example.retrofit_clone.step2.retrofit.MiniCall
import com.example.retrofit_clone.step2.retrofit.Path
import java.lang.reflect.Proxy

class MiniRetrofit2(
    private val baseUrl: String,
    private val client: MiniOkHttpClient
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service)
        ) { _, method, args ->
            val getAnno = method.getAnnotation(GET::class.java) ?: throw IllegalArgumentException("No GET annotation")

            var url = baseUrl + getAnno.value
            method.parameterAnnotations.forEachIndexed { idx, annos ->
                annos.filterIsInstance<Path>().forEach { path ->
                    url = url.replace("{${path.value}}", args?.get(idx).toString())
                }
            }

            // OkHttp Request 생성 -> Call 실행 -> 결과 반환
            return@newProxyInstance object : MiniCall<String> {
                override fun execute(): String {
                    // Request 객체 생성
                    // 이제 진짜 통신 준비를 합니다. URL과 메서드 방식을 담습니다.
                    val request = Request(url = url, method = "GET")

                    // OkHttp에게 위임
                    // "야 엔진아, 이 요청서대로 서버에 다녀와"라고 시킵니다.
                    val response = client.newCall(request).execute()

                    // 결과 반환
                    // 서버에서 온 응답의 body(JSON 문자열)만 꺼내서 사용자에게 줍니다.
                    return response.body
                }
            }
        } as T
    }
}