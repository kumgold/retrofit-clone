package com.example.retrofit_clone.okhttp

class MiniOkHttpClient(
    val interceptors: List<Interceptor> = emptyList()
) {
    fun newCall(request: Request): Call {
        return Call(this, request)
    }

    // 통신을 실행하는 객체
    class Call(private val client: MiniOkHttpClient, private val request: Request) {
        fun execute(): Response {
            // 사용자 인터셉터 + (필수) 네트워크 인터셉터 합치기
            val allInterceptors = ArrayList<Interceptor>()
            allInterceptors.addAll(client.interceptors)
            allInterceptors.add(NetworkInterceptor()) // 마지막엔 무조건 네트워크 연결

            // 체인 시작 (인덱스 0부터)
            val chain = InterceptorChain(allInterceptors, 0, request)
            return chain.proceed(request)
        }
    }
}