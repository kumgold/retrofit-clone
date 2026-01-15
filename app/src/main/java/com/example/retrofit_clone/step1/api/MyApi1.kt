package com.example.retrofit_clone.step1.api

import com.example.retrofit_clone.step1.retrofit.GET
import com.example.retrofit_clone.step1.retrofit.MiniCall
import com.example.retrofit_clone.step1.retrofit.Path

interface MyApi1 {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}