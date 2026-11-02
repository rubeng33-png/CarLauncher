package com.rgservice.carlauncher

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.rgservice.carlauncher.core.DesignScale
import com.rgservice.carlauncher.core.UiSize
import com.rgservice.carlauncher.data.Gauge
import com.rgservice.carlauncher.data.HomeState
import com.rgservice.carlauncher.data.Media
import com.rgservice.carlauncher.data.ThemeMode
import com.rgservice.carlauncher.data.Trip
import com.rgservice.carlauncher.data.Weather
import com.rgservice.carlauncher.data.apps.AppsRepository
import com.rgservice.carlauncher.ui.home.HomeActions
import com.rgservice.carlauncher.ui.home.HomeScreen
import com.rgservice.carlauncher.ui.pages.RightActions
import com.rgservice.carlauncher.ui.pages.RightData
import com.rgservice.carlauncher.ui.theme.CarLauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de la pantalla de inicio con datos de ejemplo (solo para revisar el diseño).
 * Pantalla de la radio: 1024x600. Se generan en app/build/capturas/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w1024dp-h600dp-land-mdpi")
class HomeScreenshotTest {

    @get:Rule val rule = createComposeRule()

    private fun state(dark: Boolean) = HomeState(
        name = "Ruben", gpsOk = true, speedKmh = 48, speedLimit = 80,
        trip = Trip(12.4, 38, 41),
        weather = Weather(22, "Parcialmente nublado", 29, 19, 64, 11, 2),
        odoKm = 123456,
        oil = Gauge("oil", "Aceite motor", 3200, 5000, 126656),
        gear = Gauge("gear", "Aceite caja", 420, 40000, 123876),
        maintWarnings = 1, dark = dark, theme = ThemeMode.Auto,
    )

    private val media = Media(
        source = "Bluetooth", title = "Vivir mi vida", artist = "Marc Anthony",
        playing = true, positionMs = 12_000, positionAt = 0, durationMs = 239_000, hasSession = true,
    )

    private fun shot(name: String, dark: Boolean, size: UiSize, page: Int = 0) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val apps = AppsRepository(ctx, ctx.getSharedPreferences("test", Context.MODE_PRIVATE))
        val home = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        val right = RightActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
        val data = RightData(media, null, null, -25.38, -57.5, emptyList(), mapOf("destHome" to null, "destWork" to null), apps, page, 15)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CarLauncherTheme(dark = dark) {
                DesignScale(size) { HomeScreen(state(dark), data, right, home) }
            }
        }
        rule.mainClock.advanceTimeBy(2500)
        rule.onRoot().captureRoboImage("build/capturas/$name.png")
    }

    @Test fun oscuroNormal() = shot("1-oscuro-normal", true, UiSize.Normal)
    @Test fun claroNormal() = shot("2-claro-normal", false, UiSize.Normal)
    @Test fun oscuroGrande() = shot("3-oscuro-grande", true, UiSize.Grande)
    @Test fun claroGrande() = shot("4-claro-grande", false, UiSize.Grande)
    @Test fun oscuroMuyGrande() = shot("5-oscuro-muy-grande", true, UiSize.MuyGrande)
    @Test fun oscuroAccesos() = shot("6-oscuro-accesos", true, UiSize.Normal, page = 2)
}
