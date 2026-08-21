package com.example.myapplication.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/** Body of `POST /me/play-history`. Urns look like `soundcloud:tracks:123456`. */
data class PlayHistoryRequest(
    @com.google.gson.annotations.SerializedName("track_urn") val trackUrn: String,
    @com.google.gson.annotations.SerializedName("context_urn") val contextUrn: String? = null
)

interface SoundCloudService {
    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("client_id") clientId: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): SoundCloudTracksResponse

    @GET("mixed-selections")
    suspend fun getMixedSelections(
        @Query("client_id") clientId: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("linked_partitioning") linkedPartitioning: Int,
        @Query("app_version") appVersion: String,
        @Query("app_locale") appLocale: String
    ): MixedSelectionsResponse

    @GET("system-playlists/{permalink}")
    suspend fun getSystemPlaylist(
        @Path("permalink") permalink: String,
        @Query("client_id") clientId: String,
        @Query("app_version") appVersion: String,
        @Query("app_locale") appLocale: String
    ): SoundCloudSystemPlaylist

    @GET("tracks")
    suspend fun getTracksByIds(
        @Query("ids") ids: String,
        @Query("client_id") clientId: String
    ): TracksByIdsResponse

    @GET("tracks/{id}")
    suspend fun getTrack(
        @Path("id") id: Long,
        @Query("client_id") clientId: String
    ): SoundCloudTrack

    @GET
    suspend fun resolveTranscoding(
        @Url transcodingUrl: String,
        @Query("client_id") clientId: String,
        @Query("track_authorization") trackAuthorization: String
    ): SoundCloudStreamResponse

    @retrofit2.http.PUT("users/{user_id}/track_likes/{track_id}")
    suspend fun likeTrack(
        @retrofit2.http.Path("user_id") userId: String,
        @retrofit2.http.Path("track_id") trackId: Long,
        @retrofit2.http.Query("client_id") clientId: String
    ): retrofit2.Response<Unit>

    @retrofit2.http.DELETE("users/{user_id}/track_likes/{track_id}")
    suspend fun unlikeTrack(
        @retrofit2.http.Path("user_id") userId: String,
        @retrofit2.http.Path("track_id") trackId: Long,
        @retrofit2.http.Query("client_id") clientId: String
    ): retrofit2.Response<Unit>

    /**
     * Adds a track to the account's listening history — the signal SoundCloud's recommendations
     * are built from, alongside likes and follows. Without it the app only ever reads, so the
     * mixes it is served never learn anything and keep returning the same tracks.
     *
     * `context_urn` is the playlist or system playlist the track was played from, when there is
     * one; SoundCloud uses it to understand where listening happens.
     */
    @retrofit2.http.POST("me/play-history")
    suspend fun addToPlayHistory(
        @retrofit2.http.Body body: PlayHistoryRequest,
        @retrofit2.http.Query("client_id") clientId: String,
        @retrofit2.http.Query("app_version") appVersion: String = SoundCloudApi.APP_VERSION
    ): retrofit2.Response<Unit>

    @retrofit2.http.GET("me/play-history/tracks")
    suspend fun getPlayHistory(
        @retrofit2.http.Query("client_id") clientId: String,
        @retrofit2.http.Query("limit") limit: Int = 25
    ): retrofit2.Response<okhttp3.ResponseBody>

    @retrofit2.http.GET("me")
    suspend fun getMe(
        @retrofit2.http.Query("client_id") clientId: String
    ): SoundCloudMeResponse

    @retrofit2.http.GET("users/{user_id}/track_likes")
    suspend fun getLikedTracks(
        @retrofit2.http.Path("user_id") userId: String,
        @retrofit2.http.Query("client_id") clientId: String,
        @retrofit2.http.Query("limit") limit: Int = 50,
        @retrofit2.http.Query("offset") offset: String? = null
    ): SoundCloudLikesResponse

    @retrofit2.http.GET
    suspend fun getLikedTracksByUrl(
        @retrofit2.http.Url url: String
    ): SoundCloudLikesResponse

    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") userId: Long,
        @Query("client_id") clientId: String
    ): SoundCloudUser

    @GET("users/{id}/tracks")
    suspend fun getUserTracks(
        @Path("id") userId: Long,
        @Query("client_id") clientId: String,
        @Query("limit") limit: Int = 30
    ): SoundCloudTracksResponse

    @GET("resolve")
    suspend fun resolveUrl(
        @Query("url") url: String,
        @Query("client_id") clientId: String
    ): SoundCloudUser

    @GET("stream/users/{id}")
    suspend fun getStreamUserTracks(
        @Path("id") userId: Long,
        @Query("client_id") clientId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): SoundCloudStreamUserResponse
}
