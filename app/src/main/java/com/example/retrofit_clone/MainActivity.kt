package com.example.retrofit_clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.retrofit_clone.step1.MiniRetrofit1
import com.example.retrofit_clone.step1.api.MyApi1
import com.example.retrofit_clone.step2.MiniRetrofit2
import com.example.retrofit_clone.step2.api.MyApi2
import com.example.retrofit_clone.step2.okhttp.Interceptor
import com.example.retrofit_clone.step2.okhttp.Response
import com.example.retrofit_clone.step3.MiniRetrofit3
import com.example.retrofit_clone.step3.api.MyApi3
import com.example.retrofit_clone.step3.api.User
import com.example.retrofit_clone.step4.MiniRetrofit4
import com.example.retrofit_clone.step4.api.MyApi4
import com.example.retrofit_clone.step4.api.PostRequest
import com.example.retrofit_clone.step4.api.PostResponse
import com.example.retrofit_clone.step5.MiniRetrofit5
import com.example.retrofit_clone.step5.api.MyApi5
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
            miniRetrofit4Test()
            miniRetrofit5Test()
        }
    }

    private fun miniRetrofit1Test() {
        // Mini Retrofit 객체 생성
        val retrofit = MiniRetrofit1(baseUrl)

        // 인터페이스 구현체 생성 (Dynamic Proxy)
        val apiService = retrofit.create(MyApi1::class.java)

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
        val okHttpClient = com.example.retrofit_clone.step2.okhttp.MiniOkHttpClient(
            interceptors = listOf(loggingInterceptor) // 로깅 인터셉터 장착
        )

        // Retrofit 생성 (이제 단순히 문자열을 반환하는게 아니라 OkHttp를 부릅니다)
        val retrofit = MiniRetrofit2(baseUrl = baseUrl, client = okHttpClient)

        // API 생성 및 호출
        val api = retrofit.create(MyApi2::class.java)
        val result = api.getUser(userId).execute() // 네트워크 요청

        println("[MiniOkHttp] 최종 결과 Body:\n$result")
    }

    private fun miniRetrofit3Test() {
        val client = com.example.retrofit_clone.step3.okhttp.MiniOkHttpClient()

        // Retrofit 생성 (Builder 패턴 사용)
        val retrofit = MiniRetrofit3.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(com.example.retrofit_clone.step3.converter.GsonConverterFactory.create())
            .build()

        val api = retrofit.create(MyApi3::class.java)

        try {
            println("📡 요청 시작...")
            val call = api.getUser("jakewharton") // 유명한 안드로이드 개발자 ID

            val user: User = call.execute() // String이 아니라 User 객체가 나옴!

            println("✅ 변환 성공!")
            println("User Name: ${user.login}")
            println("User ID: ${user.id}")
            println("User Bio: ${user.bio}")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun miniRetrofit4Test() {
        val retrofit = MiniRetrofit4.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com/")
            .client(com.example.retrofit_clone.step4.okhttp.MiniOkHttpClient())
            .addConverterFactory(com.example.retrofit_clone.step4.converter.GsonConverterFactory.create())
            .build() // DefaultCallAdapter는 내부에서 자동 추가됨

        val api = retrofit.create(MyApi4::class.java)

        try {
            println("📮 POST 요청 시작 (글쓰기)...")

            // 1. 보낼 데이터 생성 (객체)
            val newPost = PostRequest(
                title = "MiniRetrofit 만들기",
                body = "직접 구현하니 정말 재밌네요!",
                userId = 1
            )

            // 2. 요청 실행 (내부적으로 객체 -> JSON 변환되어 전송됨)
            val responseCall = api.createPost(newPost)
            val result: PostResponse = responseCall.execute()

            // 3. 결과 확인 (서버가 응답한 JSON -> 객체 변환됨)
            println("✅ POST 성공!")
            println("Created ID: ${result.id}")
            println("Title: ${result.title}")
            println("Body: ${result.body}")

        } catch (e: Exception) {
            println("❌ 에러 발생: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun miniRetrofit5Test() {
        val retrofit = MiniRetrofit5.Builder()
            .baseUrl("https://api.github.com/")
            .client(com.example.retrofit_clone.step5.okhttp.MiniOkHttpClient())
            .addConverterFactory(com.example.retrofit_clone.step5.converter.GsonConverterFactory.create())
            .build()

        val api = retrofit.create(MyApi5::class.java)

        try {
            println("🔍 검색 요청 시작 (Query)...")

            // 1. 첫 번째 호출: ServiceMethod 생성 및 파싱 (약간의 오버헤드 발생)
            val call = api.searchUsers("jakewharton")
            val result = call.execute() // 실제 네트워크 요청

            println("✅ 검색 결과: 총 ${result.total_count}명")
            result.items.forEach { user ->
                println("- [${user.id}] ${user.login}")
            }

            // 2. 두 번째 호출: 캐시된 ServiceMethod 사용 (파싱 과정 생략 -> 매우 빠름)
            println("🔍 재검색 (캐시 사용)...")
            api.searchUsers("kotlin").execute()
            println("✅ 재검색 완료 (더 빠름)")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}