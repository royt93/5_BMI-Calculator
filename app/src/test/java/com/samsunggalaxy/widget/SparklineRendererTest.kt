package com.samsunggalaxy.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** EPIC-09 T09.1 — pure-function coverage for the widget sparkline's normalization math. */
class SparklineRendererTest {

    @Test
    fun normalize_emptyList_returnsEmpty() {
        assertEquals(emptyList<Float>(), SparklineRenderer.normalize(emptyList()))
    }

    @Test
    fun normalize_singleValue_returnsMidpoint() {
        assertEquals(listOf(0.5f), SparklineRenderer.normalize(listOf(70.0)))
    }

    @Test
    fun normalize_risingTrend_mapsMinToZeroAndMaxToOne() {
        val result = SparklineRenderer.normalize(listOf(70.0, 72.0, 75.0))
        assertEquals(0f, result[0], 0.001f)
        assertEquals(1f, result[2], 0.001f)
        assertEquals(0.4f, result[1], 0.001f) // (72-70)/(75-70) = 0.4
    }

    @Test
    fun normalize_fallingTrend_mapsFirstHighLastLow() {
        val result = SparklineRenderer.normalize(listOf(80.0, 75.0, 70.0))
        assertEquals(1f, result[0], 0.001f)
        assertEquals(0f, result[2], 0.001f)
    }

    @Test
    fun normalize_allSameValue_returnsMidpointForEach() {
        val result = SparklineRenderer.normalize(listOf(70.0, 70.0, 70.0))
        result.forEach { assertEquals(0.5f, it, 0.001f) }
    }

    @Test
    fun render_fewerThanTwoValues_returnsNull() {
        assertEquals(null, SparklineRenderer.render(emptyList(), 100, 40, 0xFF000000.toInt()))
        assertEquals(null, SparklineRenderer.render(listOf(70.0), 100, 40, 0xFF000000.toInt()))
    }
}
