package com.example.retrofit_clone.step7

import com.example.retrofit_clone.step7.adapter.CallAdapter
import com.example.retrofit_clone.step7.adapter.DefaultCallAdapterFactory
import com.example.retrofit_clone.step7.converter.Converter
import com.example.retrofit_clone.step7.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.step7.service.ServiceMethod
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

class MiniRetrofit7(
    private val baseUrl: String,
    private val client: MiniOkHttpClient,
    private val converterFactories: List<Converter.Factory>,
    private val callAdapterFactories: List<CallAdapter.Factory>
) {
    // 메서드별 ServiceMethod를 저장할 캐시
    // ConcurrentHashMap: 멀티 스레드 환경에서도 안전하게 접근 가능한 Map
    private val serviceMethodCache = ConcurrentHashMap<Method, ServiceMethod>()

    class Builder {
        private var baseUrl: String = ""
        private var client: MiniOkHttpClient = MiniOkHttpClient()
        private val converterFactories = ArrayList<Converter.Factory>()
        private val callAdapterFactories = ArrayList<CallAdapter.Factory>()

        fun baseUrl(url: String) = apply { this.baseUrl = url }
        fun client(client: MiniOkHttpClient) = apply { this.client = client }
        fun addConverterFactory(factory: com.example.retrofit_clone.step7.converter.GsonConverterFactory) = apply { converterFactories.add(factory) }
        fun addCallAdapterFactory(factory: CallAdapter.Factory) = apply { callAdapterFactories.add(factory) }

        fun build(): MiniRetrofit7 {
            // 기본 CallAdapterFactory를 항상 추가 (MiniCall 지원용)
            callAdapterFactories.add(DefaultCallAdapterFactory())
            return MiniRetrofit7(baseUrl, client, converterFactories, callAdapterFactories)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        // Dynamic Proxy 생성 (인터페이스 구현체)
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // Object 클래스의 기본 메서드(toString, hashCode 등)는 가로채지 않고 원래대로 실행
                    if (method.declaringClass == Any::class.java) {
                        return method.invoke(this, args)
                    }

                    // 1. ServiceMethod 로드
                    // 캐시에 있으면 가져오고, 없으면 새로 만들어서 가져옴
                    val serviceMethod = loadServiceMethod(method)

                    // 2. 실제 실행 위임
                    // 모든 파싱 작업이 끝난 ServiceMethod에게 인자만 넘겨서 실행시킴
                    return serviceMethod.invoke(args)
                }
            }
        ) as T
    }

    // 캐싱 로직 구현 함수
    private fun loadServiceMethod(method: Method): ServiceMethod {
        // getOrPut: Map에 키(method)가 있으면 값을 반환, 없으면 {} 블록 실행 후 저장하고 반환
        return serviceMethodCache.getOrPut(method) {
            // ServiceMethod 객체 생성 (이때 내부 init 블록에서 무거운 파싱 작업 수행)
            ServiceMethod(baseUrl, client, method, converterFactories, callAdapterFactories)
        }
    }
}