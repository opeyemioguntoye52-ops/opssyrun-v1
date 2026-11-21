package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.GamePreferencesRepository
import com.example.game.HapticEngine
import com.example.game.SoundEngine
import com.example.ui.OpssyRunScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var prefsRepository: GamePreferencesRepository
  private lateinit var hapticEngine: HapticEngine
  private lateinit var soundEngine: SoundEngine

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefsRepository = GamePreferencesRepository(applicationContext)
    hapticEngine = HapticEngine(applicationContext)
    soundEngine = SoundEngine()

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )

    setContent {
      MyApplicationTheme {
        OpssyRunScreen(
          prefsRepository = prefsRepository,
          haptics = hapticEngine,
          sound = soundEngine,
          onExitApp = { finish() },
        )
      }
    }
  }

  override fun onDestroy() {
    soundEngine.dispose()
    super.onDestroy()
  }
}
