package com.example.retrofit_clone.step3.api

import com.example.retrofit_clone.step3.retrofit.GET
import com.example.retrofit_clone.step3.retrofit.MiniCall
import com.example.retrofit_clone.step3.retrofit.Path

interface MyApi3 {
    @GET("users/{id}")
    fun getUser(@Path("id") id: String): MiniCall<User>
}

data class User(
    val login: String,
    val id: Int,
    val bio: String?
)