package io.github.garminaicoach

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.garminaicoach.domain.*
import io.github.garminaicoach.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class CoachUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun permissionsStartEmptyAndRequestOnlySelectedType() {
        var requested = emptySet<Metric>()
        compose.setContent {
            CoachTheme { CoachScreen(CoachUiState(Availability.AVAILABLE, accessVerified = true), {}, { requested = it }, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("数据权限").performClick()
        compose.onNodeWithTag("request-permissions").assertIsNotEnabled()
        Metric.entries.forEach { compose.onNodeWithTag("select-${it.name}").assertIsOff() }
        compose.onNodeWithTag("select-STEPS").performClick()
        compose.onNodeWithTag("request-permissions").performClick()
        assertEquals(setOf(Metric.STEPS), requested)
    }
    @Test fun unavailableShowsExplanationAndNoInventedData() {
        compose.setContent {
            CoachTheme { CoachScreen(CoachUiState(Availability.UNAVAILABLE, accessVerified = true), {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("此设备无法使用 Health Connect").assertExists()
        compose.onNodeWithTag("value-STEPS").assertTextEquals("— 步")
    }
}
