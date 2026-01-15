package com.example.retrofit_clone.step4.retrofit

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

// @Retention(RetentionPolicy.RUNTIME)
// 설명: 이 어노테이션이 언제까지 살아남을지를 정합니다.
// - SOURCE: 컴파일하면 사라짐 (주석 같은 존재)
// - CLASS: 바이트코드(.class)에는 남지만 실행 시엔 못 읽음
// - RUNTIME: 앱이 실행되는 동안에도 코드로 이 정보를 읽을 수 있음
// Retrofit은 실행 중에 Reflection으로 이 정보를 읽어야 하므로 반드시 RUNTIME이어야 합니다.
@Retention(RetentionPolicy.RUNTIME)

// @Target(AnnotationTarget.FUNCTION)
// 설명: 이 어노테이션을 어디에 붙일 수 있는지 정합니다.
// - FUNCTION: 함수 위에만 붙일 수 있음 (변수나 클래스에 붙이면 에러)
@Target(AnnotationTarget.FUNCTION)
annotation class GET(val value: String) // value는 "users/{id}" 같은 URL 경로를 담는다.


@Retention(RetentionPolicy.RUNTIME)
// @Target(AnnotationTarget.VALUE_PARAMETER)
// 설명: 함수의 파라미터(인자) 옆에만 붙일 수 있음. 예: fun getUser(@Path id: String)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val value: String) // value는 "id" 같은 치환할 키워드를 담습니다.

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class POST(val value: String)

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Body // 값(value) 없음, 파라미터 자체를 Body로 씀