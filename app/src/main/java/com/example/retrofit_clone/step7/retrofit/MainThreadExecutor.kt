package com.example.retrofit_clone.step7.retrofit

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

// 안드로이드 메인 스레드에서 코드를 실행시켜주는 실행기
class MainThreadExecutor : Executor {
    // Android의 Handler는 메시지를 Looper(메인 스레드 루프)에게 전달하는 도구입니다.
    // Looper.getMainLooper()를 사용했으므로, 이 핸들러는 메인 스레드와 연결됩니다.
    private val handler = Handler(Looper.getMainLooper())

    // command를 메인 스레드에서 실행하라고 명령하는 함수
    override fun execute(command: Runnable) {
        // handler.post(): 작업을 메인 스레드의 대기열(Message Queue)에 넣습니다.
        // 그러면 메인 스레드가 순서가 되었을 때 이 코드를 실행합니다.
        handler.post(command)
    }
}