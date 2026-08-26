package com.samsunggalaxy.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * EPIC-09 T09.2 — the parts of HealthConnectManager that don't need a real HealthConnectClient
 * or device. `isAvailable` must short-circuit on the SDK_INT gate before touching the library
 * (the android.jar stub used for JVM unit tests has Build.VERSION.SDK_INT == 0, so any code
 * path that calls into HealthConnectClient here would throw — the gate is what's under test).
 */
class HealthConnectManagerTest {

    @Test
    fun isAvailable_belowMinSdk_returnsFalseWithoutTouchingHealthConnectClient() {
        // android.jar stub: Build.VERSION.SDK_INT defaults to 0, always below the 26 gate.
        assertFalse(HealthConnectManager.isAvailable(FakeContext))
    }

    @Test
    fun requiredPermissions_isReadAndWriteWeightOnly() {
        val permissions = HealthConnectManager.requiredPermissions()
        assertEquals(2, permissions.size)
    }

    private object FakeContext : android.content.ContextWrapper(null)
}
