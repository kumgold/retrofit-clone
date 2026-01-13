package com.example.retrofit_clone.miniretrofit

import com.example.retrofit_clone.retrofit.GET
import com.example.retrofit_clone.retrofit.MiniCall
import com.example.retrofit_clone.retrofit.Path
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MiniRetrofit1(private val baseUrl: String) {

    // <T> create(service: Class<T>): T
    // 설명: 제네릭 T는 우리가 만든 인터페이스(MyApi) 타입을 의미합니다.
    // Class<T>는 MyApi::class.java 정보를 받습니다.
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {

        // Proxy.newProxyInstance(...)
        // 설명: 자바의 리플렉션 API를 사용해 가짜 객체(Proxy)를 만드는 명령어입니다.
        // 이 함수가 성공적으로 실행되면, MyApi 인터페이스를 구현한 객체가 나타납니다.
        return Proxy.newProxyInstance(
            // service.classLoader
            // 설명: 클래스 로더를 지정합니다.
            // MyApi 인터페이스를 읽어들인 Loader에게 이 가짜 객체도 메모리에 올려달라는 뜻입니다.
            service.classLoader,

            // arrayOf(service)
            // 설명: 이 가짜 객체가 구현해야 할 인터페이스 목록입니다.
            // 여기서는 [MyApi] 하나만 구현하면 됩니다.
            arrayOf(service),

            // InvocationHandler (익명 클래스 또는 람다)
            // 설명: 가장 중요한 가로채기(Intercept) 로직입니다.
            // 사용자가 api.getUser()를 호출할 때마다 실행 흐름이 여기로 점프합니다.
            // proxy: 가짜 객체 본인 / method: 호출된 메서드 정보(getUser) / args: 넘겨진 인자들(["user123"])
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // method.getAnnotation(GET::class.java)
                    // 설명: 호출된 메서드(getUser) 위에 @GET 어노테이션이 붙어있는지 확인합니다.
                    // 리플렉션을 사용해 런타임에 코드를 분석하는 겁니다.
                    val getAnnotation = method.getAnnotation(GET::class.java)

                    if (getAnnotation != null) {
                        // URL 조립
                        // @GET("users/{id}")의 값과 파라미터 "user123"을 합쳐서
                        // "https://api.github.com/users/user123"을 만듭니다.
                        val requestUrl = buildRequestUrl(getAnnotation.value, method, args)

                        // MiniCall 객체 반환 (익명 클래스)
                        // 설명: Retrofit은 결과를 바로 주지 않고, 실행할 수 있는 명령 객체(Call)를 줍니다.
                        // 사용자가 나중에 .execute()를 호출해야 진짜 통신이 일어납니다.
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
        ) as T // 만들어진 Object를 T(MyApi) 타입으로 캐스팅해서 반환합니다.
    }

    // URL의 {path} 부분을 실제 인자값으로 교체하는 로직
    private fun buildRequestUrl(endpoint: String, method: Method, args: Array<out Any>?): String {
        // 초기 URL 설정
        // baseUrl("https://...") + endpoint("users/{id}")를 합칩니다.
        var finalUrl = baseUrl + endpoint

        // method.parameterAnnotations
        // 설명: 메서드의 파라미터들에 붙은 어노테이션들을 '전부' 가져옵니다.
        // 왜 2차원 배열일까요? -> fun test(@Path @NotNull id: String) 처럼
        // 하나의 파라미터에 어노테이션이 여러 개 붙을 수도 있기 때문입니다.
        // 구조: [[1번 파라미터의 어노테이션들], [2번 파라미터의 어노테이션들], ...]
        val parameterAnnotations = method.parameterAnnotations

        // 인자가 하나라도 있다면 루프를 돕니다.
        if (args != null) {
            // 파라미터 개수만큼 반복 (i: 인덱스)
            for (i in args.indices) {
                // i번째 파라미터에 붙은 어노테이션 목록을 가져옴
                val annotations = parameterAnnotations[i]

                // 어노테이션 하나하나 검사
                for (annotation in annotations) {
                    // @Path 어노테이션 확인
                    if (annotation is Path) {
                        // 치환할 키 찾기
                        // annotation.value가 "id"라면 key는 "{id}"가 됩니다.
                        val key = "{${annotation.value}}" // 예: "{id}"

                        // 실제 값 가져오기
                        // args[i]에는 사용자가 넘긴 "user123"이 들어있습니다.
                        val value = args[i].toString()    // 예: "user123"

                        // 문자열 교체
                        // URL상의 "{id}"를 "user123"으로 바꿔치기합니다.
                        finalUrl = finalUrl.replace(key, value)
                    }
                }
            }
        }
        // 완성된 URL 반환 (예: https://api.github.com/users/user123)
        return finalUrl
    }
}