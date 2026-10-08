package com.aerocine.camera.core.spring

import kotlin.math.abs

/**
 * Mesin kalkulasi fisika pegas teredam kritis (Critically Damped Harmonic Oscillator)
 * untuk menghasilkan pergerakan zoom berbobot dan elastis setara sistem kamera iOS.
 *
 * Persamaan diferensial:
 * z''(t) + 2 * zeta * omega_n * z'(t) + omega_n^2 * (z(t) - target) = 0
 * Dengan zeta = 1.0 (Critical Damping), sistem mencapai target tanpa osilasi bolak-balik.
 */
class ZoomSpringEngine(
    initialValue: Float = 1.0f,
    private val naturalFrequency: Float = 16.0f, // Kecepatan respons alami pegas (rad/s)
    private val dampingRatio: Float = 1.0f      // 1.0 = Redaman kritis (tanpa getaran balik)
) {
    var currentValue: Float = initialValue
        private set

    var targetValue: Float = initialValue
        private set

    private var velocity: Float = 0.0f
    private val threshold: Float = 0.001f

    fun setTarget(target: Float) {
        this.targetValue = target
    }

    fun snapTo(value: Float) {
        this.currentValue = value
        this.targetValue = value
        this.velocity = 0.0f
    }

    /**
     * Memperbarui posisi nilai zoom berdasarkan selisih waktu delta time (dt) dalam detik.
     * Mengembalikan true jika nilai masih bergerak, atau false jika sudah stabil di target.
     */
    fun update(dt: Float): Boolean {
        if (isAtRest()) {
            currentValue = targetValue
            velocity = 0.0f
            return false
        }

        // Batasi rentang dt untuk mencegah instabilitas numerik jika terjadi lonjakan frame
        val clampedDt = dt.coerceIn(0.001f, 0.05f)

        // Integrasi semi-implisit Euler
        val displacement = currentValue - targetValue
        val acceleration = -2.0f * dampingRatio * naturalFrequency * velocity -
                naturalFrequency * naturalFrequency * displacement

        velocity += acceleration * clampedDt
        currentValue += velocity * clampedDt

        if (isAtRest()) {
            currentValue = targetValue
            velocity = 0.0f
            return false
        }

        return true
    }

    fun isAtRest(): Boolean {
        val displacement = abs(currentValue - targetValue)
        val speed = abs(velocity)
        return displacement < threshold && speed < threshold
    }
}
