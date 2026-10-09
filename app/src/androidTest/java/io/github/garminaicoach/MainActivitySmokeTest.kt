package io.github.garminaicoach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Runs the actual application and official availability/permission inspection on API 36. */
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun applicationStartsWithOfficialHealthConnectAndMinimalPermissionUi() {
        compose.onNodeWithTag("dashboard").assertExists()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText("Health Connect 可用").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("数据权限").performClick()
        compose.onNodeWithTag("request-permissions").assertIsNotEnabled()
        MetricNames.forEach { compose.onNodeWithTag("select-$it").assertIsOff() }
    }

    private val MetricNames = listOf("STEPS", "HEART_RATE", "DISTANCE", "SLEEP")
}
