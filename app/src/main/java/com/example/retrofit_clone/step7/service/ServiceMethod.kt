package com.example.retrofit_clone.step7.service

import com.example.retrofit_clone.step7.adapter.CallAdapter
import com.example.retrofit_clone.step7.converter.Converter
import com.example.retrofit_clone.step7.okhttp.MiniOkHttpClient
import com.example.retrofit_clone.step7.okhttp.Request
import com.example.retrofit_clone.step7.retrofit.Body
import com.example.retrofit_clone.step7.retrofit.Callback
import com.example.retrofit_clone.step7.retrofit.GET
import com.example.retrofit_clone.step7.retrofit.MainThreadExecutor
import com.example.retrofit_clone.step7.retrofit.MiniCall
import com.example.retrofit_clone.step7.retrofit.POST
import com.example.retrofit_clone.step7.retrofit.Path
import com.example.retrofit_clone.step7.retrofit.Query
import java.lang.reflect.Method

// 메서드 하나에 대한 분석 결과를 저장하는 캐시 객체
class ServiceMethod(
    private val baseUrl: String, // 기본 도메인 URL
    private val client: MiniOkHttpClient, // 네트워크 엔진
    private val method: Method, // 분석할 타겟 메서드 (예: searchUsers)
    private val converterFactories: List<Converter.Factory>,
    private val callAdapterFactories: List<CallAdapter.Factory>
) {
    // --- 분석된 정보들을 저장할 필드들 (캐싱) ---
    private val httpMethod: String // "GET" or "POST"
    private val urlEndpoint: String // "search/users"
    private val callAdapter: CallAdapter<Any, Any> // 반환 타입을 처리할 어댑터
    private val responseConverter: Converter<String, Any> // 응답 바디를 변환할 컨버터
    private val parameterHandlers: Array<ParameterHandler> // 파라미터 처리 목록

    init {
        // 1. HTTP Method & URL 분석
        val getAnno = method.getAnnotation(GET::class.java)
        val postAnno = method.getAnnotation(POST::class.java)

        if (getAnno != null) {
            httpMethod = "GET"
            urlEndpoint = getAnno.value
        } else if (postAnno != null) {
            httpMethod = "POST"
            urlEndpoint = postAnno.value
        } else {
            // 어노테이션이 없으면 통신을 할 수 없으므로 에러 처리
            throw IllegalArgumentException("Method must have @GET or @POST")
        }

        // 2. CallAdapter 찾기
        val returnType = method.genericReturnType // 예: MiniCall<SearchResponse>
        var foundAdapter: CallAdapter<*, *>? = null

        // 등록된 Factory들을 순회하며 적절한 Adapter를 찾음
        for (factory in callAdapterFactories) {
            val adapter = factory.get(returnType)
            if (adapter != null) {
                foundAdapter = adapter
                break
            }
        }

        // 찾은 Adapter를 필드에 저장 (캐스팅)
        @Suppress("UNCHECKED_CAST")
        callAdapter = foundAdapter as? CallAdapter<Any, Any>
            ?: throw IllegalArgumentException("No CallAdapter for $returnType")

        // 3. Response Converter 찾기
        // CallAdapter가 알려준 실제 데이터 타입(예: SearchResponse)을 가져옴
        val responseType = callAdapter.responseType()
        var foundConverter: Converter<String, *>? = null

        // 등록된 Factory들을 순회하며 적절한 Converter를 찾음
        for (factory in converterFactories) {
            val converter = factory.responseBodyConverter(responseType)
            if (converter != null) {
                foundConverter = converter
                break
            }
        }

        // 찾은 Converter를 필드에 저장
        @Suppress("UNCHECKED_CAST")
        responseConverter = foundConverter as? Converter<String, Any>
            ?: throw IllegalArgumentException("No ResponseConverter for $responseType")


        // 4. 파라미터 핸들러 분석
        // 각 파라미터가 어떤 역할(@Path, @Query, @Body)인지 미리 파악해둡니다.
        parameterHandlers = parseParameterHandlers()
    }

    // 결과를 메인 스레드로 보내줄 배달부 생성
    private val callbackExecutor = MainThreadExecutor()

    // 사용자가 메서드를 호출(invoke)할 때 실행되는 함수
    fun invoke(args: Array<out Any>?): Any {
        var fullUrl = baseUrl + urlEndpoint // 초기 URL 설정
        var bodyJson: String? = null // POST Body용 변수
        val queryParams = StringBuilder() // 쿼리 파라미터 조립용 빌더

        // 미리 분석해둔 핸들러들을 순서대로 실행하며 URL과 Body를 채움
        parameterHandlers.forEachIndexed { index, handler ->
            // 사용자가 넘긴 인자값 (예: "jakewharton")
            val arg = args?.get(index) ?: return@forEachIndexed

            when (handler) {
                is ParameterHandler.Path -> {
                    // @Path: URL의 {placeholder}를 값으로 치환
                    fullUrl = fullUrl.replace("{${handler.name}}", arg.toString())
                }
                is ParameterHandler.Body -> {
                    // @Body: 객체를 JSON으로 변환하여 bodyJson에 저장
                    bodyJson = handler.converter.convert(arg)
                }
                is ParameterHandler.Query -> {
                    // @Query: URL 뒤에 ?key=value 형태로 붙임
                    // 첫 쿼리 앞에는 '?', 그 뒤에는 '&'를 붙이는 로직
                    if (queryParams.isEmpty()) queryParams.append("?") else queryParams.append("&")
                    queryParams.append("${handler.name}=${arg}")
                }
            }
        }

        // 조립된 쿼리 스트링을 전체 URL에 붙임
        fullUrl += queryParams.toString()

        // Request 객체 생성 (아직 전송 안 함)
        val request = Request(fullUrl, httpMethod, body = bodyJson)

        // 익명 클래스로 MiniCall 구현체 생성 및 반환
        return object : MiniCall<Any> {

            // 1. 동기 실행
            override fun execute(): Any {
                val response = client.newCall(request).execute()
                return responseConverter.convert(response.body)
            }

            // 2. 비동기 실행 구현
            override fun enqueue(callback: Callback<Any>) {
                // [중요] this 스코프 문제 해결을 위해 현재 MiniCall 객체를 변수에 저장
                val currentCall = this

                // OkHttp 클라이언트에게 비동기 요청을 위임합니다.
                // 이 순간, 작업은 Dispatcher의 스레드 풀로 넘어갑니다.
                client.newCall(request).enqueue(object : MiniOkHttpClient.Callback {

                    // [백그라운드 스레드] 통신 실패 시 호출됨
                    override fun onFailure(call: MiniOkHttpClient.Call, e: Exception) {
                        // 사용자의 콜백(callback)은 UI 작업을 할 수도 있으므로,
                        // 반드시 메인 스레드로 넘겨서 실행해야 합니다.
                        callbackExecutor.execute {
                            // [메인 스레드] 사용자에게 실패 알림
                            callback.onFailure(currentCall, e)
                        }
                    }

                    // [백그라운드 스레드] 통신 성공 시 호출됨
                    override fun onResponse(call: MiniOkHttpClient.Call, response: com.example.retrofit_clone.step6.okhttp.Response) {
                        try {
                            // 데이터 파싱 (JSON String -> Object)
                            // [성능 최적화] 파싱도 무거운 작업이므로, 여기서(백그라운드) 미리 수행합니다.
                            // 메인 스레드에서 하면 UI가 버벅일 수 있습니다.
                            val parsedData = responseConverter.convert(response.body)

                            // 파싱된 데이터를 들고 메인 스레드로 이동
                            callbackExecutor.execute {
                                // [메인 스레드] 사용자에게 성공 결과(객체) 전달
                                callback.onResponse(currentCall, parsedData)
                            }
                        } catch (e: Exception) {
                            // 파싱 도중 에러가 나면 실패로 간주
                            callbackExecutor.execute {
                                callback.onFailure(currentCall, e)
                            }
                        }
                    }
                })
            }

            override fun clone(): MiniCall<Any> = this // 단순화
        }.let { rawCall ->
            // CallAdapter를 통해 최종 반환 타입으로 변환 (MiniCall<T> -> Call<T>)
            callAdapter.adapt(rawCall)
        }
    }

    // 파라미터 어노테이션들을 분석하여 Handler 배열을 만드는 메서드
    private fun parseParameterHandlers(): Array<ParameterHandler> {
        val annotations = method.parameterAnnotations // 파라미터별 어노테이션 목록
        val types = method.genericParameterTypes // 파라미터별 타입 목록

        // 파라미터 개수만큼 배열 생성
        return Array(annotations.size) { i ->
            val paramAnnos = annotations[i]
            var handler: ParameterHandler? = null

            // 파라미터 하나에 붙은 어노테이션들을 순회
            for (anno in paramAnnos) {
                if (anno is Path) {
                    handler = ParameterHandler.Path(anno.value)
                } else if (anno is Query) {
                    handler = ParameterHandler.Query(anno.value)
                } else if (anno is Body) {
                    // @Body인 경우, RequestConverter(객체 -> JSON)를 찾아야 함
                    val converter = converterFactories.firstOrNull { it.requestBodyConverter(types[i]) != null }
                            as? Converter<Any, String>
                        ?: throw IllegalArgumentException("No RequestConverter for Body")
                    handler = ParameterHandler.Body(converter)
                }
            }
            // 적절한 어노테이션이 없으면 에러

            handler ?: throw IllegalArgumentException("Parameter must have annotation (@Path, @Body, @Query)")
        }
    }

    // 파라미터 처리 전략을 표현하는 Sealed Class (다형성 활용)
    sealed class ParameterHandler {
        data class Path(val name: String) : ParameterHandler()
        data class Query(val name: String) : ParameterHandler()
        data class Body(val converter: Converter<Any, String>) : ParameterHandler()
    }
}