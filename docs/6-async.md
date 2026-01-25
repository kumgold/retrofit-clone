# 6. 비동기 처리와 스레드 관리
이전 단계는 응답이 올 때까지 화면이 멈추거나 CoroutineScope 등을 사용해 스레드를 관리해야 했습니다.
그러나 Retrofit은 네트워크 요청을 보내면 응답이 올 때까지 기다리는 동기 방식으로 동작하지 않습니다.
이번 단계에서 스레드 풀을 관리하고 작업이 끝나면 자동으로 메인 스레드로 복귀하는 비동기 시스템을 구축합니다.

## 주요 구성 요소
### Callback & MiniCall 인터페이스
사용자가 비동기 요청을 보내고(`enqueue`), 결과를 받을 통로(`Callback`) 역할을 합니다.
```kotlin
// 요청의 결과(성공/실패)를 받을 리스너
interface Callback<T> {
    fun onResponse(call: MiniCall<T>, response: T)
    fun onFailure(call: MiniCall<T>, t: Throwable)
}

// 비동기 요청 메서드 추가
interface MiniCall<T> {
    fun execute(): T // 동기
    fun enqueue(callback: Callback<T>) // [NEW] 비동기
}
```

## Dispatcher (스레드 관리)
`MiniOkHttpClient` 내부에 위치하며, 실제 작업을 수행할 백그라운드 스레드 풀을 관리합니다.
- 역할 : 요청이 들어오면 대기열에 넣거나, 즉시 스레드를 할당해 실행합니다.
- 구현 : `ExecutorService` (FixedThreadPool) 사용
```kotlin
class Dispatcher {
    // 최대 64개의 스레드를 가진 풀 생성
    private val executorService = Executors.newFixedThreadPool(64)

    fun enqueue(call: Runnable) {
        executorService.execute(call) // 스레드 풀에 던짐
    }
} 
```

## MainThreadExecutor (메인 스레드로 전달)
백그라운드 작업이 끝난 후 결과를 안드로이드의 메인 스레드로 전달해주는 브릿지입니다.
- 역할 : `Handler.post()`를 사용하여 코드를 메인 스레드 대기열에 넣습니다.
- 중요성 : 안드로이드 정책상 백그라운드 스레드에서는 UI를 변경할 수 없기 때문에 필수적입니다.
```kotlin
class MainThreadExecutor : Executor {
    private val handler = Handler(Looper.getMainLooper())

    override fun execute(command: Runnable) {
        handler.post(command) // 메인 스레드로 슈팅!
    }
}
```

## 핵심 로직 : ServiceMethod 조율
`ServiceMethod`는 컴포넌트들을 연결하여 전체적인 흐름을 제어합니다.
```kotlin
// ServiceMethod.kt 내부의 invoke 메서드 로직

override fun enqueue(callback: Callback<Any>) {
    // 1. 스코프 캡처: 현재 Call 객체를 변수에 저장 (익명 클래스 내부에서 참조하기 위함)
    val currentCall = this

    // 2. 엔진(OkHttp)에게 비동기 요청 위임 -> Dispatcher가 백그라운드 스레드에서 실행함
    client.newCall(request).enqueue(object : MiniOkHttpClient.Callback {

        // [백그라운드 스레드] 통신 성공 시
        override fun onResponse(call: MiniOkHttpClient.Call, response: Response) {
            try {
                // 3. 데이터 파싱 (JSON -> Object)
                // ★ 성능 포인트: 파싱도 무거운 작업이므로 백그라운드에서 미리 수행합니다.
                val parsedData = responseConverter.convert(response.body)

                // 4. 스레드 전환 (Background -> Main)
                callbackExecutor.execute {
                    // [메인 스레드] 사용자 콜백 실행
                    callback.onResponse(currentCall, parsedData)
                }
            } catch (e: Exception) {
                // 파싱 에러 처리
                callbackExecutor.execute { callback.onFailure(currentCall, e) }
            }
        }

        // [백그라운드 스레드] 통신 실패 시
        override fun onFailure(call: MiniOkHttpClient.Call, e: Exception) {
            // 4. 스레드 전환 (Background -> Main)
            callbackExecutor.execute {
                callback.onFailure(currentCall, e)
            }
        }
    })
}
```

## 동기(Execute) vs 비동기(Enqueue)
|구분|execute()|enqueue()|
|---|---------|---------|
|실행 스레드|호출한 스레드 (Main에서 호출 시 멈춤)|별도의 백그라운드 스레드|
|리턴 시점|서버 응답이 올 때까지 대기(Blocking)|호출 즉시 리턴(Non-Blocking)|
|결과 수신|리턴 값(`return T`)|콜백 메서드 형태(`onResponse`)|
|에러 처리|`try-catch` 블록|콜백 메서드 `onFailure`에서 처리|

## 결론
- Concurrency(동시성) : 여러 개의 네트워크 요청을 동시에 처리할 수 있습니다. (Dispatcher)
- Thread Safety : 네트워크 작업은 백그라운드에서, UI 갱신은 메인 스레드에서 하도록 강제하여 `NetworkOnMainThreadException`을 차단합니다.
- Performance : JSON 파싱 같은 CPU 집약적 작업도 백그라운드에서 처리하여 UI 버벅임을 방지하였습니다.
