package com.example.aiknowledgefeed.backend.api

import com.example.aiknowledgefeed.backend.models.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface PaperViewApi {
    @GET("v1/profile") suspend fun profile(): Profile
    @PUT("v1/profile") suspend fun profile(@Body profile: Profile): Profile
    @GET("v1/knowledge") suspend fun knowledge(): List<Knowledge>
    @PUT("v1/knowledge/{id}") suspend fun update(@Path("id") id: String, @Body body: KnowledgeUpdate): Knowledge
    @DELETE("v1/knowledge/{id}") suspend fun deleteKnowledge(@Path("id") id: String)
    @GET("v1/documents") suspend fun documents(): List<Document>
    @Multipart @POST("v1/documents") suspend fun upload(@Part file: MultipartBody.Part): Document
    @POST("v1/documents/{id}/analyze") suspend fun analyze(@Path("id") id: String, @Body consent: Consent): Document
    @DELETE("v1/documents/{id}") suspend fun deleteDocument(@Path("id") id: String)
    @GET("v1/feed") suspend fun feed(@Query("timezone") timezone: String): Feed
    @POST("v1/feed/generate") suspend fun generate(@Body body: Generation): Feed
    @POST("v1/papers/{id}/understood") suspend fun understood(@Path("id") id: String): Knowledge
    @GET("v1/papers/{id}") suspend fun paper(@Path("id") id: String): Paper
    @POST("v1/llm/test") suspend fun test(): ConnectionResult
}
