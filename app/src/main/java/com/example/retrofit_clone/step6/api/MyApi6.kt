package com.example.retrofit_clone.step6.api

import com.example.retrofit_clone.step6.retrofit.GET
import com.example.retrofit_clone.step6.retrofit.MiniCall
import com.example.retrofit_clone.step6.retrofit.Query

interface MyApi6 {
    @GET("search/users")
    fun searchUsers(@Query("q") keyword: String): MiniCall<SearchResponse>
}

data class User(
    val login: String,
    val id: Int,
    val bio: String?
)

data class SearchResponse(
    val total_count: Int,
    val items: List<User>
)