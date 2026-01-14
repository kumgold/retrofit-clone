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

// 기본 CallAdapter 생성 Factory class
class DefaultCallAdapterFactory : CallAdapter.Factory() {

    // 리턴 타입(returnType)을 처리할 어댑터를 만들어달라고 요청받는 메서드
    override fun get(returnType: Type): CallAdapter<*, *>? {
        // 타입 검사: 반환 타입의 껍데기(Raw Class)가 MiniCall 인지 확인
        if (getRawType(returnType) != MiniCall::class.java) {
            // MiniCall이 아니면(예: String, List 등) 처리 못함 -> null 반환
            return null
        }

        // 제네릭 검사: MiniCall<User> 처럼 제네릭 파라미터가 있는지 확인
        if (returnType !is ParameterizedType) {
            // 그냥 MiniCall 이라고만 쓰면 안됨. 안에 뭐가 들었는지(User) 알려줘야 함.
            throw IllegalArgumentException("MiniCall return type must be parameterized as MiniCall<Foo> or MiniCall<? extends Foo>")
        }

        // 내부 타입 추출: <User> 부분의 타입을 꺼냄
        val responseType = getParameterUpperBound(0, returnType)

        // 어댑터 구현체 반환
        return object : CallAdapter<Any, MiniCall<Any>> {
            // 이 어댑터가 다루는 실제 데이터 타입은 User(responseType) 입니다.
            override fun responseType(): Type = responseType

            // 실제로 call을 받아서 반환값으로 바꿔주는 함수
            // DefaultAdapter는 별도의 변환 없이 call을 그대로 리턴합니다.
            override fun adapt(call: MiniCall<Any>): MiniCall<Any> {
                // 기본 동작은 그냥 call을 그대로 리턴하는 것
                return call
            }
        }
    }

    // [유틸리티] Type 객체에서 Class<?> 정보를 안전하게 꺼내는 메서드
    private fun getRawType(type: Type): Class<*> {
        return when (type) {
            is Class<*> -> type  // 일반 클래스면 그대로 반환
            is ParameterizedType -> type.rawType as Class<*>  // 제네릭이면 Raw Type 반환
            else -> throw IllegalArgumentException()
        }
    }
}