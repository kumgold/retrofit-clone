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

class MiniRetrofit3(
    private val baseUrl: String,
    private val client: MiniOkHttpClient,
    // 여러 개의 공장을 가질 수 있음
    private val converterFactories: List<Converter.Factory>,
    private val callAdapterFactories: List<CallAdapter.Factory>
) {

    // Builder 패턴 추가 (편리한 생성을 위해)
    class Builder {
        private var baseUrl: String = ""
        private var client: MiniOkHttpClient = MiniOkHttpClient()
        private val converterFactories = ArrayList<Converter.Factory>()
        private val callAdapterFactories = ArrayList<CallAdapter.Factory>()

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun client(client: MiniOkHttpClient) = apply { this.client = client }
        fun addConverterFactory(factory: Converter.Factory) = apply { converterFactories.add(factory) }
        fun addCallAdapterFactory(factory: CallAdapter.Factory) = apply { callAdapterFactories.add(factory) }

        fun build(): MiniRetrofit3 {
            // 기본 어댑터는 항상 마지막에 추가
            callAdapterFactories.add(DefaultCallAdapterFactory())
            return MiniRetrofit3(baseUrl, client, converterFactories, callAdapterFactories)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // 메서드 정보 파싱 (어노테이션 등 - 기존 로직 유지)
                    val getAnno = method.getAnnotation(GET::class.java)
                        ?: throw IllegalArgumentException("GET annotation not found")

                    var url = baseUrl + getAnno.value
                    method.parameterAnnotations.forEachIndexed { idx, annos ->
                        annos.filterIsInstance<Path>().forEach { path ->
                            url = url.replace("{${path.value}}", args?.get(idx).toString())
                        }
                    }

                    // CallAdapter 찾기
                    // 메서드의 리턴 타입(MiniCall<User>)을 처리할 수 있는 어댑터를 찾음
                    val returnType = method.genericReturnType
                    var callAdapter: CallAdapter<Any, Any>? = null
                    for (factory in callAdapterFactories) {
                        val adapter = factory.get(returnType)
                        if (adapter != null) {
                            callAdapter = adapter as CallAdapter<Any, Any>
                            break
                        }
                    }

                    if (callAdapter == null) {
                        throw IllegalArgumentException("Could not locate CallAdapter for $returnType")
                    }

                    // Converter 찾기
                    // CallAdapter가 알려준 실제 데이터 타입(User)을 변환할 컨버터를 찾음
                    val responseType = callAdapter.responseType()

                    var converter: Converter<String, Any>? = null
                    for (factory in converterFactories) {
                        val conv = factory.responseBodyConverter(responseType)
                        if (conv != null) {
                            converter = conv as Converter<String, Any>
                            break
                        }
                    }

                    if (converter == null) {
                        throw IllegalArgumentException("Could not locate Converter for $responseType")
                    }

                    // Raw Call 생성 (String을 반환하는 기본 Call)
                    val rawCall = object : MiniCall<Any> {
                        override fun execute(): Any {
                            val request = Request(url, "GET")
                            val response = client.newCall(request).execute()

                            // 여기서 Converter가 String -> Object로 변환!
                            return converter.convert(response.body)
                        }
                    }

                    // CallAdapter로 감싸서 반환
                    // DefaultCallAdapter라면 rawCall을 그대로 반환하지만,
                    // RxJavaAdapter라면 Observable.from(rawCall) 같은 짓을 함
                    return callAdapter.adapt(rawCall)
                }
            }
        ) as T
    }
}