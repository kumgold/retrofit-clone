# Retrofit-Clone

# [Deep Dive] 안드로이드 Retrofit, 바닥부터 직접 구현하며 원리 파헤치기

대부분의 안드로이드 개발자는 Retrofit을 "사용"하는 방법에 익숙합니다. 하지만 이 라이브러리가 **어떻게 인터페이스만으로 네트워크 요청을 수행하는지** 그 내부 원리를 깊이 이해하는 경우는 드뭅니다.
이 글에서는 Retrofit의 핵심 기술인 **Dynamic Proxy(동적 프록시)**와 **Reflection(리플렉션)**을 사용하여, 나만의 **Mini Retrofit**을 직접 구현해보고 그 구조를 파헤쳐 봅니다.

---

## 1. 핵심 개념: 마법의 원리

Retrofit의 마법은 **"인터페이스를 런타임에 구현체로 만드는 기술"**에 있습니다.

*   **Reflection (리플렉션):** 실행 중에 클래스, 메서드, 어노테이션의 정보를 분석하는 기술.
*   **Dynamic Proxy (동적 프록시):** 인터페이스만 정의되어 있을 때, 런타임에 가짜 구현체 객체를 생성하여 메서드 호출을 가로채는(Intercept) 기술.

---

## 2. 구현하기: Mini Retrofit 만들기

### Step 1. 어노테이션 정의 (Annotation)
Retrofit이 어떤 요청을 보내야 할지 식별하기 위한 표식을 만듭니다.

```kotlin
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

// 런타임까지 정보가 살아있어야 하므로 RUNTIME 정책 사용
@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class GET(val value: String) // 예: "users/{id}"

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val value: String) // 예: "id"
```

### Step 2. Call 인터페이스 정의
실행 결과를 감싸줄 래퍼(Wrapper) 인터페이스입니다.

```kotlin
interface MiniCall<T> {
    fun execute(): T
}
```

### Step 3. MiniRetrofit 구현 (핵심 로직)
Proxy.newProxyInstance를 통해 메서드 호출을 가로채고, URL을 조립하는 로직입니다.

```kotlin
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class MiniRetrofit(private val baseUrl: String) {

    @Suppress("UNCHECKED_CAST")
    fun <T> create(service: Class<T>): T {
        // Dynamic Proxy: 런타임에 인터페이스 구현체 생성
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf(service),
            object : InvocationHandler {
                override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any {
                    // 1. 메서드 호출 감지 (Interception)
                    val getAnnotation = method.getAnnotation(GET::class.java)
                    
                    if (getAnnotation != null) {
                        // 2. URL 파싱 및 파라미터 바인딩
                        val requestUrl = buildRequestUrl(getAnnotation.value, method, args)
                        
                        // 3. 실행기(Call) 반환 -> 실제 네트워크 요청은 여기서 수행됨
                        return object : MiniCall<String> {
                            override fun execute(): String {
                                println("🌐 Sending Request to: $requestUrl")
                                // 실제 Retrofit은 여기서 OkHttp를 호출합니다.
                                return "{ \"status\": 200, \"data\": \"Success\" }"
                            }
                        }
                    }
                    throw IllegalArgumentException("알 수 없는 메서드입니다.")
                }
            }
        ) as T
    }

    private fun buildRequestUrl(endpoint: String, method: Method, args: Array<out Any>?): String {
        var finalUrl = baseUrl + endpoint
        val parameterAnnotations = method.parameterAnnotations
        
        if (args != null) {
            for (i in args.indices) {
                val annotations = parameterAnnotations[i]
                for (annotation in annotations) {
                    if (annotation is Path) {
                        finalUrl = finalUrl.replace("{${annotation.value}}", args[i].toString())
                    }
                }
            }
        }
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

fun main() {
    val retrofit = MiniRetrofit("https://api.github.com/")
    val api = retrofit.create(MyApi::class.java) // 구현체가 자동 생성됨!

    val call = api.getUser("dev_user") // 호출을 가로채서 URL 생성
    val result = call.execute()        // 가짜 통신 실행

    println("✅ Result: $result")
}
```

## 3. 심화 학습: 아키텍처 비교
직접 구현해본 MiniRetrofit과 실제 안드로이드 네트워크 라이브러리들의 관계

### Proxy vs HttpUrlConnection vs OkHttp vs Retrofit
|기술/라이브러리| 설명                                                     |
|-----------|--------------------------------------------------------|
|Proxy| 자바 언어 기능. 인터페이스 호출을 가로채서 "통신해!"라고 명령을 내리는 기술적 도구.      |
|HttpUrlConnection| Java 표준 API (Low Level). 직접 소켓을 다루며 사용이 불편함.           |
|OkHttp| Square사가 만든 강력한 통신 엔진. Connection Pooling 등으로 성능을 최적화함. |
|Retrofit| Proxy를 이용해 설계도(인터페이스)를 해석하고, OkHttp에게 작업을 지시하는 관리자.    |

### Retrofit의 기능
Retrofit 자체는 Reflection을 사용하기 때문에 미세한 오버헤드가 발생할 수 있지만, 결과적으로 가장 빠른 체감 성능을 냅니다. 
그 이유는 Retrofit이 아니라 내부 엔진(OkHttp)과 처리 방식의 최적화 때문입니다.

1. Connection Pooling (커넥션 풀링):
    매 요청마다 3-way handshake를 하지 않고, 기존 소켓 연결을 재사용하여 지연 시간을 획기적으로 줄입니다. 
2. Streaming & Conversion (스트리밍):
   응답 데이터를 거대한 String으로 메모리에 쌓지 않고, 바이트 스트림 상태에서 즉시 객체로 변환합니다(Gson/Moshi Converter). 이는 메모리 효율과 속도 모두에서 직접 구현한 코드보다 뛰어납니다.
3. Smart Thread Management:
   내부적으로 스레드 풀과 비동기 처리를 효율적으로 관리하여 메인 스레드 차단(Blocking)을 방지합니다.

## 4. 결론
Retrofit을 "구현"해본다는 것은 **추상화(Abstraction)**의 힘을 이해하는 과정입니다.

- 사용자는 **Interface**만 작성합니다.
- **Dynamic Proxy**가 이를 가로채서 로직을 주입합니다.
- **Reflection**이 어노테이션을 읽어 설정을 완료합니다.
- **OkHttp**가 실제 통신을 수행합니다.