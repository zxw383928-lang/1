package io.github.garminaicoach

import io.github.garminaicoach.domain.*
import io.github.garminaicoach.ui.displayValue
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun sevenLocalDaysRespectDst() {
        val window = ReadWindow.lastSevenDays(Instant.parse("2026-03-10T16:00:00Z"), ZoneId.of("America/New_York"))
        assertEquals(7, window.days.size)
        val dst = window.days.first { it.date == LocalDate.parse("2026-03-08") }
        assertEquals(23, Duration.between(dst.start, dst.end).toHours())
        assertEquals(Instant.parse("2026-03-10T16:00:00Z"), window.end)
    }
    @Test fun midnightDoesNotCreateFutureData() {
        val now = Instant.parse("2026-10-08T16:00:00Z")
        val window = ReadWindow.lastSevenDays(now, ZoneId.of("Asia/Shanghai"))
        assertEquals(window.days.last().start, window.days.last().end)
        assertTrue(window.days.all { it.end <= now })
    }
    @Test fun rereadsKeepNewestVersionAndKeepOriginsSeparate() {
        val old = record("a", "provider.one", 12.0)
        val changed = old.copy(value = 15.0, modified = old.modified.plusSeconds(1))
        val another = record("a", "provider.two", 12.0)
        val unique = deduplicate(listOf(old, changed, another, old))
        assertEquals(2, unique.size)
        assertEquals(15.0, unique.first { it.origin == "provider.one" }.value!!, 0.0)
    }
    @Test fun missingRemainsMissingWhileActualZeroIsVisible() {
        assertEquals("—", displayValue(Metric.STEPS, null))
        assertEquals("0", displayValue(Metric.STEPS, 0.0))
        assertEquals("1.25", displayValue(Metric.DISTANCE, 1250.0))
        assertEquals("7.5", displayValue(Metric.SLEEP, 27000.0))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectEmptyRecordIds() { recordKey(Metric.STEPS, "p", "") }
}

internal fun record(id: String = "id", origin: String = "test.source", value: Double = 100.0, metric: Metric = Metric.STEPS): HealthRecord {
    val end = Instant.parse("2026-10-09T08:00:00Z")
    return HealthRecord(recordKey(metric, origin, id), metric, origin, id, end.minusSeconds(60), end, end, value, "{}")
}
