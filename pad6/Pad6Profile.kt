package com.droiddeck.launcher

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import com.droiddeck.launcher.session.SessionPrefs
import com.droiddeck.launcher.session.SessionService
import com.droiddeck.launcher.session.SessionState

/**
 * Xiaomi Pad 6 / pipa: 16:10 native panel, Snapdragon 870 (Adreno 650), 8 GB RAM.
 * Real frame rate depends on the game and thermal headroom: 60 FPS is a target, not a promise.
 * Presets are editable and never re-applied on every launch.
 */
object Pad6Profile {
    private const val PREF = "pad6_personal_profile"
    private const val KEY_MODE = "selected_mode"
    private const val KEY_INITIALIZED_V1 = "initialized_v1"
    private const val KEY_INITIALIZED_V3 = "initialized_v3"

    // 16:10 on the 2880x1800 display. Stable reduces pixel count compared to old 1280x720.
    enum class Mode(val title: String, val resolution: String, val sharpness: Int) {
        STABLE("Stable 60 • 1152x720 (16:10)", "1152x720", 65),
        HEAVY("Heavy 60 • 960x600 (16:10)", "960x600", 70),
        QUALITY("Quality 60 • 1440x900 (16:10)", "1440x900", 55)
    }

    fun matchesDevice(): Boolean {
        val maker = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        return maker.equals("Xiaomi", ignoreCase = true) &&
            (model.equals("23043RP34G", ignoreCase = true) ||
                device.equals("pipa", ignoreCase = true))
    }

    fun selected(context: Context): Mode {
        val name = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, Mode.STABLE.name)
        return Mode.entries.firstOrNull { it.name == name } ?: Mode.STABLE
    }

    /**
     * Migrate just the resolution applied by old V2 defaults. Respect a manually
     * changed Steam resolution instead of resetting user tweaks during an APK update.
     */
    fun applyOnce(context: Context) {
        if (!matchesDevice()) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED_V3, false)) return
        if (!prefs.getBoolean(KEY_INITIALIZED_V1, false)) {
            apply(context, Mode.STABLE)
            return
        }
        val mode = selected(context)
        val legacy = when (mode) {
            Mode.STABLE -> "1280x720"
            Mode.HEAVY -> "960x540"
            Mode.QUALITY -> "1600x900"
        }
        val previous = SessionPrefs.sessionPrefs(context)
            .getString("displayResolution.${SessionService.MODE_STEAM}", null)
        if (previous == null || previous == legacy) {
            apply(context, mode)
        } else {
            // User made a custom resolution choice: leave all runtime preferences intact.
            prefs.edit().putBoolean(KEY_INITIALIZED_V3, true).apply()
        }
    }

    /** Explicit button tap re-applies the chosen profile without touching game files. */
    fun apply(context: Context, mode: Mode): Boolean {
        if (!matchesDevice()) return false
        SessionPrefs.setResolutionChoice(context, SessionService.MODE_STEAM, mode.resolution)
        SessionPrefs.setOrientation(context, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        SessionPrefs.setUpscaler(context, 4) // AMD FSR 1 for image scaling
        SessionPrefs.setUpscaleSharpness(context, mode.sharpness)
        SessionPrefs.setLowLatency(context, true)
        SessionPrefs.setGpuDriverMode(context, SessionPrefs.GPU_DRIVERS_AUTO)
        SessionPrefs.setFexPreset(context, "PERFORMANCE_TSO") // safer than Extreme for JIT
        SessionPrefs.setHudShowFps(context, true)
        SessionPrefs.setMangoapp(context, true)
        SessionPrefs.sessionPrefs(context).edit()
            .putBoolean("hudDeck", true)
            .putBoolean("hud", true)
            .apply()
        // Caps non-Steam composition; Steam games require Steam Quick Access Frame Limit = 60.
        SessionState.fpsLimit = 60
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.name)
            .putBoolean(KEY_INITIALIZED_V1, true)
            .putBoolean(KEY_INITIALIZED_V3, true)
            .apply()
        return true
    }
}
