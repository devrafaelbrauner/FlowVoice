package dev.rafaelbrauner.flowvoice.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartedActivitiesTest {

    @Test
    fun leavingTheAppEndsTheForeground() {
        val activities = StartedActivities()
        activities.onStarted()
        assertTrue(activities.any)
        activities.onStopped(changingConfigurations = false)
        assertFalse(activities.any)
    }

    @Test
    fun rotationNeverReportsTheAppAsGone() {
        val activities = StartedActivities()
        activities.onStarted()
        activities.onStopped(changingConfigurations = true)
        assertTrue(activities.any)
        activities.onStarted()
        assertTrue(activities.any)
        activities.onStopped(changingConfigurations = false)
        assertFalse(activities.any)
    }

    @Test
    fun aSecondInstanceKeepsTheForegroundUntilBothStop() {
        val activities = StartedActivities()
        activities.onStarted()
        activities.onStarted()
        activities.onStopped(changingConfigurations = false)
        assertTrue(activities.any)
        activities.onStopped(changingConfigurations = false)
        assertFalse(activities.any)
    }
}
