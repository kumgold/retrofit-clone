package com.example.retrofit_clone.retrofit

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MiniRetrofit(private val baseUrl: String) {

    // 제네릭 T 타입의 인터페이스를 받아 실제 구현체를 만들어 반환
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {

        // Dynamic Proxy 생성
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // 인터페이스의 메서드가 호출되면 이 코드가 실행됩니다.

                    // 메서드에 붙은 @GET 어노테이션 가져오기
                    val getAnnotation = method.getAnnotation(GET::class.java)

                    if (getAnnotation != null) {
                        // URL 파싱 및 파라미터 바인딩 로직 실행
                        val requestUrl = buildRequestUrl(getAnnotation.value, method, args)

                        // 네트워크 요청을 수행할 Call 객체 반환
                        return object : MiniCall<String> {
                            override fun execute(): String {
                                // 실제 네트워크 통신 대신 로그를 찍습니다.
                                println("[MiniRetrofit] Network Request Sending to: $requestUrl")
                                return "{ \"result\": \"Success\", \"data\": \"Fake Data\" }"
                            }
                        }
                    }

                    throw IllegalArgumentException("알 수 없는 메서드입니다.")
                }
            }
        ) as T
    }

    // URL의 {path} 부분을 실제 인자값으로 교체하는 로직
    private fun buildRequestUrl(endpoint: String, method: Method, args: Array<out Any>?): String {
        var finalUrl = baseUrl + endpoint

        // 메서드의 파라미터들을 순회 (예: @Path("id") id: String)
        val parameterAnnotations = method.parameterAnnotations

        if (args != null) {
            for (i in args.indices) {
                // 각 파라미터에 붙은 어노테이션 확인
                val annotations = parameterAnnotations[i]
                for (annotation in annotations) {
                    if (annotation is Path) {
                        val key = "{${annotation.value}}" // 예: "{id}"
                        val value = args[i].toString()    // 예: "100"
                        finalUrl = finalUrl.replace(key, value)
                    }
                }
            }
        }
        return finalUrl
    }
}