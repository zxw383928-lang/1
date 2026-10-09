package io.github.garminaicoach

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.testing.AggregationResult
import androidx.health.connect.client.testing.stubs.Stub
import androidx.test.core.app.ApplicationProvider
import io.github.garminaicoach.data.healthconnect.*
import io.github.garminaicoach.domain.*
import java.time.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class HealthConnectSourceTest {
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private fun source(client: FakeHealthConnectClient, status: Int = HealthConnectClient.SDK_AVAILABLE) = HealthConnectSource(ApplicationProvider.getApplicationContext(), { client }, { status })

    @Test fun officialFakeReadsAllPagesAndUsesAggregateInsteadOfRawSum() = runTest {
        val fake = FakeHealthConnectClient()
        val permissions = fake.permissionController as FakePermissionController
        permissions.replaceGrantedPermissions(setOf(Metric.STEPS.readPermission()))
        val raw = (0 until 1001).map { index ->
            StepsRecord(now.minusSeconds(2000L - index), null, now.minusSeconds(1999L - index), null, 10, Metadata.autoRecorded(Device(type = Device.TYPE_WATCH)))
        }
        fake.insertRecords(raw)
        fake.overrides.aggregate = Stub {
            AggregationResult(dataOrigins = setOf(DataOrigin("test.source")), metrics = mapOf(StepsRecord.COUNT_TOTAL to 777L))
        }
        val snapshot = source(fake).readMetric(Metric.STEPS, ReadWindow.lastSevenDays(now, ZoneOffset.UTC))
        assertEquals(1001, snapshot.records.size)
        assertEquals(7, snapshot.days.size)
        assertTrue(snapshot.days.all { it.value == 777.0 })
        assertTrue(snapshot.records.all { it.recordId.isNotBlank() })
        assertEquals(setOf(Metric.STEPS), source(fake).grantedMetrics())
    }
    @Test fun emptyAggregateStaysNull() = runTest {
        val fake = FakeHealthConnectClient()
        (fake.permissionController as FakePermissionController).replaceGrantedPermissions(setOf(Metric.STEPS.readPermission()))
        fake.overrides.aggregate = Stub { AggregationResult() }
        val result = source(fake).readMetric(Metric.STEPS, ReadWindow.lastSevenDays(now, ZoneOffset.UTC))
        assertTrue(result.days.all { it.value == null })
        assertTrue(result.records.isEmpty())
    }
    @Test fun revokedPermissionPreventsApiRead() = runTest {
        val fake = FakeHealthConnectClient()
        (fake.permissionController as FakePermissionController).replaceGrantedPermissions(emptySet())
        fake.overrides.readRecords = Stub { fail("API must not be called"); null }
        try { source(fake).readMetric(Metric.STEPS, ReadWindow.lastSevenDays(now, ZoneOffset.UTC)); fail("Expected permission rejection") }
        catch (_: SecurityException) { /* expected */ }
    }
    @Test fun unavailableDoesNotCreateClient() = runTest {
        val source = HealthConnectSource(ApplicationProvider.getApplicationContext(), { error("Do not create client") }, { HealthConnectClient.SDK_UNAVAILABLE })
        assertEquals(Availability.UNAVAILABLE, source.availability())
        assertTrue(source.grantedMetrics().isEmpty())
    }
    @Test fun heartDistanceSleepKeepUnitsAndFullLocalPayload() = runTest {
        val fake = FakeHealthConnectClient(clock = Clock.fixed(now, ZoneOffset.UTC))
        (fake.permissionController as FakePermissionController).replaceGrantedPermissions(Metric.entries.map { it.readPermission() }.toSet())
        fake.insertRecords(listOf<Record>(
            HeartRateRecord(now.minusSeconds(120), null, now.minusSeconds(1), null,
                listOf(HeartRateRecord.Sample(now.minusSeconds(90), 60), HeartRateRecord.Sample(now.minusSeconds(30), 90)), Metadata.autoRecorded(Device(type = Device.TYPE_WATCH))),
            DistanceRecord(now.minusSeconds(120), null, now.minusSeconds(1), null, Length.meters(1500.0), Metadata.autoRecorded(Device(type = Device.TYPE_WATCH))),
            SleepSessionRecord(now.minusSeconds(28800), null, now.minusSeconds(1), null, Metadata.autoRecorded(Device(type = Device.TYPE_WATCH)),
                stages = listOf(SleepSessionRecord.Stage(now.minusSeconds(27000), now.minusSeconds(24000), SleepSessionRecord.STAGE_TYPE_DEEP)))
        ))
        fake.overrides.aggregate = Stub {
            AggregationResult(metrics = mapOf(
                HeartRateRecord.BPM_AVG to 75L,
                DistanceRecord.DISTANCE_TOTAL to Length.meters(1500.0),
                SleepSessionRecord.SLEEP_DURATION_TOTAL to Duration.ofHours(6),
            ))
        }
        val source = source(fake)
        val window = ReadWindow.lastSevenDays(now, ZoneOffset.UTC)
        val heart = source.readMetric(Metric.HEART_RATE, window)
        val distance = source.readMetric(Metric.DISTANCE, window)
        val sleep = source.readMetric(Metric.SLEEP, window)
        assertEquals(75.0, heart.records.single().value!!, 0.0)
        assertEquals(2, JSONObject(heart.records.single().payload).getJSONArray("samples").length())
        assertEquals(1500.0, distance.days.last().value!!, 0.0)
        assertEquals(21600.0, sleep.days.last().value!!, 0.0)
        assertEquals(1, JSONObject(sleep.records.single().payload).getJSONArray("stages").length())
    }
}
