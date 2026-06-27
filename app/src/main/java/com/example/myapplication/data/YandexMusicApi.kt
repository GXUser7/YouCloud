package com.example.myapplication.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface YandexMusicService {
    @GET("search")
    suspend fun searchTracks(
        @Query("text") text: String,
        @Query("type") type: String = "track",
        @Query("page") page: Int = 0
    ): YandexSearchResponse

    @GET("tracks/{trackId}/download-info")
    suspend fun getDownloadInfo(
        @Path("trackId") trackId: String
    ): YandexDownloadInfoResponse

    @GET("artists/{artistId}/brief-info")
    suspend fun getArtistBriefInfo(
        @Path("artistId") artistId: String
    ): YandexArtistBriefResponse

    @GET("artists/{artistId}/tracks")
    suspend fun getArtistTracks(
        @Path("artistId") artistId: String,
        @Query("page") page: Int = 0,
        @Query("page-size") pageSize: Int = 100
    ): YandexArtistTracksResponse

    @GET("account/status")
    suspend fun getAccountStatus(): YandexAccountStatusResponse

    @GET("users/{userId}/playlists/list")
    suspend fun getUserPlaylists(
        @Path("userId") userId: Long
    ): YandexPlaylistsResponse

    @GET("users/{userId}/playlists/{playlistKind}")
    suspend fun getPlaylistDetail(
        @Path("userId") userId: Long,
        @Path("playlistKind") playlistKind: Long
    ): YandexPlaylistDetailResponse

    @POST("users/{userId}/likes/tracks/add-multiple")
    @FormUrlEncoded
    suspend fun likeTrack(
        @Path("userId") userId: Long,
        @Field("track-ids") trackIds: String
    ): YandexLikeResponse

    @POST("users/{userId}/likes/tracks/remove")
    @FormUrlEncoded
    suspend fun unlikeTrack(
        @Path("userId") userId: Long,
        @Field("track-ids") trackIds: String
    ): YandexLikeResponse

    @GET("users/{userId}/likes/tracks")
    suspend fun getLikedTracks(
        @Path("userId") userId: Long
    ): YandexLikedTracksResponse

    @POST("tracks")
    @FormUrlEncoded
    suspend fun getTracksDetails(
        @Field("track-ids") trackIds: String
    ): YandexTracksResponse

    @GET("albums/{albumId}/with-tracks")
    suspend fun getAlbumWithTracks(
        @Path("albumId") albumId: Long
    ): YandexAlbumDetailResponse
}

object YandexMusicApi {
    private const val BASE_URL = "https://api.music.yandex.net/"
    private const val SALT = "XGRlBW9FXlekgbPrRHuSiA"

    fun createService(tokenProvider: () -> String): YandexMusicService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("User-Agent", "Yandex-Music-API")
                    .header("Accept", "application/json")
                
                val token = tokenProvider().trim()
                if (token.isNotEmpty()) {
                    requestBuilder.header("Authorization", "OAuth $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YandexMusicService::class.java)
    }

    fun generateDirectLink(host: String, path: String, ts: String, s: String): String {
        val normalizedPath = if (path.startsWith("/")) path.substring(1) else path
        val signatureSource = SALT + normalizedPath + s
        val md5Hash = md5(signatureSource)
        return "https://$host/get-mp3/$md5Hash/$ts$path"
    }

    suspend fun resolveTrackStream(trackId: String, token: String): String? {
        return try {
            val service = createService { token }
            val response = service.getDownloadInfo(trackId)
            val bestItem = response.result.orEmpty().firstOrNull { it.codec == "mp3" } ?: response.result.orEmpty().firstOrNull()
                ?: return null
            
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(bestItem.downloadInfoUrl)
                .header("Authorization", "OAuth $token")
                .build()
            
            val xmlStringBuilder = java.lang.StringBuilder()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    xmlStringBuilder.append(response.body?.string() ?: "")
                }
            }
            val xmlString = xmlStringBuilder.toString()
            if (xmlString.isEmpty()) return null
            
            val regex = { tag: String ->
                val r = "<$tag>(.*?)</$tag>".toRegex()
                r.find(xmlString)?.groupValues?.get(1).orEmpty()
            }
            
            val host = regex("host")
            val path = regex("path")
            val ts = regex("ts")
            val s = regex("s")
            
            if (host.isNotEmpty() && path.isNotEmpty() && ts.isNotEmpty() && s.isNotEmpty()) {
                generateDirectLink(host, path, ts, s)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun md5(input: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        val bytes = md5.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
