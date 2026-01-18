# Converter & CallAdapter
MiniRetrofit이 단순히 문자열(String)만 주고받는 장난감에서, 실제 객체를 자유자재로 다루는 상용 라이브러리 수준으로 진화할 수 있었던 이유는 바로 
Converter와 CallAdapter라는 두 가지 핵심 컴포넌트 덕분입니다.

---

## 💡 비유로 이해하기: 해외 직구와 배송

Retrofit을 해외 직구 대행사라고 생각해 봅시다.

1. Converter (번역가):
    * 영어를 모르는 내가 "신발 사줘(객체)"라고 하면, 이를 미국 쇼핑몰이 알아듣는 "영어 주문서(JSON)"로 **번역**해줍니다.
    * 반대로 물건과 함께 온 "영어 영수증(JSON)"을 내가 알아볼 수 있는 "한국어 영수증(객체)"으로 **번역**해줍니다.

2. CallAdapter (배송 옵션):
    * 물건(데이터)을 받을 때, "일반 택배(`Call`)로 받을지", "정기 구독(`Observable/Flow`)으로 받을지", "비동기 퀵서비스(`CompletableFuture`)로 받을지" **배달의 형태를 결정**합니다.

---

## 1. Converter (데이터 변환기)

`Converter`는 네트워크를 통해 오고 가는 **데이터의 형태(Format)**를 변환하는 역할을 합니다.

### 왜 필요한가요?
네트워크 통신은 0과 1의 바이트, 혹은 String만 이해합니다. 하지만 우리는 `User`, `Post` 같은 자바/코틀린 Object를 사용하고 싶습니다. 
이 둘 사이의 간극(직렬화/역직렬화)을 메워주는 것이 Converter입니다.

### Converter의 두 가지 역할 (양방향 변환)

1. `requestBodyConverter` (쓰기/보내기)
    * **역할:** 내 객체를 서버로 보낼 때 사용 (Serialization)
    * **흐름:** `User 객체` -> Converter -> `{"name": "Kim"}` (JSON String)
    * **사용처:** `@Body` 어노테이션이 붙은 데이터

2. `responseBodyConverter` (읽기/받기)
    * **역할:** 서버에서 온 데이터를 내 객체로 변환할 때 사용 (Deserialization)
    * **흐름:** `{"id": 1}` (JSON String) -> Converter -> `Post 객체`
    * **사용처:** API 메서드의 반환 타입 내부 (예: `Call<Post>`)

```kotlin
// Converter의 핵심 구조
interface Converter<F, T> {
    fun convert(value: F): T // F(From)를 T(To)로 변환!
}
```
대표적인 Converter: Gson, Moshi, Jackson(JSON 처리), SimpleXML(XML 처리), Protobuf 등

## 2. CallAdapter
`CallAdapter`는 Retrofit 인터페이스 메서드의 Return Type을 결정합니다.

### 왜 필요한가요?
기본적으로 Retrofit은 요청 정보를 `Call<T>`라는 자체 객체에 담아서 줍니다. 하지만 프로젝트에 따라 RxJava를 쓸 수도 있고, Kotlin Coroutines의 Flow를 쓸 수도 있습니다.

Retrofit 본체 코드를 고치지 않고도, 사용자가 원하는 비동기 라이브러리를 사용할 수 있게 해주는 플러그인이 바로 CallAdapter입니다.

### 동작 원리
CallAdapter는 Retrofit이 만들어낸 기본 Call 객체를 받아서, 개발자가 정의한 리턴 타입으로 포장하거나 변환합니다.

- Call<User> -> DefaultCallAdapter -> 그대로 Call<User>
- Call<User> -> RxJavaCallAdapter -> Single<User> 또는 Observable<User>
- Call<User> -> CoroutineCallAdapter -> Deferred<User> (또는 suspend fun 직접 지원)

```kotlin
// CallAdapter의 핵심 구조
interface CallAdapter<R, T> {
    fun responseType(): Type       // R: Call 안에 들어갈 실제 데이터 타입 (예: User)
    fun adapt(call: Call<R>): T    // Call<R>을 받아서 T(예: Single<User>)로 변환!
}
```

## 3. Factory Pattern : 확장성의 비밀
Retrofit이 정말 똑똑한 점은, Converter와 CallAdapter를 Factory 형태로 관리한다는 것입니다.
```kotlin
val retrofit = Retrofit.Builder()
    .addConverterFactory(GsonConverterFactory.create())
    .addConverterFactory(XmlConverterFactory.create())
    .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
    .build()
```

### Retrofit은 어떻게 맞는 도구를 찾을까?
Retrofit 내부에는 Factory List가 있습니다. 사용자가 api.getUser()를 호출하면 Retrofit은 다음과 같이 순회하며 도구를 찾습니다.

1. CallAdapter 찾기: 등록된 AdapterFactory들에게 차례대로 물어봅니다.
   - 만약 메서드 리턴 타입이 Single<User>라면, 해당 자료형을 처리할 수 있는 Factory를 찾습니다.
   - RxJavaFactory를 발견합니다. -> Adapter 사용
2. Converter 찾기: 어댑터가 알려준 실제 데이터 타입(User)을 변환할 수 있는 Factory를 찾습니다.
   - 서버에서 온 JSON을 User 객체로 바꿀 수 있는 Factory를 찾습니다.
   - GsonFactory가 손을 듭니다. -> Converter 사용

💡 핵심 포인트
- 이러한 Factory + Chain of Responsibility(책임 연쇄) 구조 덕분에, 
   Retrofit 라이브러리 개발자는 세상의 모든 데이터 형식(JSON, XML 등)이나 비동기 라이브러리(Rx, Coroutine 등)를 미리 알 필요가 없습니다.
- 새로운 기술이 나오면? 그저 새로운 Factory만 만들어서 add() 해주면 됩니다. 이것이 진정한 관심사의 분리(Separation of Concerns)이자 개방-폐쇄 원칙(OCP)의 실현입니다.

## 요약
|구분|Converter|CallAdapter|
|---|---------|-----------|
|목적|Body 형태 변환|Return Type 형태 변환|
|처리 대상|JSON, XML <-> Java/Kotlin Object|`Call<T>` <-> `Observable<T>`, `Flow<T>`|
|시점|네트워크 요청 전/후|메서드가 호출되는 즉시(Wrapper)|
