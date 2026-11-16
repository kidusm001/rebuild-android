package com.rebuild.platform

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rebuild.ui.RestAlert
import com.rebuild.ui.RestSound
import com.rebuild.ui.RestVisual
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestAlertPrefsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun choiceSurvivesANewInstance() {
        RestAlertPrefs(context).set(RestAlert(RestSound.ALARM, RestVisual.FLASH))
        assertEquals(RestAlert(RestSound.ALARM, RestVisual.FLASH), RestAlertPrefs(context).alert.value)
    }

    @Test fun nothingStoredReadsAsTheShortBuzz() {
        assertEquals(RestAlert(), RestAlertPrefs(context).alert.value)
    }

    @Test fun unknownOrForbiddenStoredValuesAreRepaired() {
        context.getSharedPreferences("rest_alert", Context.MODE_PRIVATE).edit()
            .putString("sound", "FOGHORN").putString("visual", "PULSE").commit()
        assertEquals(RestAlert(), RestAlertPrefs(context).alert.value)
        context.getSharedPreferences("rest_alert", Context.MODE_PRIVATE).edit()
            .putString("sound", "SILENT").putString("visual", "NONE").commit()
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.FLASH), RestAlertPrefs(context).alert.value)
    }

    @Test fun settingAForbiddenPairStoresTheRepairedOne() {
        val prefs = RestAlertPrefs(context)
        prefs.set(RestAlert(RestSound.SILENT, RestVisual.NONE))
        assertEquals(RestAlert(RestSound.SILENT, RestVisual.FLASH), prefs.alert.value)
    }
}
