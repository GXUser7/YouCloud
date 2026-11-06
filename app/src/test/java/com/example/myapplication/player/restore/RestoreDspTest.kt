package com.example.myapplication.player.restore

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * The Kotlin restoration against the Python it was trained with (tools/restore/vectors.py): a
 * 128 kbps MP3's cutoff, one frame's model input, the light model's answer, the gains after it.
 */
class RestoreDspTest {
    private val vectors = JSONObject(javaClass.getResource("/restore/vectors.json")!!.readText())
    private val rate = vectors.getInt("rate")

    private fun floats(name: String): FloatArray = vectors.getJSONArray(name).let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }

    private fun light(): RestoreModel = File("src/main/assets/restore/light-v1.ycrm").inputStream().use(RestoreModel::read)

    @Test
    fun findsTheCutoffWherePythonDoes() {
        val cutoff = RestoreDsp.detectCutoff(floats("longDb"), rate)
        assertNotNull(cutoff)
        assertEquals(vectors.getDouble("cutoff").toFloat(), cutoff!!, 1f)
    }

    @Test
    fun hearsTheFrameAsPythonDoes() {
        val model = light()
        val cutoff = vectors.getDouble("cutoff").toFloat()
        val kc = RestoreDsp.cutoffBin(cutoff, rate)
        val input = FloatArray(model.inputs)
        val anchor = RestoreDsp.features(floats("power"), cutoff, kc, RestoreDsp.Bands(model.edges, rate), rate, input)
        assertEquals(vectors.getDouble("anchor").toFloat(), anchor, 1e-3f)
        val expected = floats("features")
        for (i in expected.indices) assertEquals("input $i", expected[i], input[i], 1e-4f)
    }

    @Test
    fun answersAsPyTorchDoes() {
        val model = light()
        val out = FloatArray(model.bands)
        model.State().step(floats("features"), out)
        val expected = floats("modelOut")
        for (i in expected.indices) assertEquals("band $i", expected[i], out[i], 1e-4f)
    }

    @Test
    fun setsTheGainsAsPythonDoes() {
        val model = light()
        val cutoff = vectors.getDouble("cutoff").toFloat()
        val kc = RestoreDsp.cutoffBin(cutoff, rate)
        val top = RestoreDsp.topBin(rate)
        val bandGain = FloatArray(model.bands)
        val previous = FloatArray(top - kc) { Float.NaN }
        RestoreDsp.gains(
            floats("midRe"), floats("midIm"), kc, top, RestoreDsp.patchWidth(kc, rate),
            floats("modelOut"), vectors.getDouble("anchor").toFloat(), RestoreDsp.Bands(model.edges, rate),
            bandGain, previous
        )
        val expected = floats("gains")
        assertEquals(expected.size, previous.size)
        for (i in expected.indices) assertEquals("bin ${kc + i}", expected[i], previous[i], 0.01f)
    }
}
