package com.aichallenge.aiagentapp.data

import com.aichallenge.aiagentapp.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface DeepSeekApi {
    @POST("chat/completions")
    suspend fun createChatCompletion(@Body body: DeepSeekRequest): Response<DeepSeekResponse>
}

private fun buildApi(baseUrl: String, apiKey: String): DeepSeekApi {
    val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }
    val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    return retrofit.create(DeepSeekApi::class.java)
}

fun createDeepSeekApi(): DeepSeekApi =
    buildApi("https://api.deepseek.com/", BuildConfig.DEEPSEEK_API_KEY)

fun createRouterAiApi(): DeepSeekApi =
    buildApi("https://routerai.ru/api/v1/", BuildConfig.ROUTERAI_API_KEY)
