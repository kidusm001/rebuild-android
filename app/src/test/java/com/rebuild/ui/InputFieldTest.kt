package com.rebuild.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.rebuild.ui.theme.RebuildTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputFieldTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /** Rows down the field's left edge drawn in the ring's colour: the ring's height, whatever sits beneath it. */
    private fun ringRows(tag: String, ring: Color): Int {
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val x = bounds.left.toInt() + 1
        val want = ring.toArgb()
        fun near(a: Int, b: Int) = abs(a - b) < 20
        return (bounds.top.toInt() until bounds.bottom.toInt()).count { y ->
            val c = bitmap.getPixel(x, y)
            near(android.graphics.Color.red(c), android.graphics.Color.red(want)) &&
                near(android.graphics.Color.green(c), android.graphics.Color.green(want)) &&
                near(android.graphics.Color.blue(c), android.graphics.Color.blue(want))
        }
    }

    @Test fun theRingWrapsTheBoxNotTheSupportingTextBeneathIt() {
        var ring = Color.Unspecified
        compose.setContent {
            RebuildTheme {
                ring = MaterialTheme.colorScheme.error
                Column {
                    NumberField("200", {}, "Bodyweight (kg)", Modifier.fillMaxWidth().testTag("bare"), isError = true,
                        errorSemantics = "Too heavy")
                    NumberField("200", {}, "Bodyweight (kg)", Modifier.fillMaxWidth().testTag("with-note"), isError = true,
                        supportingText = "Enter your weight from 20 to 400 kg.")
                    InputField("Felt strong", {}, "Note (optional)", Modifier.fillMaxWidth().testTag("note"), isError = true,
                        supportingText = "Too long.")
                }
            }
        }
        val bare = ringRows("bare", ring)
        assertTrue("the ring is drawn at all ($bare rows)", bare > 20)
        assertEquals("a number field's ring stops above its supporting text", bare, ringRows("with-note", ring))
        assertEquals("a text field's ring matches a number field's", bare, ringRows("note", ring))
    }
}
