package com.rebuild.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DragSlotTest {
    // Three other pills, 100 wide with 10 gaps, laid out from 0 with the dragged pill (120 wide) taken out.
    private val lefts = listOf(0f, 110f, 220f)
    private val sizes = listOf(100f, 100f, 100f)
    private fun slot(centre: Float, from: Int) = dragSlot(lefts, sizes, 10f, 120f, centre, from)

    @Test fun staysUntilItsCentrePassesTheNeighboursCentre() {
        // In slot 0 the first other sits at 130..230, centre 180.
        assertEquals(0, slot(179f, from = 0))
        assertEquals(1, slot(181f, from = 0))
    }

    @Test fun aBigJumpPassesEveryNeighbourItCrossesAtOnce() {
        // Past the third other's centre (with the pill in slot 2 it sits at 350..450, centre 400): last slot.
        assertEquals(3, slot(401f, from = 0))
        assertEquals(2, slot(399f, from = 0))
    }

    @Test fun movingBackNeedsTheCentreBackPastTheNeighbourItPassed() {
        // From slot 1, the neighbour now on the left is the first other at 0..100, centre 50.
        assertEquals(1, slot(51f, from = 1))
        assertEquals(0, slot(49f, from = 1))
        // And a jump all the way back from the end.
        assertEquals(0, slot(10f, from = 3))
    }
}
