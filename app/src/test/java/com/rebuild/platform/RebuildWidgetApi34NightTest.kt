package com.rebuild.platform

import android.app.Application
import android.widget.FrameLayout
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.rebuild.R
import com.rebuild.domain.WidgetModel
import com.rebuild.ui.accentTextFor
import com.rebuild.ui.theme.Dark
import com.rebuild.ui.theme.SignalAmber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "night", application = Application::class)
class RebuildWidgetApi34NightTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun darkWidgetColorResourcesMatchInkAndEmberDarkTheme() {
        assertEquals(Dark.surfaceContainerLowest.toArgb(), ContextCompat.getColor(context, R.color.widget_surface))
        assertEquals(Dark.surfaceContainerHighest.toArgb(), ContextCompat.getColor(context, R.color.widget_badge_pending))
        assertEquals(Dark.onSurface.toArgb(), ContextCompat.getColor(context, R.color.widget_on_surface))
        assertEquals(Dark.onSurfaceVariant.toArgb(), ContextCompat.getColor(context, R.color.widget_on_surface_variant))
        assertEquals(accentTextFor(Dark).toArgb(), ContextCompat.getColor(context, R.color.widget_accent_text))
        assertEquals(Dark.primaryContainer.toArgb(), ContextCompat.getColor(context, R.color.widget_primary_container))
        assertEquals(Dark.onPrimaryContainer.toArgb(), ContextCompat.getColor(context, R.color.widget_on_primary_container))
        assertEquals(SignalAmber.toArgb(), ContextCompat.getColor(context, R.color.widget_signal_amber))
    }

    @Test
    fun buildResponsiveProducesSizeMappedRemoteViewsOnApi34() {
        val rv = RebuildWidgetRenderer.buildResponsive(context, WidgetModel.Setup)
        val inflated = rv.apply(context, FrameLayout(context))
        assertNotNull(inflated)
    }
}
