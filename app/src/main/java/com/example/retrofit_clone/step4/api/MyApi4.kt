package com.example.retrofit_clone.step4.api

import com.example.retrofit_clone.step4.retrofit.Body
import com.example.retrofit_clone.step4.retrofit.MiniCall
import com.example.retrofit_clone.step4.retrofit.POST

interface MyApi4 {
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