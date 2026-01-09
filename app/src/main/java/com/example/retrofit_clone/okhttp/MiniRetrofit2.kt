package com.example.retrofit_clone.okhttp

import com.example.retrofit_clone.retrofit.GET
import com.example.retrofit_clone.retrofit.MiniCall
import com.example.retrofit_clone.retrofit.Path

class MiniRetrofit2(
    private val baseUrl: String,
    private val client: MiniOkHttpClient
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        return java.lang.reflect.Proxy.newProxyInstance(
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
                    val request = Request(url = url, method = "GET")
                    val response = client.newCall(request).execute() // OkHttp에게 위임
                    return response.body
                }
            }
        } as T
    }
}