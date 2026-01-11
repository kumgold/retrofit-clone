# 안드로이드 Retrofit, 바닥부터 직접 구현하며 원리 파헤치기

대부분의 안드로이드 개발자는 Retrofit을 사용하는 방법에 익숙합니다. 하지만 이 라이브러리가 **어떻게 인터페이스만으로 네트워크 요청을 수행하는지** 그 내부 원리를 깊이 이해하는 경우는 드뭅니다.<br>
이 글에서는 Retrofit의 핵심 기술인 **Dynamic Proxy**와 **Reflection**을 사용하여, 나만의 **Mini Retrofit**을 직접 구현해보고 그 구조를 파헤쳐 봅니다.

---

## 1. 핵심 개념

Retrofit의 마법은 **인터페이스를 런타임에 구현체로 만드는 기술**에 있습니다.

*   **Reflection:** 실행 중에 클래스, 메서드, 어노테이션의 정보를 분석하는 기술.
*   **Dynamic Proxy:** 인터페이스만 정의되어 있을 때, 런타임에 가짜 구현체 객체를 생성하여 메서드 호출을 가로채는(Intercept) 기술.

## 2. 구현하기

### Step 1. 어노테이션 정의
Retrofit이 어떤 요청을 보내야 할지 식별하기 위한 표식을 만듭니다.

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

### Step 2. Call 인터페이스 정의
실행 결과를 감싸줄 래퍼(Wrapper) 인터페이스입니다.

```kotlin
// 실제 Retrofit의 Call 인터페이스 단순화 버전
interface MiniCall<T> {
    fun execute(): T // 동기적으로 실행해서 결과 반환
}
```

### Step 3. MiniRetrofit 구현 (핵심 로직)
Proxy.newProxyInstance를 통해 메서드 호출을 가로채고, URL을 조립하는 로직입니다.

```kotlin
class MiniRetrofit(private val baseUrl: String) {

    // <T> create(service: Class<T>): T
    // 설명: 제네릭 T는 우리가 만든 인터페이스(MyApi) 타입을 의미합니다.
    // Class<T>는 MyApi::class.java 정보를 받습니다.
    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {

        // Proxy.newProxyInstance(...)
        // 설명: 자바의 리플렉션 API를 사용해 가짜 객체(Proxy)를 만드는 명령어입니다.
        // 이 함수가 성공적으로 실행되면, MyApi 인터페이스를 구현한 객체가 나타납니다.
        return Proxy.newProxyInstance(
            // service.classLoader
            // 설명: 클래스 로더를 지정합니다.
            // MyApi 인터페이스를 읽어들인 Loader에게 이 가짜 객체도 메모리에 올려달라는 뜻입니다.
            service.classLoader,

            // arrayOf(service)
            // 설명: 이 가짜 객체가 구현해야 할 인터페이스 목록입니다.
            // 여기서는 [MyApi] 하나만 구현하면 됩니다.
            arrayOf(service),

            // InvocationHandler (익명 클래스 또는 람다)
            // 설명: 가장 중요한 가로채기(Intercept) 로직입니다.
            // 사용자가 api.getUser()를 호출할 때마다 실행 흐름이 여기로 점프합니다.
            // proxy: 가짜 객체 본인 / method: 호출된 메서드 정보(getUser) / args: 넘겨진 인자들(["user123"])
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // method.getAnnotation(GET::class.java)
                    // 설명: 호출된 메서드(getUser) 위에 @GET 어노테이션이 붙어있는지 확인합니다.
                    // 리플렉션을 사용해 런타임에 코드를 분석하는 겁니다.
                    val getAnnotation = method.getAnnotation(GET::class.java)

                    if (getAnnotation != null) {
                        // URL 조립
                        // @GET("users/{id}")의 값과 파라미터 "user123"을 합쳐서
                        // "https://api.github.com/users/user123"을 만듭니다.
                        val requestUrl = buildRequestUrl(getAnnotation.value, method, args)

                        // MiniCall 객체 반환 (익명 클래스)
                        // 설명: Retrofit은 결과를 바로 주지 않고, 실행할 수 있는 명령 객체(Call)를 줍니다.
                        // 사용자가 나중에 .execute()를 호출해야 진짜 통신이 일어납니다.
                        return object : MiniCall<String> {
                            override fun execute(): String {
                                // 실제 네트워크 통신 대신 로그를 찍습니다.
                                println("[MiniRetrofit] Network Request Sending to: $requestUrl")
                                return "{ \"result\": \"Success\", \"data\": \"Fake Data\" }"
                            }
                        }
                    }

                    throw IllegalArgumentException("알 수 없는 메서드입니다.")
                }
            }
        ) as T // 만들어진 Object를 T(MyApi) 타입으로 캐스팅해서 반환합니다.
    }

    // URL의 {path} 부분을 실제 인자값으로 교체하는 로직
    private fun buildRequestUrl(endpoint: String, method: Method, args: Array<out Any>?): String {
        // 초기 URL 설정
        // baseUrl("https://...") + endpoint("users/{id}")를 합칩니다.
        var finalUrl = baseUrl + endpoint

        // method.parameterAnnotations
        // 설명: 메서드의 파라미터들에 붙은 어노테이션들을 '전부' 가져옵니다.
        // 왜 2차원 배열일까요? -> fun test(@Path @NotNull id: String) 처럼
        // 하나의 파라미터에 어노테이션이 여러 개 붙을 수도 있기 때문입니다.
        // 구조: [[1번 파라미터의 어노테이션들], [2번 파라미터의 어노테이션들], ...]
        val parameterAnnotations = method.parameterAnnotations

        // 인자가 하나라도 있다면 루프를 돕니다.
        if (args != null) {
            // 파라미터 개수만큼 반복 (i: 인덱스)
            for (i in args.indices) {
                // i번째 파라미터에 붙은 어노테이션 목록을 가져옴
                val annotations = parameterAnnotations[i]

                // 어노테이션 하나하나 검사
                for (annotation in annotations) {
                    // @Path 어노테이션 확인
                    if (annotation is Path) {
                        // 치환할 키 찾기
                        // annotation.value가 "id"라면 key는 "{id}"가 됩니다.
                        val key = "{${annotation.value}}" // 예: "{id}"

                        // 실제 값 가져오기
                        // args[i]에는 사용자가 넘긴 "user123"이 들어있습니다.
                        val value = args[i].toString()    // 예: "user123"

                        // 문자열 교체
                        // URL상의 "{id}"를 "user123"으로 바꿔치기합니다.
                        finalUrl = finalUrl.replace(key, value)
                    }
                }
            }
        }
        // 완성된 URL 반환 (예: https://api.github.com/users/user123)
        return finalUrl
    }
}
```

