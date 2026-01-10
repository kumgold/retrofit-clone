package com.example.retrofit_clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.retrofit_clone.api.MyApi
import com.example.retrofit_clone.okhttp.Interceptor
import com.example.retrofit_clone.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.okhttp.MiniRetrofit2
import com.example.retrofit_clone.okhttp.Response
import com.example.retrofit_clone.retrofit.MiniRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val baseUrl = "https://api.github.com/"
    private val userId = "user123"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        miniRetrofitTest()

        CoroutineScope(Dispatchers.IO).launch {
            miniOkHttpTest()
        }
    }

    private fun miniRetrofitTest() {
        // Mini Retrofit 객체 생성
        val retrofit = MiniRetrofit(baseUrl)

        // 인터페이스 구현체 생성 (Dynamic Proxy)
        val apiService = retrofit.create(MyApi::class.java)

        // 메서드 호출 -> invoke() 실행 -> URL 생성 -> Call 객체 반환
        val call = apiService.getUser(userId)

        // 실행
        val result = call.execute()

        println("[MiniRetrofit] 결과: $result")
    }

    private fun miniOkHttpTest() {
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
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        Greeting("Android")
    }
}