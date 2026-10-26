package com.rebuild.domain

import org.junit.Assert.*
import org.junit.Test

class UserTextTest {
    @Test fun overflowingEmojiIsRemovedWholeInsteadOfSplit() {
        val prefix = "a".repeat(4095)
        assertEquals(prefix, limitUserText(prefix + "\uD83D\uDE42", 4096))
    }

    @Test fun completeEmojiAtTheExactLimitIsPreserved() {
        val text = "a".repeat(4094) + "\uD83D\uDE42"
        assertEquals(text, limitUserText(text, 4096))
        assertTrue(isWellFormedUtf16(text))
    }

    @Test fun emptyAndBmpTextRespectTheSameLimit() {
        assertEquals("", limitUserText("\uD83D\uDE42", 0))
        assertEquals("abc", limitUserText("abcdef", 3))
        assertEquals("", limitUserText("", 4096))
        assertTrue(isWellFormedUtf16(""))
        assertTrue(isWellFormedUtf16("\u6F22\u00E9\n"))
    }

    @Test fun loneOrMisorderedSurrogatesAreRejectedWithoutReplacingText() {
        listOf("\uD83D", "\uDE42", "a\uD83Db", "\uDE42\uD83D", "\uD83D\uD83D").forEach {
            assertFalse(isWellFormedUtf16(it))
            assertEquals(it, limitUserText(it, 4096))
        }
        assertTrue(isWellFormedUtf16("\uD83D\uDE42\uD83D\uDE42"))
    }
}
