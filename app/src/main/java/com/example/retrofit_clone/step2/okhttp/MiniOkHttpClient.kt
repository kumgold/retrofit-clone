package com.example.retrofit_clone.step2.okhttp

// [MiniOkHttp] MiniOkHttpClient.kt
// 실제 OkHttp: okhttp3.OkHttpClient
// 이 클래스는 설정을 담는 컨테이너 역할을 합니다.
class MiniOkHttpClient(
    // 사용자가 추가한 인터셉터들 (예: 로깅, 인증 토큰 추가 등)
    val interceptors: List<Interceptor> = emptyList()
) {
    // 요청을 실행할 객체를 생성하는 팩토리 메서드
    fun newCall(request: Request): Call {
        return Call(this, request)
    }

    // 실제 OkHttp: okhttp3.RealCall
    // 실제 실행 로직을 담당하는 내부 클래스
    class Call(private val client: MiniOkHttpClient, private val request: Request) {
        fun execute(): Response {
            // 전체 인터셉터 리스트 구성
            // 사용자가 넣은 인터셉터 뒤에, 시스템이 필수적으로 해야 할 작업들을 붙입니다.
            val allInterceptors = ArrayList<Interceptor>()

            // 사용자 정의 인터셉터 (예: LoggingInterceptor) 먼저 추가
            allInterceptors.addAll(client.interceptors)

            // 실제 OkHttp는 여기에 RetryAndFollowUpInterceptor, BridgeInterceptor 등을 추가합니다.

            // [필수] 네트워크 인터셉터 추가
            // 이게 없으면 실제 통신이 안 일어나고 뺑뺑이만 돕니다. 무조건 마지막에 넣어야 합니다.
            allInterceptors.add(NetworkInterceptor())

            // 체인 시동 걸기
            // index 0번(첫 번째 인터셉터)부터 시작하도록 체인을 만듭니다.
            val chain = InterceptorChain(allInterceptors, 0, request)

            // proceed()를 호출하면 도미노처럼 인터셉터들이 실행되고,
            // 마지막에 NetworkInterceptor가 서버 갔다가 돌아오면서 결과를 반환합니다.
            return chain.proceed(request)
        }
    }
}