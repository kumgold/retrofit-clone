package com.example.retrofit_clone.miniretrofit

import com.example.retrofit_clone.adapter.CallAdapter
import com.example.retrofit_clone.adapter.DefaultCallAdapterFactory
import com.example.retrofit_clone.converter.Converter
import com.example.retrofit_clone.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.okhttp.Request
import com.example.retrofit_clone.retrofit.*
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MiniRetrofit4(
    private val baseUrl: String,
    private val client: MiniOkHttpClient,
    private val converterFactories: List<Converter.Factory>,
    private val callAdapterFactories: List<CallAdapter.Factory>
) {
    class Builder {
        private var baseUrl: String = ""
        private var client: MiniOkHttpClient = MiniOkHttpClient()
        private val converterFactories = ArrayList<Converter.Factory>()
        private val callAdapterFactories = ArrayList<CallAdapter.Factory>()

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun client(client: MiniOkHttpClient) = apply { this.client = client }
        fun addConverterFactory(factory: Converter.Factory) = apply { converterFactories.add(factory) }
        fun addCallAdapterFactory(factory: CallAdapter.Factory) = apply { callAdapterFactories.add(factory) }

        fun build(): MiniRetrofit4 {
            callAdapterFactories.add(DefaultCallAdapterFactory())
            return MiniRetrofit4(baseUrl, client, converterFactories, callAdapterFactories)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {

                    // 1. HTTP Method 및 URL Endpoint 파싱
                    val getAnno = method.getAnnotation(GET::class.java)
                    val postAnno = method.getAnnotation(POST::class.java)

                    val httpMethod: String
                    var urlEndpoint: String

                    if (getAnno != null) {
                        httpMethod = "GET"
                        urlEndpoint = getAnno.value
                    } else if (postAnno != null) {
                        httpMethod = "POST"
                        urlEndpoint = postAnno.value
                    } else {
                        throw IllegalArgumentException("Method must have @GET or @POST annotation")
                    }

                    // 2. URL Path 치환 (기존 로직)
                    var fullUrl = baseUrl + urlEndpoint
                    method.parameterAnnotations.forEachIndexed { idx, annos ->
                        annos.filterIsInstance<Path>().forEach { path ->
                            fullUrl = fullUrl.replace("{${path.value}}", args?.get(idx).toString())
                        }
                    }

                    // 3. [NEW] Request Body 처리
                    var requestBodyJson: String? = null

                    // 파라미터들 중 @Body가 붙은 녀석을 찾음
                    method.parameterAnnotations.forEachIndexed { idx, annos ->
                        annos.filterIsInstance<Body>().forEach {
                            // Body는 인자가 null이면 안됨
                            val bodyArg = args?.get(idx) ?: throw IllegalArgumentException("@Body parameter cannot be null")

                            // 해당 객체(User 등)를 처리할 Converter 찾기
                            val bodyType = method.genericParameterTypes[idx] // 파라미터 타입 가져옴

                            var converter: Converter<Any, String>? = null
                            for (factory in converterFactories) {
                                val conv = factory.requestBodyConverter(bodyType)
                                if (conv != null) {
                                    converter = conv as Converter<Any, String>
                                    break
                                }
                            }

                            if (converter == null) {
                                throw IllegalArgumentException("Could not locate RequestConverter for $bodyType")
                            }

                            // 객체 -> JSON String 변환 수행
                            requestBodyJson = converter.convert(bodyArg)
                        }
                    }

                    // 4. CallAdapter & Response Converter 찾기 (기존과 동일)
                    val returnType = method.genericReturnType

                    // Adapter 찾기
                    var callAdapter: CallAdapter<Any, Any>? = null
                    for (factory in callAdapterFactories) {
                        val adapter = factory.get(returnType)
                        if (adapter != null) {
                            callAdapter = adapter as CallAdapter<Any, Any>
                            break
                        }
                    }
                    if (callAdapter == null) throw IllegalArgumentException("No CallAdapter for $returnType")

                    // Response Converter 찾기
                    val responseType = callAdapter.responseType()
                    var resConverter: Converter<String, Any>? = null
                    for (factory in converterFactories) {
                        val conv = factory.responseBodyConverter(responseType)
                        if (conv != null) {
                            resConverter = conv as Converter<String, Any>
                            break
                        }
                    }
                    if (resConverter == null) throw IllegalArgumentException("No ResponseConverter for $responseType")


                    // 5. 실행 객체 생성
                    val rawCall = object : MiniCall<Any> {
                        override fun execute(): Any {
                            // [NEW] requestBodyJson을 함께 실어 보냄
                            val request = Request(
                                url = fullUrl,
                                method = httpMethod,
                                body = requestBodyJson // GET이면 null, POST면 JSON 문자열
                            )

                            val response = client.newCall(request).execute()

                            // 응답 변환 (String -> Object)
                            return resConverter.convert(response.body)
                        }
                    }

                    return callAdapter.adapt(rawCall)
                }
            }
        ) as T
    }
}