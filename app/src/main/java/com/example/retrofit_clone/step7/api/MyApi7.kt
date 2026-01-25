package com.example.retrofit_clone.step7.api

import com.example.retrofit_clone.step7.retrofit.GET
import com.example.retrofit_clone.step7.retrofit.MiniCall
import com.example.retrofit_clone.step7.retrofit.Query

interface MyApi7 {
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