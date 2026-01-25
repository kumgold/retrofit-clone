# 5. 성능 최적화(Caching)와 기능 확장
ServiceMethod를 도입하여 정보를 캐싱하고 API를 호출할 때마다 매번 어노테이션 파싱을 반복하지 않도록 합니다.
또한, @Query 어노테이션을 추가하여 URL 처리 능력을 고도화 합니다.

## 문제점과 해결책
### 문제점 : 반복되는 리플렉션 비용
아래의 과정을 매 호출할때마다 반복하는 문제가 있었습니다.
1. 메서드에 `@GET`이 붙었는지 확인
2. 반환 타입을 분석하여 `CallAdapter` 찾기
3. 제네릭 타입을 분석하여 `Converter` 찾기
4. 파라미터 어노테이션(`@Path`, `@Body`) 파싱

### 해결책 : ServiceMethod의 Caching
변하지 않는 정보는 한 번만 분석해서 저장합니다.
- ServiceMethod : 메서드 하나를 실행하기 위해 필요한 모든 정보(HTTP Method, URL, Converter, Adapter 등)을 미리 분석해서 담아두는 객체
- Caching : `ConcurrentHashMap`을 사용하여 `Method`를 Key, 분석된 `ServiceMethod`를 Value로 저장. 두 번째 호출부터 저장된 객체 사용

## 구현
### @Query 어노테이션 추가
검색 기능 등을 구현하려면 URL 뒤에 `?key=value` 형태의 쿼리 스트링이 필요합니다.
```kotlin
@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Query(val value: String)
```

### ServiceMethod : 파싱 로직 분리
객체가 생성되는 순간(init) 성능 비용을 지불하고, 이후 `invoke` 함수에 비용은 0에 가깝다.
```kotlin
class ServiceMethod(...) {
    // 분석된 결과물들 (캐시됨)
    private val httpMethod: String
    private val urlEndpoint: String
    private val callAdapter: CallAdapter<Any, Any>
    private val responseConverter: Converter<String, Any>
    private val parameterHandlers: Array<ParameterHandler>

    init {
        // 1. 어노테이션 파싱 (GET/POST)
        // 2. CallAdapter & Converter 찾기 (Factory 순회)
        // 3. 파라미터 핸들러 분석 (@Path, @Body, @Query)
        // ... (상세 로직은 코드 참조)
    }

    fun invoke(args: Array<out Any>?): Any {
        // 여기서는 미리 분석된 handler들을 이용해 URL 조립만 수행합니다. (매우 빠름)
        // ...
        return callAdapter.adapt(rawCall)
    }
}
```

### ParameterHandler : 전략 패턴 적용
파라미터마다 처리 방식이 다르기 때문에 `sealed class`로 추상화합니다.
- `ParameterHandler.Path`: URL 경로 치환 (`uses/{id}`)
- `ParameterHandler.Body`: 객체를 JSON으로 변환 (`Converter` 사용)
- `ParameterHandler.Query`: URL 쿼리 추가 (`?q=keyword`)

### MiniRetrofit5 : 캐싱 구현
`ConcurrentHashMap`을 사용하려 멀티 스레드 환경에서도 안전하게 캐싱을 처리합니다.
```kotlin
private val serviceMethodCache = ConcurrentHashMap<Method, ServiceMethod>()

private fun loadServiceMethod(method: Method): ServiceMethod {
    // getOrPut: 캐시에 있으면 반환, 없으면 생성 후 저장하고 반환 (원자적 연산)
    return serviceMethodCache.getOrPut(method) {
        ServiceMethod(baseUrl, client, method, converterFactories, callAdapterFactories)
    }
}
```

## 이전 버전과 비교
|상황|이전|MiniRetrofit5 (현재)|
|---|---|-------------------|
|첫 번째|어노테이션 파싱 + Converter/Adapter 탐색 (느림)|어노테이션 파싱 + Converter/Adapter 탑색 + 캐싱 (느림)|
|두 번째|어노테이션 파싱 + 탐색 반복 (느림)|캐시 조회 -> 즉시 실행 (매우 빠름)|
|N번째|N번 파싱 (낭비)|1회 파싱 후 (N-1)번 캐시 조회 (최적화)|

## 결론
1. 기능적 완성 : `@GET`, `@POST`, `@Body`, `@Path`에 이어 `@Query` 지원하며 REST API의 주요 스펙 구현
2. 구조적 완성 : `ServiceMethod` 패턴과 캐싱을 통해 실제 프로덕션 레벨의 라이브러리와 비슷한 아키텍처를 갖추었습니다.


