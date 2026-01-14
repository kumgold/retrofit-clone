package com.example.retrofit_clone.miniretrofit

import com.example.retrofit_clone.adapter.CallAdapter
import com.example.retrofit_clone.adapter.DefaultCallAdapterFactory
import com.example.retrofit_clone.converter.Converter
import com.example.retrofit_clone.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.okhttp.Request
import com.example.retrofit_clone.retrofit.GET
import com.example.retrofit_clone.retrofit.MiniCall
import com.example.retrofit_clone.retrofit.Path
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

// Retrofit 클래스: 네트워크 통신을 위한 설정값들을 모아둔 컨테이너이자, API 구현체를 생성하는 Factory 입니다.
class MiniRetrofit3(
    private val baseUrl: String,  // 통신할 서버의 기본 URL (예: https://api.github.com/)
    private val client: MiniOkHttpClient,  // 실제 네트워크 요청을 수행할 엔진 (OkHttp)
    private val converterFactories: List<Converter.Factory>,  // 데이터 Converter Factory 목록 (예: GsonConverterFactory)
    private val callAdapterFactories: List<CallAdapter.Factory> // 반환 타입 Adapter Factory 목록 (예: DefaultCallAdapterFactory)
) {

    // [Builder 패턴]
    // 복잡한 생성자 매개변수를 단계별로 설정하여 객체를 생성하기 위한 도우미 클래스입니다.
    class Builder {
        private var baseUrl: String = ""  // 기본 URL 저장 변수
        private var client: MiniOkHttpClient = MiniOkHttpClient()  // HTTP 클라이언트 (기본값 설정)
        private val converterFactories = ArrayList<Converter.Factory>()  // Converter Factory를 담을 리스트
        private val callAdapterFactories = ArrayList<CallAdapter.Factory>()  // Adapter Factory를 담을 리스트

        // Base URL 설정 메서드 (체이닝을 위해 this 반환)
        fun baseUrl(url: String) = apply { this.baseUrl = url }

        // OkHttp 클라이언트 교체 메서드
        fun client(client: MiniOkHttpClient) = apply { this.client = client }

        // Converter Factory 추가 (예: Gson)
        fun addConverterFactory(factory: Converter.Factory) = apply { converterFactories.add(factory) }

        // CallAdapter Factory 추가 (예: RxJava, Coroutines 등)
        fun addCallAdapterFactory(factory: CallAdapter.Factory) = apply { callAdapterFactories.add(factory) }

        // 최종적으로 MiniRetrofit3 객체를 생성하는 메서드
        fun build(): MiniRetrofit3 {
            // [중요] 기본 CallAdapter(DefaultCallAdapterFactory)는 사용자가 추가하지 않아도 항상 마지막에 넣어줍니다.
            // 그래야 기본적인 MiniCall<T> 반환 타입을 처리할 수 있기 때문입니다.
            callAdapterFactories.add(DefaultCallAdapterFactory())

            // 준비된 설정값들로 본체를 생성하여 반환
            return MiniRetrofit3(baseUrl, client, converterFactories, callAdapterFactories)
        }
    }

    // 인터페이스(Class<T>)를 받아서, 실제 동작하는 구현체(T)를 런타임에 생성해줍니다.
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        // 자바의 Proxy.newProxyInstance를 사용해 동적 프록시 객체를 생성합니다.
        return Proxy.newProxyInstance(
            service.classLoader,  // 인터페이스를 로드할 클래스 로더 지정
            arrayOf(service), // 구현할 인터페이스 목록 (여기서는 service 하나)

            // InvocationHandler: 메서드가 호출되었을 때 실행될 로직을 정의하는 익명 클래스
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // --- [Step 1] 어노테이션 파싱 및 URL 조립 ---

                    // 호출된 메서드에 @GET 어노테이션이 붙어있는지 확인합니다.
                    val getAnno = method.getAnnotation(GET::class.java)
                        ?: throw IllegalArgumentException("GET annotation not found")

                    // 기본 URL + 상대 경로 (예: "https://api.github.com/" + "users/{id}")
                    var url = baseUrl + getAnno.value

                    // 파라미터 어노테이션(@Path)을 확인하여 URL의 {key}를 실제 값으로 치환합니다.
                    method.parameterAnnotations.forEachIndexed { idx, annos ->
                        // 해당 파라미터의 어노테이션 중 Path 타입인 것만 필터링
                        annos.filterIsInstance<Path>().forEach { path ->
                            // URL의 "{id}" 부분을 args[idx] (예: "user123")로 교체
                            url = url.replace("{${path.value}}", args?.get(idx).toString())
                        }
                    }

                    // --- [Step 2] CallAdapter 찾기 (반환 타입 처리기) ---

                    // 메서드의 리턴 타입(Generic)을 가져옵니다. (예: MiniCall<User>)
                    val returnType = method.genericReturnType
                    var callAdapter: CallAdapter<Any, Any>? = null

                    // 등록된 Adapter Factory들을 하나씩 순회합니다.
                    for (factory in callAdapterFactories) {
                        // 앞에서 가져온 메서드의 리턴 타입을 처리할 수 있는 Adapter인지 확인합니다.
                        val adapter = factory.get(returnType)
                        if (adapter != null) {
                            // 찾았으면 캐스팅하고 루프 종료
                            callAdapter = adapter as CallAdapter<Any, Any>
                            break
                        }
                    }

                    // 끝까지 못 찾으면 에러 발생 (보통 리턴 타입이 잘못되었거나 Factory 등록 누락 시 발생)
                    if (callAdapter == null) {
                        throw IllegalArgumentException("Could not locate CallAdapter for $returnType")
                    }

                    // --- [Step 3] Converter 찾기 (데이터 변환기) ---

                    // CallAdapter가 알려준 '실제 데이터 타입'을 가져옵니다. (예: MiniCall<User> -> User)
                    val responseType = callAdapter.responseType()
                    var converter: Converter<String, Any>? = null

                    // 등록된 Converter Factory들을 순회합니다. (예: GsonConverterFactory)
                    for (factory in converterFactories) {
                        // 앞에서 가져온 실제 데이터 타입을 처리할 수 있는 Converter인지 확인합니다.
                        val conv = factory.responseBodyConverter(responseType)
                        if (conv != null) {
                            // 찾았으면 캐스팅하고 루프 종료
                            converter = conv as Converter<String, Any>
                            break
                        }
                    }

                    // Converter를 못 찾으면 에러 발생
                    if (converter == null) {
                        throw IllegalArgumentException("Could not locate Converter for $responseType")
                    }

                    // --- [Step 4] 실행 객체(Raw Call) 생성 ---

                    // 실제 네트워크 요청을 수행하고, 변환까지 담당하는 익명 객체를 만듭니다.
                    val rawCall = object : MiniCall<Any> {
                        override fun execute(): Any {
                            // 완성된 URL로 Request 객체 생성
                            val request = Request(url, "GET")

                            // OkHttp 클라이언트에게 요청 위임 (동기 실행)
                            val response = client.newCall(request).execute()

                            // [핵심] Converter를 사용해 JSON String -> User 객체로 변환하여 반환
                            return converter.convert(response.body)
                        }
                    }

                    // --- [Step 5] 최종 반환 ---

                    // CallAdapter에게 포장(Adapt)을 맡깁니다.
                    // DefaultCallAdapter라면 rawCall을 그대로 반환하지만,
                    // RxJava라면 Observable 등으로 감싸서 반환하게 됩니다.
                    return callAdapter.adapt(rawCall)
                }
            }
        ) as T  // 생성된 프록시 객체를 제네릭 T 타입으로 캐스팅하여 반환
    }
}