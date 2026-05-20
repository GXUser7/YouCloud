package com.example.myapplication.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object SoundCloudApi {
    const val BASE_URL = "https://api-v2.soundcloud.com/"
    const val APP_VERSION = "1778677443"

    fun createService(oauthTokenProvider: () -> String): SoundCloudService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Origin", "https://soundcloud.com")
                    .header("Referer", "https://soundcloud.com/")

                val oauthToken = oauthTokenProvider().trim()
                if (oauthToken.isNotEmpty()) {
                    requestBuilder.header("Authorization", "OAuth $oauthToken")
                }

                chain.proceed(requestBuilder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SoundCloudService::class.java)
    }
}
