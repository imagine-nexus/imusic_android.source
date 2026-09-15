package com.imusic.app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- Official YouTube Data API v3 ---
interface YouTubeSearchApi {
    @GET("youtube/v3/search?part=snippet&type=video&videoCategoryId=10&maxResults=20")
    suspend fun searchVideos(
        @Query("q") query: String,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

object YouTubeClient {
    // Put your exact Google Cloud YouTube API Key here inside the quotes
    const val API_KEY = "AIzaSyAns13m5rHn4wExQaQP6xL7-wlmhVCteI8"

    val api: YouTubeSearchApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YouTubeSearchApi::class.java)
}

data class YouTubeSearchResponse(val items: List<YTItem>)
data class YTItem(val id: YTId, val snippet: YTSnippet)
data class YTId(val videoId: String?)
data class YTSnippet(val title: String, val channelTitle: String, val thumbnails: YTThumbnails)
data class YTThumbnails(val high: YTThumbnailInfo)
data class YTThumbnailInfo(val url: String)