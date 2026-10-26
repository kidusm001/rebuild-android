package com.rebuild.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.rebuild.domain.Monotonic
import java.util.UUID

/**
 * The phone's monotonic since-boot clock for the activity stopwatch. It keeps counting in deep sleep and ignores
 * wall-clock and time-zone changes; it restarts at zero after a reboot, which the boot count tells apart.
 */
object PhoneClock {
    /** This app process. A reading from another process is trusted only with a positively known matching boot. */
    private val session = UUID.randomUUID().toString()

    /** The boot count can't change while a process lives, so it is read once; null when the phone doesn't say. */
    @Volatile private var boot: Result<Int?>? = null

    fun read(context: Context): Monotonic = Monotonic(bootCount(context), session, SystemClock.elapsedRealtime())

    private fun bootCount(context: Context): Int? {
        boot?.let { return it.getOrNull() }
        val count = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (error: Settings.SettingNotFoundException) {
            null
        } catch (error: SecurityException) {
            null
        }
        boot = Result.success(count)
        return count
    }
}
