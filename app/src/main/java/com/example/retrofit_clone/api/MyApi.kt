package com.example.retrofit_clone.api

import com.example.retrofit_clone.mini.GET
import com.example.retrofit_clone.mini.MiniCall
import com.example.retrofit_clone.mini.Path

interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}
