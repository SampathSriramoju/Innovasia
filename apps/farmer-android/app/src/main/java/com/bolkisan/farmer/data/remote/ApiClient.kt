package com.bolkisan.farmer.data.remote

import com.bolkisan.farmer.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val DEV_HOST = "10.0.2.2"
    private const val SECONDARY_HOST = "192.168.0.150"

    // Environment-driven Base URL: Production uses strict HTTPS, development allows local emulator loopback
    val BASE_URL: String = if (BuildConfig.DEBUG) {
        "http://10.0.2.2:8000/api/v1/"
    } else {
        "https://api.bolkisan.gov.in/api/v1/"
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
        redactHeader("Cookie")
    }

    // Dynamic Host Fallback: If 10.0.2.2 fails (e.g. in BlueStacks), automatically retry with host LAN IP
    private val hostFallbackInterceptor = Interceptor { chain ->
        val original = chain.request()
        try {
            chain.proceed(original)
        } catch (e: Exception) {
            val fallbackUrl = original.url.newBuilder().host(SECONDARY_HOST).build()
            val fallbackRequest = original.newBuilder().url(fallbackUrl).build()
            chain.proceed(fallbackRequest)
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(hostFallbackInterceptor)
        .addInterceptor(logging)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: BolKisanApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BolKisanApiService::class.java)
    }
}
