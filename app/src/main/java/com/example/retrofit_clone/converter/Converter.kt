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

    override fun responseBodyConverter(type: Type): Converter<String, *>? {
        // 어떤 타입이든 Gson을 이용해 변환해주는 컨버터 반환
        return object : Converter<String, Any> {
            override fun convert(value: String): Any {
                // value(JSON String) -> Type(User Class)
                return gson.fromJson(value, type)
            }
        }
    }
}