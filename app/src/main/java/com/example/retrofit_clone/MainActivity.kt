package com.example.retrofit_clone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.retrofit_clone.api.MyApi
import com.example.retrofit_clone.mini.MiniRetrofit
import com.example.retrofit_clone.ui.theme.RetrofitCloneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        miniRetrofitTest()
        setContent {
            RetrofitCloneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun miniRetrofitTest() {
        // Mini Retrofit 객체 생성
        val retrofit = MiniRetrofit("https://api.github.com/")

        // 인터페이스 구현체 생성 (Dynamic Proxy)
        val apiService = retrofit.create(MyApi::class.java)

        // 메서드 호출 -> invoke() 실행 -> URL 생성 -> Call 객체 반환
        val call = apiService.getUser("user123")

        // 실행
        val result = call.execute()

        println("✅ 결과: $result")
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
    RetrofitCloneTheme {
        Greeting("Android")
    }
}