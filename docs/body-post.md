# @Post & @Body
서버에서 데이터를 가져오는 GET뿐만 아니라 로그인, 글쓰기, 파일 업로드와 같은 
데이터를 서버로 보내는 POST 메커니즘과 Body의 작동 원리를 이해합니다.

## GET vs POST
|특징|GET|POST|
|---|---|----|
|데이터 위치|URL 뒤 (`?id=1&name=kim`)|HTTP Body 안(`{"id":1, "name":"kim}`)|
|용량 제한|URL 길이 제한으로 인해 작음.|제한 없음 (대용량 가능)|
|보안|URL에 노출됨 (보안성 낮음)|Body 내부에 숨겨짐 (HTTPS 사용 시 암호화)|

## @Body
Retrofit에서 @Body가 붙는 순간 해당 파라미터를 특별하게 취급합니다.
```kotlin
interface MyApi {
    @POST("posts")
    fun createPost(@Body post: PostRequest): Call<PostResponse>
}
```

### 내부 동작
1. Reflection 감지 : Retrofit은 메서드를 실행할 때 파라미터를 훑어봅니다.
2. 타겟 식별 : 어떤 파라미터에 `@Body`가 붙어있는지 찾아냅니다. (PostRequest 객체)
3. 직렬화(Serialization) : 해당 객체를 서버가 이해할 수 있는 문자열(JSON)로 변환합니다. 이때 Converter가 사용됩니다.

## Converter의 확장 : RequestBodyConverter
서버에서 받은 JSON을 Object로 변경하는 역직렬화(Deserialization)이 필요합니다.

### 양방향 변환 구조
- ResponseConverter(Read) : Stream -> Object (GET 요청 시 사용)
- RequestConverter(Write) : Object -> String/Byte (POST 요청 시 사용)

```kotlin
override fun requestBodyConverter(type: Type): Converter<*, String>? {
    return object : Converter<Any, String> {
        override fun convert(value: Any): String {
            // Gson 라이브러리가 객체를 JSON 문자열로 바꿔줍니다.
            return gson.toJson(value) 
        }
    }
}
```

## 네트워크 레벨 변화 : OutputStream
데이터가 JSON으로 준비되었다면, 실제로 전송을 해야 합니다. 작업은 OkHttp의 NetworkInterceptor에서 이루어집니다.

### doOutput과 OutputStream
HttpURLConnection은 기본적으로 읽기 전용입니다. 데이터를 보내려면 Output에 대해서 명시해야 합니다.
```kotlin
// NetworkInterceptor.kt 내부

// 1. 요청에 Body가 있는지 확인
if (request.body != null) {
    // 2. 쓰기 모드 활성화
    connection.doOutput = true 
    
    // 3. Content-Type 헤더 설정 (서버에게 JSON이라고 알려줌)
    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    
    // 4. 빨대(Stream)를 꽂고 데이터 전송
    connection.outputStream.use { os ->
        os.write(request.body.toByteArray()) // 문자열을 바이트로 변환해 전송
        os.flush() // 남은 데이터 밀어넣기
    }
}
```

## REST Client 완성
`@POST`와 `@Body`를 구현함으로써 완전한 REST Client를 구현할 수 있었습니다.
- 동적 메서드 : `@GET`뿐만 아니라 `@POST` 등을 어노테이션만으로 교체할 수 있습니다.
- 자동 직렬화 : 개발자가 `json.put("key", value)`를 하지 않아도 알아서 JSON으로 변환되어 서버에 전송됩니다.

 
Retrofit이 간편한 이유 또한 강력한 추상화를 제공하기 때문입니다.