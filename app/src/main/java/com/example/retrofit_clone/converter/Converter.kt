package com.example.retrofit_clone.converter

import com.google.gson.Gson
import java.lang.reflect.Type

// 변환기 인터페이스 (F: From, T: To)
interface Converter<F, T> {
    fun convert(value: F): T

    // 변환기를 만들어내는 공장
    abstract class Factory {
        // 리턴 타입(Type)을 보고 알맞은 Converter를 반환 (못 만들면 null)
        open fun responseBodyConverter(type: Type): Converter<String, *>? {
            return null
        }
    }
}

// 구현체
class GsonConverterFactory private constructor(private val gson: Gson) : Converter.Factory() {

    // Static Factory Method 패턴
    companion object {
        fun create(): GsonConverterFactory = GsonConverterFactory(Gson())
    }

    // 타입(type)으로 변환할 Converter를 만들어 달라고 요청
    override fun responseBodyConverter(type: Type): Converter<String, *>? {
        // 모든 타입을 처리할 수 있다고 가정하고 컨버터 반환
        return object : Converter<String, Any> {
            override fun convert(value: String): Any {
                // Gson 라이브러리를 사용해 String JSON을 자바 객체(type)로 변환
                return gson.fromJson(value, type)
            }
        }
    }
}