package com.example.retrofit_clone.proxy

interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>
}
