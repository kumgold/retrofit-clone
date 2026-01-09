package com.example.retrofit_clone.okhttp

class InterceptorChain(
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val request: Request
) : Interceptor.Chain {

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        // 더 이상 실행할 인터셉터가 없으면 에러 (이론상 NetworkInterceptor가 마지막이라 발생 안 함)
        if (index >= interceptors.size) throw AssertionError()

        // 1. 다음 단계의 체인을 미리 생성
        val nextChain = InterceptorChain(interceptors, index + 1, request)

        // 2. 현재 순서의 인터셉터를 가져옴
        val interceptor = interceptors[index]

        // 3. 현재 인터셉터 실행 (여기서 nextChain을 넘겨줌으로써 연결됨)
        return interceptor.intercept(nextChain)
    }
}