package com.example.retrofit_clone.step4.okhttp

// [MiniOkHttp] RealInterceptorChain.kt
// 실제 OkHttp: okhttp3.internal.http.RealInterceptorChain
class InterceptorChain(
    private val interceptors: List<Interceptor>, // 전체 인터셉터 목록 (로그 -> 헤더 -> ... -> 네트워크)
    private val index: Int, // 현재 몇 번째 인터셉터를 실행할 차례인지
    private val request: Request // 현재 요청 데이터
) : Interceptor.Chain {

    override fun request(): Request = request

    // 다음 단계로 실행하라는 명령
    override fun proceed(request: Request): Response {
        // 더 이상 실행할 인터셉터가 없으면 에러 (이론상 NetworkInterceptor가 마지막이라 발생 안 함)
        if (index >= interceptors.size) throw AssertionError()

        // [핵심] 다음 단계의 체인을 미리 만듭니다.
        // index + 1을 해서 "다음 타자"를 가리키게 합니다.
        val nextChain = InterceptorChain(interceptors, index + 1, request)

        // 현재 순서의 인터셉터를 가져옴
        val interceptor = interceptors[index]

        // 현재 인터셉터에게 intercept를 지시합니다.
        // 이때 nextChain을 인자로 넘겨주므로,
        // 인터셉터 내부에서 chain.proceed()를 호출하면 위 1번 과정이 다시 반복됩니다.
        return interceptor.intercept(nextChain)
    }
}