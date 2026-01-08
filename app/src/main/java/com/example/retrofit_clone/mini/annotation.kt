package com.example.retrofit_clone.mini

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

// 런타임에도 어노테이션 정보를 읽을 수 있어야 하므로 RUNTIME으로 설정
@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class GET(val value: String) // 예: "users/{id}"

@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Path(val value: String) // 예: "id"