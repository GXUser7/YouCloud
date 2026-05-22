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
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
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

    suspend fun fetchSoundCloudClientId(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        try {
            val mainRequest = okhttp3.Request.Builder()
                .url("https://soundcloud.com")
                .header("User-Agent", userAgent)
                .build()

            client.newCall(mainRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val html = response.body?.string() ?: return@withContext null

                val scriptRegex = """<script[^>]+src="([^"]+)"""".toRegex()
                val scriptUrls = scriptRegex.findAll(html)
                    .map { it.groupValues[1] }
                    .toList()
                    .reversed()

                val clientIdRegex = """client_id\s*:\s*"([0-9a-zA-Z]{32})"""".toRegex()
                for (url in scriptUrls) {
                    try {
                        val scriptRequest = okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .build()
                        client.newCall(scriptRequest).execute().use { scriptResponse ->
                            if (scriptResponse.isSuccessful) {
                                val js = scriptResponse.body?.string() ?: return@use
                                val match = clientIdRegex.find(js)
                                if (match != null) {
                                    val extracted = match.groupValues[1]
                                    if (extracted.isNotBlank()) {
                                        return@withContext extracted
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and try next script
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
