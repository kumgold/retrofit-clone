# Retrofit & OkHttp: 바닥부터 구현하며 원리 파헤치기
안드로이드 개발의 필수 라이브러리인 Retrofit과 OkHttp.
단순히 사용하는 것을 넘어, "도대체 내부에서 어떻게 동작하길래 인터페이스만으로 통신이 되는가?"
에 대한 궁금증을 해소하기 위해 MiniRetrofit과 MiniOkHttp를 바닥부터 직접 구현해봅니다.

---

## 📚 목차
1. [아키텍처 및 OSI 7계층 비유](#1-아키텍처-및-osi-7계층-비유)
2. [Chapter 1: Mini OkHttp 구현](#2-chapter-1-mini-okhttp-구현-엔진-만들기)
3. [Chapter 2: Mini Retrofit 구현](#3-chapter-2-mini-retrofit-구현-설계자-만들기)
4. [Chapter 3: 통합 테스트](#4-chapter-3-통합-테스트-android-ui)
5. [심화 분석: 코드 한 줄 한 줄 뜯어보기](#5-심화-분석-코드-한-줄-한-줄-뜯어보기)

## 1. 아키텍처 및 OSI 7계층 비유

이 두 라이브러리는 **Application Layer**(7계층)에서 동작하지만, 역할은 명확히 나뉩니다.

🧱 OSI 계층별 역할 매핑
- Retrofit (Layer 6~7 담당): 데이터의 형태(JSON ↔ 객체)를 변환하고, 인터페이스를 통해 통신 규격을 정의합니다.
- OkHttp (Layer 5~7 담당): 실제 연결(Connection)을 맺고, 유지하고(Pooling), 데이터를 실어 나릅니다.
- HttpUrlConnection / Socket (Layer 4 담당): 실제 TCP/IP 통신의 진입점입니다.

## 2. Chapter 1: Mini OkHttp 구현
OkHttp의 핵심은 Request/Response 모델과 Interceptor Chain 입니다.

### 2-1. 데이터 모델
```kotlin
// [MiniOkHttp] Request.kt
// 실제 OkHttp: okhttp3.Request (Builder 패턴 사용)
data class Request(
    val url: String,                     // 요청 보낼 주소 (https://...)
    val method: String = "GET",          // HTTP 메서드 (GET, POST, PUT...)
    val headers: Map<String, String> = emptyMap(), // HTTP 헤더 (User-Agent, Auth 등)
    val body: String? = null             // POST 요청 시 보낼 데이터 (JSON 등)
)

// [MiniOkHttp] Response.kt
// 실제 OkHttp: okhttp3.Response (ResponseBody, Handshake 등 더 많은 정보 포함)
data class Response(
    val code: Int,                       // 응답 코드 (200: 성공, 404: 없음, 500: 서버 에러)
    val body: String,                    // 서버가 준 실제 데이터 (JSON 문자열)
    val headers: Map<String, List<String>> // 서버가 준 헤더 (Set-Cookie, Content-Type 등)
)
```

### 2-2. Interceptor 구조
```kotlin
// [MiniOkHttp] Interceptor.kt
// 실제 OkHttp: okhttp3.Interceptor
interface Interceptor {
    // intercept 함수
    // 설명: 요청을 가로채서(intercept) 작업을 수행하고, 결과(Response)를 반환해야 합니다.
    // chain: 다음 단계로 넘어갈 수 있는 열쇠입니다.
    fun intercept(chain: Chain): Response

    // Chain 인터페이스
    // 설명: 인터셉터들이 서로 연결될 수 있도록 하는 고리입니다.
    // 실제 OkHttp: okhttp3.Interceptor.Chain
    interface Chain {
        fun request(): Request // 현재 처리 중인 요청 정보를 확인
        fun proceed(request: Request): Response // "다음 인터셉터에게 일 넘기기" (중요)
    }
}
```

### 2-3. 실제 네트워크 연결
체인의 가장 마지막에서 실제 HttpUrlConnection을 수행합니다.
```kotlin
// [MiniOkHttp] NetworkInterceptor.kt
// 실제 OkHttp: okhttp3.internal.http.CallServerInterceptor
// 실제 OkHttp는 여기서 소켓(Socket)을 열고 Okio를 써서 바이트를 씁니다.
// 아래 코드 블록에서는 편의상 HttpURLConnection을 사용해 이를 흉내 냅니다.
// 체인의 맨 마지막에 위치하여, 실제 인터넷 세상과 만나는 역할을 합니다.
class NetworkInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println("[MiniOkHttp] 실제 연결 시도: ${request.url}")

        // Java 표준 API인 HttpURLConnection 사용
        val connection = URL(request.url).openConnection() as HttpURLConnection

        // HTTP 메서드 설정 (GET, POST...)
        connection.requestMethod = request.method

        // 헤더 세팅 (Request 객체에 담긴 Map을 실제 통신 헤더에 넣음)
        request.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        // --- 여기서 실제 서버로 요청이 날아갑니다. ---

        // 실제 응답 코드 수신 (200, 404...)
        val responseCode = connection.responseCode

        // Body 읽기
        // 성공(2xx)이면 inputStream, 에러면 errorStream을 읽습니다.
        // 실제 OkHttp는 'Source'라는 스트림 객체로 감싸서 줍니다.
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream

        // 바이트를 String으로 변환
        val body = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

        println("[MiniOkHttp] 응답 수신 완료: $responseCode")

        // Response 객체에 담아서 반환
        // 이제 거슬러 올라가며 이전 인터셉터들에게 결과를 줍니다.
        return Response(
            code = responseCode,
            body = body,
            headers = connection.headerFields
        )
    }
}
```

### 2-4. 체인 매니저와 클라이언트
```kotlin
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
```

## 3. Chapter 2: Mini Retrofit 구현
Retrofit의 핵심은 Annotation, Reflection, 그리고 Dynamic Proxy입니다.

### 3-1. 어노테이션
```kotlin
// @Retention(RetentionPolicy.RUNTIME)
// 설명: 이 어노테이션이 언제까지 살아남을지를 정합니다.
// - SOURCE: 컴파일하면 사라짐 (주석 같은 존재)
// - CLASS: 바이트코드(.class)에는 남지만 실행 시엔 못 읽음
// - RUNTIME: 앱이 실행되는 동안에도 코드로 이 정보를 읽을 수 있음
// Retrofit은 실행 중에 Reflection으로 이 정보를 읽어야 하므로 반드시 RUNTIME이어야 합니다.
@Retention(RetentionPolicy.RUNTIME)

// @Target(AnnotationTarget.FUNCTION)
// 설명: 이 어노테이션을 어디에 붙일 수 있는지 정합니다.
// - FUNCTION: 함수 위에만 붙일 수 있음 (변수나 클래스에 붙이면 에러)
@Target(AnnotationTarget.FUNCTION)
annotation class GET(val value: String) // value는 "users/{id}" 같은 URL 경로를 담는다.


@Retention(RetentionPolicy.RUNTIME)
// @Target(AnnotationTarget.VALUE_PARAMETER)
// 설명: 함수의 파라미터(인자) 옆에만 붙일 수 있음. 예: fun getUser(@Path id: String)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val value: String) // value는 "id" 같은 치환할 키워드를 담습니다.
```

### 3-2. Mini Retrofit 본체
```kotlin
class MiniRetrofit2(
    private val baseUrl: String,
    private val client: MiniOkHttpClient
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service)
        ) { _, method, args ->
            val getAnno = method.getAnnotation(GET::class.java) ?: throw IllegalArgumentException("No GET annotation")

            var url = baseUrl + getAnno.value
            method.parameterAnnotations.forEachIndexed { idx, annos ->
                annos.filterIsInstance<Path>().forEach { path ->
                    url = url.replace("{${path.value}}", args?.get(idx).toString())
                }
            }

            // OkHttp Request 생성 -> Call 실행 -> 결과 반환
            return@newProxyInstance object : MiniCall<String> {
                override fun execute(): String {
                    // Request 객체 생성
                    // 이제 진짜 통신 준비를 합니다. URL과 메서드 방식을 담습니다.
                    val request = Request(url = url, method = "GET")

                    // OkHttp에게 위임
                    // "야 엔진아, 이 요청서대로 서버에 다녀와"라고 시킵니다.
                    val response = client.newCall(request).execute()

                    // 결과 반환
                    // 서버에서 온 응답의 body(JSON 문자열)만 꺼내서 사용자에게 줍니다.
                    return response.body
                }
            }
        } as T
    }
}
```

## 4. Chapter 3: 통합 테스트
작성한 라이브러리를 실제 안드로이드 앱에서 실행하고, 결과를 화면에 출력합니다.

### 4-1. 테스트용 API 인터페이스
```kotlin
interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}
```

### 4-2 MainActivity.kt
```kotlin
private val baseUrl = "https://api.github.com/"
private val userId = "user123"

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
```

## 5. 심화 분석: 코드 한 줄 한 줄 뜯어보기
우리가 작성한 코드가 실제 라이브러리의 어떤 부분과 매칭되는지 분석합니다.

### 5-1. MiniRetrofit 분석 (Dynamic Proxy)

```kotlin
[코드] Proxy.newProxyInstance(service.classLoader, arrayOf(service)) { ... }
```
- 설명: 자바의 리플렉션 기능을 이용해, 껍데기만 있는 MyApi 인터페이스를 런타임에 **실행 가능한 객체**로 만듭니다.
- 실제 Retrofit: Retrofit.create() 메서드 내부에서 동일하게 Proxy.newProxyInstance를 호출하여 구현체를 생성합니다.

```kotlin
[코드] method.getAnnotation(GET::class.java)
```
- 설명: 메서드 위에 붙은 @GET("users/{id}") 정보를 읽어옵니다. (Runtime Retention 정책 덕분)
- 실제 Retrofit: RequestFactory 클래스가 이 역할을 하며, 어노테이션을 파싱해 HTTP 메서드와 상대 경로를 추출합니다.

```kotlin
[코드] url.replace("{${path.value}}", args[idx])
```
- 설명: URL의 구멍난 부분({id})을 실제 파라미터(user123)로 메꿉니다.
- 실제 Retrofit: ParameterHandler 클래스가 @Path, @Query 등을 처리하여 RequestBuilder에 값을 채워 넣습니다.

### 5-2. MiniOkHttp 분석

```kotlin
[코드] interface Interceptor { fun intercept(chain: Chain): Response }
```
- 설명: 요청 흐름 중간에 끼어들 수 있는 훅(Hook) 포인트입니다.
- 실제 OkHttp: okhttp3.Interceptor 인터페이스와 100% 동일한 역할입니다.

```kotlin
[코드] class RealInterceptorChain(...) { fun proceed(...) }
```
- 설명: 현재 인터셉터를 실행하고, nextChain을 만들어 다음 인터셉터에게 넘겨주는 재귀적 호출을 담당합니다.
- 실제 OkHttp: okhttp3.internal.http.RealInterceptorChain 클래스가 이 역할을 합니다. OkHttp 내부에는 기본적으로 RetryAndFollowUpInterceptor, BridgeInterceptor, CacheInterceptor 등이 순서대로 포함되어 있습니다.

```kotlin
[코드] class NetworkInterceptor
```
- 설명: 체인의 가장 끝에서 실제 소켓 통신을 담당합니다.
- 실제 OkHttp: CallServerInterceptor가 이 역할을 하며, 내부적으로 Okio를 사용하여 소켓에 데이터를 쓰고 읽습니다.

# 결론
- 추상화(Abstraction): Interface와 Annotation만으로 복잡한 로직을 숨깁니다.
- 동적 프록시(Dynamic Proxy): 런타임에 코드를 생성하여 유연함을 확보합니다.
- 책임 연쇄(Chain of Responsibility): 네트워크 요청 과정을 여러 단계(인터셉터)로 나누어 처리합니다.
