package com.example.retrofit_clone.step7.retrofit

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 네트워크 요청을 실행하는 단위 객체
interface MiniCall<T> {
    // 동기 실행: 호출한 스레드를 멈추고 결과를 기다림 (Blocking)
    fun execute(): T

    // 비동기 실행: 호출 즉시 리턴되며, 결과는 나중에 Callback으로 알려줌 (Non-Blocking)
    fun enqueue(callback: Callback<T>)

    // 복제: 동일한 요청을 다시 보낼 때 사용 (Retrofit 스펙)
    fun clone(): MiniCall<T>
}

// MiniCall<T> 클래스에 await() 기능을 추가합니다.
// 이 함수 안에서는 'this'가 곧 MiniCall 객체입니다.
suspend fun <T> MiniCall<T>.await(): T {

    // 1. 여기서 코루틴이 일시 정지(Suspend) 상태로 들어갑니다.
    // continuation: "재생 버튼"입니다. 나중에 이걸 누르면 멈췄던 코드가 다시 돌아갑니다.
    return suspendCancellableCoroutine { continuation ->

        // 2. 'this'(즉, MiniCall 객체)를 사용하여 비동기 요청을 시작합니다.
        // 이 enqueue 메서드는 즉시 실행되고 바로 다음 줄로 넘어가지 않습니다.
        // (정확히는 enqueue는 비동기라 바로 리턴되지만,
        //  suspendCancellableCoroutine이 리턴을 막고 기다리고 있는 상태입니다.)
        this.enqueue(object : Callback<T> {

            // 3. [성공 시] 서버에서 응답을 받습니다.
            override fun onResponse(call: MiniCall<T>, response: T) {
                // "재생 버튼(continuation)"을 누르면서 결과값(response)을 전달합니다.
                // -> 멈춰있던 코루틴이 깨어나고, await() 함수의 리턴값으로 response가 나갑니다.
                continuation.resume(response)
            }

            // 4. [실패 시] 에러가 났습니다.
            override fun onFailure(call: MiniCall<T>, t: Throwable) {
                // "재생 버튼"을 누르는데, 이번엔 에러(Exception)를 던지면서 깨웁니다.
                // -> 코루틴이 깨어나면서 try-catch 문으로 에러가 잡힙니다.
                continuation.resumeWithException(t)
            }
        })

        // 5. [취소 처리] 만약 사용자가 기다리다가 화면을 나가버렸다면?
        continuation.invokeOnCancellation {
            try {
                // 네트워크 요청 취소 (MiniCall에 cancel 기능이 있다면 호출)
                // this.cancel()
                println("✋ 코루틴 취소됨 -> 네트워크 요청도 취소 처리")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}