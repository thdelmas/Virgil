package com.virgil.app.analysis

/**
 * Estimates the gravity vector from raw accelerometer samples with an
 * exponential low-pass filter — the standard substitute on hardware that
 * ships no dedicated gravity sensor (e.g. Galaxy A06). Feeding the result
 * into [FallDetectionAlgorithm.processGravity] restores the
 * pocket-insertion rejection on such devices.
 *
 * During free-fall and impact the raw signal is nothing like gravity, but
 * the algorithm latches the pre-event orientation while idle and reads the
 * post-event orientation at stillness confirmation — ≥300ms after impact,
 * by which point the filter has re-converged (~90% in ~10 samples at 50Hz).
 */
class GravityEstimator(private val alpha: Float = DEFAULT_ALPHA) {

    var x: Float = 0f
        private set
    var y: Float = 0f
        private set
    var z: Float = 0f
        private set

    private var initialized = false

    fun process(ax: Float, ay: Float, az: Float) {
        if (!initialized) {
            x = ax; y = ay; z = az
            initialized = true
            return
        }
        x = alpha * x + (1 - alpha) * ax
        y = alpha * y + (1 - alpha) * ay
        z = alpha * z + (1 - alpha) * az
    }

    companion object {
        const val DEFAULT_ALPHA = 0.8f
    }
}
