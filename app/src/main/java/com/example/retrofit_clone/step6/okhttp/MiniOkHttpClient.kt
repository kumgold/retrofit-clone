package com.example.retrofit_clone.step6.okhttp

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// [Dispatcher] 스레드 풀 관리자
// 수많은 요청이 들어와도 정해진 개수의 스레드 안에서 효율적으로 처리합니다.
class Dispatcher {
    // 최대 64개의 스레드를 가진 풀(Pool)을 생성합니다. (실제 OkHttp 기본값)
    // 즉, 동시에 최대 64개의 네트워크 요청을 날릴 수 있습니다.
    private val executorService: ExecutorService = Executors.newFixedThreadPool(64)

    // 요청(AsyncCall)을 받아서 스레드 풀에 던집니다.
    fun enqueue(call: MiniOkHttpClient.Call.AsyncCall) {
        // execute(): 남는 스레드를 하나 골라서 작업(call)을 실행하라고 요청합니다.
        executorService.execute(call)
    }
}

// [MiniOkHttp] MiniOkHttpClient.kt
// 실제 OkHttp: okhttp3.OkHttpClient
// 이 클래스는 설정을 담는 컨테이너 역할을 합니다.
class MiniOkHttpClient(
    // 사용자가 추가한 인터셉터들 (예: 로깅, 인증 토큰 추가 등)
    val interceptors: List<Interceptor> = emptyList()
) {
    // 클라이언트는 하나의 Dispatcher를 가집니다.
    val dispatcher = Dispatcher()

    // 요청을 실행할 객체를 생성하는 팩토리 메서드
    fun newCall(request: Request): Call {
        return Call(this, request)
    }

    // [내부용 콜백] OkHttp가 통신 끝나고 Retrofit에게 알려줄 때 사용
    interface Callback {
        fun onFailure(call: Call, e: Exception)
        fun onResponse(call: Call, response: Response)
    }

    // 실제 OkHttp: okhttp3.RealCall
    // 실제 실행 로직을 담당하는 내부 클래스
    class Call(private val client: MiniOkHttpClient, private val request: Request) {
        // 동기 실행
        fun execute(): Response {
            return getResponseWithInterceptorChain()
        }

        // 비동기 실행
        fun enqueue(responseCallback: Callback) {
            // 1. 실제 작업을 수행할 Runnable 객체(AsyncCall)를 만듭니다.
            val asyncCall = AsyncCall(responseCallback)

            // 2. Dispatcher에게 백그라운드에서 실행해 달라고 요청합니다.
            // 이 함수는 즉시 리턴되므로 메인 스레드는 멈추지 않습니다.
            client.dispatcher.enqueue(asyncCall)
        }

        // [작업자] 실제 백그라운드 스레드에서 돌아갈 코드 덩어리
        inner class AsyncCall(private val responseCallback: Callback) : Runnable {
            override fun run() {
                // --- 여기서부터는 백그라운드 스레드입니다! ---
                try {
                    // 1. 인터셉터 체인을 통해 서버와 통신하고 응답을 받습니다. (시간이 오래 걸림)
                    val response = getResponseWithInterceptorChain()

                    // 2. 성공했으면 콜백을 호출합니다.
                    responseCallback.onResponse(this@Call, response)
                } catch (e: Exception) {
                    // 3. 실패했으면 에러 콜백을 호출합니다.
                    responseCallback.onFailure(this@Call, e)
                }
            }
        }

        // 인터셉터 체인 조립 및 실행
        private fun getResponseWithInterceptorChain(): Response {
            val allInterceptors = ArrayList<Interceptor>()
            allInterceptors.addAll(client.interceptors)
            allInterceptors.add(NetworkInterceptor())

            val chain = InterceptorChain(allInterceptors, 0, request)
            return chain.proceed(request)
        }
    }
}