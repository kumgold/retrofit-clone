package com.example.retrofit_clone.adapter

import com.example.retrofit_clone.retrofit.MiniCall
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

// 어댑터 인터페이스 (R: 응답 타입, T: 최종 반환 타입)
interface CallAdapter<R, T> {
    // 이 어댑터가 다루는 실제 데이터 타입 (예: Call<User>라면 User)
    fun responseType(): Type

    // MiniCall<R>을 받아서 T로 바꿔주는 함수
    fun adapt(call: MiniCall<R>): T

    // 어댑터 공장
    abstract class Factory {
        open fun get(returnType: Type): CallAdapter<*, *>? {
            return null
        }

        // 유틸리티: Call<User>에서 User를 꺼내는 함수 (제네릭 타입 추출)
        protected fun getParameterUpperBound(index: Int, type: ParameterizedType): Type {
            return type.actualTypeArguments[index]
        }
    }
}

// 구현체
class DefaultCallAdapterFactory : CallAdapter.Factory() {

    override fun get(returnType: Type): CallAdapter<*, *>? {
        // 반환 타입이 MiniCall인지 확인
        if (getRawType(returnType) != MiniCall::class.java) {
            return null
        }

        // MiniCall<User> 처럼 제네릭이 지정되어 있는지 확인
        if (returnType !is ParameterizedType) {
            throw IllegalArgumentException("MiniCall return type must be parameterized as MiniCall<Foo> or MiniCall<? extends Foo>")
        }

        // <User> 내부 타입 추출
        val responseType = getParameterUpperBound(0, returnType)

        return object : CallAdapter<Any, MiniCall<Any>> {
            override fun responseType(): Type = responseType

            override fun adapt(call: MiniCall<Any>): MiniCall<Any> {
                // 기본 동작은 그냥 call을 그대로 리턴하는 것
                return call
            }
        }
    }

    // 타입의 Raw Class를 가져오는 헬퍼 (예: List<String> -> List)
    private fun getRawType(type: Type): Class<*> {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as Class<*>
            else -> throw IllegalArgumentException()
        }
    }
}