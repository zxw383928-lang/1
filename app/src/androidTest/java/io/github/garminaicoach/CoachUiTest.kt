package io.github.garminaicoach

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test fun unverifiedAccessClearsConfirmedGrantsAndDisablesRequests() {
        val state = mutableStateOf(CoachUiState(
            availability = Availability.AVAILABLE,
            granted = setOf(Metric.STEPS),
            accessVerified = true,
        ))
        var requests = 0
        compose.setContent {
            CoachTheme { CoachScreen(state.value, {}, { requests++ }, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("open-permissions").performClick()
        compose.onNodeWithTag("select-STEPS").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("select-HEART_RATE").performClick()
        compose.onNodeWithTag("permissions").performScrollToNode(hasTestTag("request-permissions"))
        compose.onNodeWithTag("request-permissions").assertIsEnabled()

        compose.runOnIdle { state.value = state.value.copy(accessVerified = false) }
        Metric.entries.forEach {
            compose.onNodeWithTag("permissions").performScrollToNode(hasTestTag("select-${it.name}"))
            compose.onNodeWithTag("select-${it.name}").assertIsOff().assertIsNotEnabled()
        }
        compose.onNodeWithTag("permissions").performScrollToNode(hasTestTag("request-permissions"))
        compose.onNodeWithTag("request-permissions").assertIsNotEnabled().performClick()
        compose.onNodeWithText("已授权；可在系统设置撤销").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, requests) }
    }

    @Test fun narrowScreenWithLargeTextKeepsActionsAndMetricStatusReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    CoachTheme { CoachScreen(CoachUiState(Availability.AVAILABLE, accessVerified = true), {}, {}, {}, {}, {}, {}) }
                }
            }
        }
        compose.onNodeWithTag("dashboard").performScrollToNode(hasTestTag("open-permissions"))
        compose.onNodeWithTag("open-permissions").assertIsDisplayed()
        val refresh = compose.onNodeWithTag("refresh").getUnclippedBoundsInRoot()
        val permissions = compose.onNodeWithTag("open-permissions").getUnclippedBoundsInRoot()
        assertTrue("Large text should wrap action buttons onto separate rows", permissions.top >= refresh.bottom)

        compose.onNodeWithTag("dashboard").performScrollToNode(hasTestTag("metric-SLEEP"))
        compose.onNodeWithTag("title-SLEEP").assertIsDisplayed()
        compose.onNodeWithTag("status-SLEEP").assertIsDisplayed()
        val title = compose.onNodeWithTag("title-SLEEP").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("status-SLEEP").getUnclippedBoundsInRoot()
        assertTrue("Metric title and status must not overlap", status.top >= title.bottom || status.left >= title.right)

        compose.onNodeWithTag("dashboard").performScrollToNode(hasTestTag("open-permissions"))
        compose.onNodeWithTag("open-permissions").performClick()
        compose.onNodeWithTag("permissions").performScrollToNode(hasTestTag("select-SLEEP"))
        compose.onNodeWithTag("select-SLEEP").assertIsDisplayed().performClick()
        compose.onNodeWithTag("permissions").performScrollToNode(hasTestTag("request-permissions"))
        compose.onNodeWithTag("request-permissions").assertIsDisplayed().assertIsEnabled()
    }
}
