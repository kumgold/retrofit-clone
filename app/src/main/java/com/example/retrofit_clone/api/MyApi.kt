package com.example.retrofit_clone.api

import com.example.retrofit_clone.retrofit.GET
import com.example.retrofit_clone.retrofit.MiniCall
import com.example.retrofit_clone.retrofit.Path

interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>

    @GET("posts/{id}")
    fun getPost(@Path("id") id: String): MiniCall<String>
}
