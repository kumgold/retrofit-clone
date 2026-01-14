package com.example.retrofit_clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.retrofit_clone.adapter.DefaultCallAdapterFactory
import com.example.retrofit_clone.api.MyApi
import com.example.retrofit_clone.api.User
import com.example.retrofit_clone.converter.GsonConverterFactory
import com.example.retrofit_clone.okhttp.Interceptor
import com.example.retrofit_clone.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.miniretrofit.MiniRetrofit2
import com.example.retrofit_clone.okhttp.Response
import com.example.retrofit_clone.miniretrofit.MiniRetrofit1
import com.example.retrofit_clone.miniretrofit.MiniRetrofit3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val baseUrl = "https://api.github.com/"
    private val userId = "user123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        miniRetrofit1Test()

        CoroutineScope(Dispatchers.IO).launch {
            miniRetrofit2Test()
            miniRetrofit3Test()
        }
    }

    private fun miniRetrofit1Test() {
        // Mini Retrofit 객체 생성
        val retrofit = MiniRetrofit1(baseUrl)

        // 인터페이스 구현체 생성 (Dynamic Proxy)
        val apiService = retrofit.create(MyApi::class.java)

        // 메서드 호출 -> invoke() 실행 -> URL 생성 -> Call 객체 반환
        val call = apiService.getUser(userId)

        // 실행
        val result = call.execute()

        println("[MiniRetrofit] 결과: $result")
    }

    private fun miniRetrofit2Test() {
        val loggingInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                val t1 = System.nanoTime()
                println("[MiniOkHttp] 요청 시작: ${request.method} ${request.url}")

                // 다음 단계로 진행 (proceed)
                val response = chain.proceed(request)

                val t2 = System.nanoTime()
                println("[MiniOkHttp] 응답 도착: ${response.code} (걸린 시간: ${(t2 - t1) / 1e6}ms)")
                return response
            }
        }

        // OkHttp 클라이언트 생성 (엔진 조립)
        val okHttpClient = MiniOkHttpClient(
            interceptors = listOf(loggingInterceptor) // 로깅 인터셉터 장착
        )

        // Retrofit 생성 (이제 단순히 문자열을 반환하는게 아니라 OkHttp를 부릅니다)
        val retrofit = MiniRetrofit2(baseUrl = baseUrl, client = okHttpClient)

        // API 생성 및 호출
        val api = retrofit.create(MyApi::class.java)
        val result = api.getUser(userId).execute() // 네트워크 요청

        println("[MiniOkHttp] 최종 결과 Body:\n$result")
    }

    private fun miniRetrofit3Test() {
        val client = MiniOkHttpClient()

        // Retrofit 생성 (Builder 패턴 사용)
        val retrofit = MiniRetrofit3.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(MyApi::class.java)

        try {
            println("📡 요청 시작...")
            val call = api.getUser2("jakewharton") // 유명한 안드로이드 개발자 ID

            val user: User = call.execute() // String이 아니라 User 객체가 나옴!

            println("✅ 변환 성공!")
            println("User Name: ${user.login}")
            println("User ID: ${user.id}")
            println("User Bio: ${user.bio}")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}