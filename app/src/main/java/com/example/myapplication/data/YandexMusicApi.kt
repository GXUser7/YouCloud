package com.example.myapplication.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
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

    /** Everything at once — tracks, albums, artists, playlists — for a query's first page. */
    @GET("search")
    suspend fun searchAll(
        @Query("text") text: String,
        @Query("type") type: String = "all",
        @Query("page") page: Int = 0,
        @Query("nocorrect") noCorrect: Boolean = false,
        @Query("playlist-in-best") playlistInBest: Boolean = true
    ): YandexSearchResponse

    /**
     * Where to download a track's lyrics. `format=LRC` gives them with line timings. Signed
     * like the Android app signs it (see [YandexMusicApi.lyricsSign]), and the server only checks
     * that signature for the Android client, so the request has to say it is one.
     */
    @GET("tracks/{trackId}/lyrics")
    @Headers("X-Yandex-Music-Client: YandexMusicAndroid/24023621")
    suspend fun getLyrics(
        @Path("trackId") trackId: String,
        @Query("format") format: String,
        @Query("timeStamp") timeStamp: Long,
        @Query("sign") sign: String
    ): YandexLyricsResponse

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

    /**
     * Yandex Music's home, in [blocks]: the playlists made for the listener (of the day, Дежавю,
     * Премьера, Тайник…), new releases and playlists, the chart. Read by [YandexLanding].
     */
    @GET("landing3")
    suspend fun landing(@Query("blocks") blocks: String = YandexLanding.BLOCKS): com.google.gson.JsonObject

    /** Every new release, as album ids; its home block shows only the first few. */
    @GET("landing3/new-releases")
    suspend fun newReleases(): com.google.gson.JsonObject

    /** Every new playlist, as owner and kind. */
    @GET("landing3/new-playlists")
    suspend fun newPlaylists(): com.google.gson.JsonObject

    /** The feed: among the rest, every playlist made for the listener (`generatedPlaylists`). */
    @GET("feed")
    suspend fun feed(): com.google.gson.JsonObject

    @POST("albums")
    @FormUrlEncoded
    suspend fun albums(@Field("album-ids") albumIds: String): com.google.gson.JsonObject

    @POST("playlists/list")
    @FormUrlEncoded
    suspend fun playlists(@Field("playlist-ids") playlistIds: String): com.google.gson.JsonObject

    /** "Не рекомендовать": the track is kept out of the wave and the radio from now on. */
    @POST("users/{userId}/dislikes/tracks/add-multiple")
    @FormUrlEncoded
    suspend fun dislikeTrack(
        @Path("userId") userId: Long,
        @Field("track-ids") trackIds: String
    ): com.google.gson.JsonObject

    @GET("users/{userId}/likes/tracks")
    suspend fun getLikedTracks(
        @Path("userId") userId: Long
    ): YandexLikedTracksResponse

    @POST("tracks")
    @FormUrlEncoded
    suspend fun getTracksDetails(
        @Field("track-ids") trackIds: String
    ): YandexTracksResponse

    @GET("artists/{artistId}/blocks/artist-clips")
    suspend fun getArtistClips(
        @Path("artistId") artistId: String
    ): com.google.gson.JsonObject

    @GET("albums/{albumId}/with-tracks")
    suspend fun getAlbumWithTracks(
        @Path("albumId") albumId: Long
    ): YandexAlbumDetailResponse

    /** A new radio session (Rotor) from [YandexRotorSessionRequest.seeds], with its first batch. */
    @POST("rotor/session/new")
    suspend fun rotorSessionNew(
        @Body request: YandexRotorSessionRequest
    ): YandexRotorSessionResponse

    /** The radio's next batch; the queue is what it has already given, so nothing repeats. */
    @POST("rotor/session/{sessionId}/tracks")
    suspend fun rotorSessionTracks(
        @Path("sessionId") sessionId: String,
        @Body request: YandexRotorQueueRequest
    ): YandexRotorSessionResponse

    /** What was done with the radio's tracks: started, finished, skipped. */
    @POST("rotor/session/{sessionId}/feedback")
    suspend fun rotorSessionFeedback(
        @Path("sessionId") sessionId: String,
        @Body feedback: YandexRotorFeedback
    ): com.google.gson.JsonObject
}

object YandexMusicApi {
    private const val BASE_URL = "https://api.music.yandex.net/"
    private const val SALT = "XGRlBW9FXlekgbPrRHuSiA"

    // The Android app's key for signed requests (MarshalX/yandex-music-api, utils/sign_request.py).
    private const val SIGN_KEY = "p93jhgh689SBReK6ghtw62"

    /** HMAC-SHA256 of the numeric track id followed by [timestamp], base64. */
    fun lyricsSign(trackId: String, timestamp: Long): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(SIGN_KEY.toByteArray(), "HmacSHA256"))
        val digest = mac.doFinal("${trackId.substringBefore(':')}$timestamp".toByteArray())
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }

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

    /**
     * @param lightest the smallest file rather than the best-sounding one: for when the sound is
     *   only analysed, not listened to.
     */
    suspend fun resolveTrackStream(trackId: String, token: String, lightest: Boolean = false): String? {
        return try {
            val service = createService { token }
            val response = service.getDownloadInfo(trackId)
            val items = response.result.orEmpty()
            val bestItem = (if (lightest) items.minByOrNull { it.bitrateInKbps } else null)
                ?: items.firstOrNull { it.codec == "mp3" } ?: items.firstOrNull()
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
