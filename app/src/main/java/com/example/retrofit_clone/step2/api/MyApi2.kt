package com.example.retrofit_clone.step2.api

import com.example.retrofit_clone.step2.retrofit.GET
import com.example.retrofit_clone.step2.retrofit.MiniCall
import com.example.retrofit_clone.step2.retrofit.Path

interface MyApi2 {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}