### Step 4. 실행 및 검증
만든 라이브러리를 실제 사용하는 코드입니다.

```kotlin
// 사용자가 정의하는 인터페이스
interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}

private val baseUrl = "https://api.github.com/"
private val userId = "user123"

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
```

## 3. 아키텍처 비교
직접 구현해본 MiniRetrofit과 실제 안드로이드 네트워크 라이브러리들의 관계

### Proxy vs HttpUrlConnection vs OkHttp vs Retrofit
|기술/라이브러리| 설명                                                     |
|-----------|--------------------------------------------------------|
|Proxy| 자바 언어 기능. 인터페이스 호출을 가로채서 "통신해!"라고 명령을 내리는 기술적 도구.      |
|HttpUrlConnection| Java 표준 API (Low Level). 직접 소켓을 다루며 사용이 불편함.           |
|OkHttp| Square사가 만든 강력한 통신 엔진. Connection Pooling 등으로 성능을 최적화함. |
|Retrofit| Proxy를 이용해 설계도(인터페이스)를 해석하고, OkHttp에게 작업을 지시하는 관리자.    |

### Retrofit 참고사항
Retrofit 자체는 Reflection을 사용하기 때문에 미세한 오버헤드가 발생할 수 있지만, 결과적으로 가장 빠른 체감 성능을 냅니다.
그 이유는 Retrofit이 아니라 내부 엔진(OkHttp)과 처리 방식의 최적화 때문입니다.

1. Connection Pooling (커넥션 풀링):
   매 요청마다 3-way handshake를 하지 않고, 기존 소켓 연결을 재사용하여 지연 시간을 획기적으로 줄입니다.
2. Streaming & Conversion (스트리밍):
   응답 데이터를 거대한 String으로 메모리에 쌓지 않고, 바이트 스트림 상태에서 즉시 객체로 변환합니다(Gson/Moshi Converter). 이는 메모리 효율과 속도 모두에서 직접 구현한 코드보다 뛰어납니다.
3. Smart Thread Management:
   내부적으로 스레드 풀과 비동기 처리를 효율적으로 관리하여 메인 스레드 차단(Blocking)을 방지합니다.

## 4. 결론
Retrofit을 구현해본다는 것은 **추상화**의 힘을 이해하는 과정입니다.

- 사용자는 **Interface**만 작성합니다.
- **Dynamic Proxy**가 이를 가로채서 로직을 주입합니다.
- **Reflection**이 어노테이션을 읽어 설정을 완료합니다.
- **OkHttp**가 실제 통신을 수행합니다.