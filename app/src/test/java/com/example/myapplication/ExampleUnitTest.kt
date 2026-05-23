package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @org.junit.Test
    fun testFetchClientId() {
        kotlinx.coroutines.runBlocking {
            val clientId = com.example.myapplication.data.SoundCloudApi.fetchSoundCloudClientId()
            println("EXTRACTED CLIENT ID: $clientId")
            assertNotNull("Client ID should not be null", clientId)
        }
    }
}