package com.example.myapplication.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

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
}
