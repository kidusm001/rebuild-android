package com.rebuild.ui

import com.rebuild.domain.BodyweightEntry
import com.rebuild.domain.MassUnit
import com.rebuild.domain.weightWeeks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BodyChartTest {
    private fun weighIn(date: String, value: Double) =
        BodyweightEntry("w-$date", LocalDate.parse(date).toEpochDay() * 86_400_000L, date, value, MassUnit.KG)

    @Test fun aWeeksPointSitsOnItsWeighInsAndAMissingWeekBreaksTheLine() {
        // Mon 5 Oct and Wed 7 Oct; Mon 12 Oct only (this week, so far); nothing 19–25 Oct; Tue 27 Oct.
        val weeks = weightWeeks(listOf(weighIn("2026-10-05", 82.0), weighIn("2026-10-07", 81.0),
            weighIn("2026-10-12", 81.4), weighIn("2026-10-27", 80.6)), MassUnit.KG)
        val segments = weeklySegments(weeks)
        assertEquals(2, segments.size)
        assertEquals(listOf(LocalDate.parse("2026-10-06"), LocalDate.parse("2026-10-12")), segments[0].map { it.date })
        assertEquals(81.5, segments[0][0].value, 1e-9)
        assertEquals(listOf(LocalDate.parse("2026-10-27")), segments[1].map { it.date })
    }

    @Test fun measurementsMoreThanAMissedWeekApartAreNotJoined() {
        val points = listOf("2026-09-01", "2026-09-08", "2026-09-29", "2026-10-06").map {
            ChartPoint(LocalDate.parse(it), 90.0)
        }
        assertEquals(listOf(2, 2), measuredSegments(points).map { it.size })
    }

    @Test fun changesAreSignedNeutrallyAndKeepTheirUnit() {
        assertEquals("−1.6 kg", signedText(-1.62, "kg"))
        assertEquals("+0.4 cm", signedText(0.38, "cm"))
        assertEquals("no change", signedText(0.04, "kg"))
    }
}
