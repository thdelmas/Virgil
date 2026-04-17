package com.virgil.app.debug

/**
 * Canned accelerometer magnitude traces for testing [com.virgil.app.analysis.FallDetectionAlgorithm]
 * end-to-end through the running service. Units: m/s^2. 1g ≈ 9.8.
 *
 * Each trace is a pair of (magnitudes, delays-in-ms-between-samples).
 * Delays mirror SENSOR_DELAY_GAME (~20ms / 50Hz).
 */
object DebugTraces {

    val ALL: Map<String, Trace> = mapOf(
        "fall" to classicFall(),
        "sit_hard" to sitHard(),
        "walk" to walk(),
        "drop_on_couch" to dropOnCouch(),
    )

    data class Trace(val magnitudes: FloatArray, val delays: LongArray) {
        init {
            require(magnitudes.size == delays.size) { "magnitudes and delays must align" }
        }
    }

    private fun classicFall(): Trace {
        val mags = mutableListOf<Float>()
        val delays = mutableListOf<Long>()

        repeat(20) { mags += 9.8f; delays += 20L }
        repeat(8) { mags += 3.0f; delays += 20L }
        mags += 38.0f; delays += 20L
        mags += 42.0f; delays += 20L
        mags += 20.0f; delays += 20L
        mags += 12.0f; delays += 20L
        repeat(60) { mags += 9.8f; delays += 20L }

        return Trace(mags.toFloatArray(), delays.toLongArray())
    }

    private fun sitHard(): Trace {
        val mags = mutableListOf<Float>()
        val delays = mutableListOf<Long>()
        repeat(20) { mags += 9.8f; delays += 20L }
        repeat(5) { mags += 7.0f; delays += 20L }
        mags += 16.0f; delays += 20L
        mags += 18.0f; delays += 20L
        repeat(40) { mags += 9.8f; delays += 20L }
        return Trace(mags.toFloatArray(), delays.toLongArray())
    }

    private fun walk(): Trace {
        val mags = mutableListOf<Float>()
        val delays = mutableListOf<Long>()
        repeat(200) { i ->
            mags += if (i % 25 < 5) 14.5f else 9.2f
            delays += 20L
        }
        return Trace(mags.toFloatArray(), delays.toLongArray())
    }

    private fun dropOnCouch(): Trace {
        val mags = mutableListOf<Float>()
        val delays = mutableListOf<Long>()
        repeat(20) { mags += 9.8f; delays += 20L }
        repeat(10) { mags += 2.5f; delays += 20L }
        mags += 22.0f; delays += 20L
        mags += 18.0f; delays += 20L
        repeat(60) { mags += 9.8f; delays += 20L }
        return Trace(mags.toFloatArray(), delays.toLongArray())
    }
}
