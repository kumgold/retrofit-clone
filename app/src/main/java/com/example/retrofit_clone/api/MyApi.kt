package com.example.retrofit_clone.api

import com.example.retrofit_clone.retrofit.Body
import com.example.retrofit_clone.retrofit.GET
import com.example.retrofit_clone.retrofit.MiniCall
import com.example.retrofit_clone.retrofit.POST
import com.example.retrofit_clone.retrofit.Path

interface MyApi {
    @GET("users/{id}")
    fun getUser(@Path("id") userId: String): MiniCall<String>

    @GET("posts/{id}")
    fun getPost(@Path("id") id: String): MiniCall<String>

    @GET("users/{id}")
    fun getUser2(@Path("id") id: String): MiniCall<User>

    @POST("posts")
    fun createPost(@Body newPost: PostRequest): MiniCall<PostResponse>
}

data class User(
    val login: String,
    val id: Int,
    val bio: String?
)

data class PostRequest(
    val title: String,
    val body: String,
    val userId: Int
)

data class PostResponse(
    val id: Int,
    val title: String,
    val body: String,
    val userId: Int
)