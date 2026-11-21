package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GamePreferencesRepository
import com.example.game.BiomeId
import com.example.game.GamePhase
import com.example.game.HapticEngine
import com.example.game.SoundEngine
import com.example.game.TurnHint
import com.example.gl.RunnerGLSurfaceView
import com.example.gl.RunnerGameRenderer
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.FogMist
import com.example.ui.theme.HammeredGold
import com.example.ui.theme.JadeRelic
import com.example.ui.theme.Sandstone
import com.example.ui.theme.ShadowSlate
import com.example.ui.theme.SunlitCream
import kotlinx.coroutines.delay

@Composable
fun OpssyRunScreen(
  prefsRepository: GamePreferencesRepository,
  haptics: HapticEngine,
  sound: SoundEngine,
  onExitApp: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  val renderer = remember { RunnerGameRenderer(prefsRepository, haptics, sound) }
  val glView = remember { RunnerGLSurfaceView(context, renderer) }

  val snap by renderer.snapshotFlow.collectAsStateWithLifecycle()
  val progress by renderer.progressFlow.collectAsStateWithLifecycle()

  var vaultOpen by remember { mutableStateOf(false) }
  var showCloseCall by remember { mutableStateOf(false) }

  LaunchedEffect(snap.closeCallCounter) {
    if (snap.closeCallCounter > 0) {
      showCloseCall = true
      delay(880L)
      showCloseCall = false
    }
  }

  DisposableEffect(lifecycleOwner, glView) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> {
          glView.queueEvent { renderer.pauseIfPlaying() }
          glView.onPause()
        }
        Lifecycle.Event.ON_RESUME -> {
          glView.onResume()
          glView.requestFocus()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  BackHandler {
    when {
      vaultOpen -> vaultOpen = false
      snap.phase == GamePhase.PLAYING -> glView.queueEvent { renderer.pauseIfPlaying() }
      snap.phase == GamePhase.PAUSED || snap.phase == GamePhase.GAMEOVER ->
        glView.queueEvent { renderer.returnToTitle() }
      else -> onExitApp()
    }
  }

  val biomeLabel =
    when (snap.biome) {
      BiomeId.COURTYARD -> "SUNLIT COURTYARD"
      BiomeId.AQUEDUCT -> "FLOODED AQUEDUCTS"
      BiomeId.VAULT -> "TORCHLIT VAULTS"
    }

  val titleShadow =
    Shadow(
      color = ShadowSlate.copy(alpha = 0.72f),
      offset = Offset(0f, 4f),
      blurRadius = 10f,
    )

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(FogMist)
        .testTag("game_opengl_container")
  ) {
    // Native OpenGL ES 2.0 3D World
    AndroidView(
      factory = { glView },
      modifier = Modifier.fillMaxSize(),
    )

    // Atmospheric & Chaser Danger Vignette
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.radialGradient(
              colors =
                listOf(
                  Color.Transparent,
                  if (snap.chaserDanger > 0.05f) {
                    Color(0xFFA82A18).copy(alpha = (0.18f + snap.chaserDanger * 0.30f).coerceIn(0f, 0.55f))
                  } else {
                    ShadowSlate.copy(alpha = 0.24f)
                  },
                ),
              radius = 1250f,
            )
          )
    )

    // Wind-Streak Edge Accent on Close Call or Sunburst Dash
    if ((showCloseCall || snap.relics.dashRemaining > 0f) && snap.phase == GamePhase.PLAYING) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        for (i in 0 until 20) {
          val angle = (i / 20.0) * Math.PI * 2.0
          val r0 = size.minDimension * 0.38f
          val r1 = size.maxDimension * 0.78f
          drawLine(
            color = BrightGold.copy(alpha = if (showCloseCall) 0.28f else 0.16f),
            start = Offset(cx + kotlin.math.cos(angle).toFloat() * r0, cy + kotlin.math.sin(angle).toFloat() * r0),
            end = Offset(cx + kotlin.math.cos(angle).toFloat() * r1, cy + kotlin.math.sin(angle).toFloat() * r1),
            strokeWidth = 3f,
          )
        }
      }
    }

    // Main Safe-Area HUD & Overlays
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
      // Top Corner HUD
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        // Top-Left: Distance + Biome + Active Relics OR Wallet Badge
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          if (snap.phase == GamePhase.PLAYING || snap.phase == GamePhase.PAUSED) {
            Row(verticalAlignment = Alignment.Bottom) {
              Text(
                text = "${snap.distance}",
                style =
                  MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
                color = SunlitCream,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "M",
                style = MaterialTheme.typography.labelLarge.copy(shadow = titleShadow),
                color = BrightGold,
                modifier = Modifier.padding(bottom = 5.dp),
              )
            }
            Text(
              text = biomeLabel,
              style = MaterialTheme.typography.labelSmall.copy(shadow = titleShadow),
              color = SunlitCream.copy(alpha = 0.85f),
            )

            Spacer(modifier = Modifier.height(2.dp))
            if (snap.relics.hasShield) {
              RelicStatusPill(
                icon = Icons.Default.Shield,
                text = "Amber Aegis",
                accent = BrightGold,
              )
            }
            if (snap.relics.magnetRemaining > 0f) {
              RelicStatusPill(
                icon = Icons.Default.Attractions,
                text = String.format("%.1fs", snap.relics.magnetRemaining),
                accent = JadeRelic,
              )
            }
            if (snap.relics.dashRemaining > 0f) {
              RelicStatusPill(
                icon = Icons.Default.Bolt,
                text = String.format("%.1fs", snap.relics.dashRemaining),
                accent = HammeredGold,
              )
            }
          } else {
            Surface(
              shape = CircleShape,
              color = ShadowSlate.copy(alpha = 0.55f),
              border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.45f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                NairaBadgeIcon()
                Text(
                  text = formatNaira(snap.totalNaira),
                  style = MaterialTheme.typography.labelLarge,
                  color = BrightGold,
                )
              }
            }
          }
        }

        // Top-Right: Run Naira + Pause + Mute
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (snap.phase == GamePhase.PLAYING || snap.phase == GamePhase.PAUSED) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(end = 4.dp),
            ) {
              NairaBadgeIcon(sizeDp = 24)
              Text(
                text = formatNaira(snap.runNaira),
                style = MaterialTheme.typography.titleLarge.copy(shadow = titleShadow),
                color = BrightGold,
              )
            }
          }

          if (snap.phase == GamePhase.PLAYING) {
            IconButton(
              onClick = { glView.queueEvent { renderer.togglePause() } },
              modifier = Modifier.size(48.dp).testTag("pause_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Pause game",
                tint = SunlitCream,
              )
            }
          }

          IconButton(
            onClick = { glView.queueEvent { renderer.toggleMute() } },
            modifier = Modifier.size(48.dp).testTag("mute_button"),
          ) {
            Icon(
              imageVector =
                if (snap.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = if (snap.isMuted) "Unmute audio" else "Mute audio",
              tint = SunlitCream,
            )
          }
        }
      }

      // In-Game Transient Callouts
      if (snap.phase == GamePhase.PLAYING) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 74.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (snap.turnHint != TurnHint.NONE) {
            Surface(
              shape = CircleShape,
              color = ShadowSlate.copy(alpha = 0.72f),
              border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.75f)),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                if (snap.turnHint == TurnHint.LEFT || snap.turnHint == TurnHint.BOTH) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = BrightGold,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Text(
                  text =
                    when (snap.turnHint) {
                      TurnHint.BOTH -> "SWIPE TURN"
                      TurnHint.LEFT -> "TURN LEFT"
                      TurnHint.RIGHT -> "TURN RIGHT"
                      TurnHint.NONE -> ""
                    },
                  style = MaterialTheme.typography.labelLarge,
                  color = BrightGold,
                )
                if (snap.turnHint == TurnHint.RIGHT || snap.turnHint == TurnHint.BOTH) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = BrightGold,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          if (showCloseCall) {
            Text(
              text = "+25M CLOSE CALL",
              style = MaterialTheme.typography.labelLarge.copy(shadow = titleShadow),
              color = BrightGold,
            )
          }

          snap.biomeAnnouncement?.let { banner ->
            Surface(
              shape = CircleShape,
              color = ShadowSlate.copy(alpha = 0.65f),
              border = BorderStroke(1.dp, Sandstone.copy(alpha = 0.5f)),
            ) {
              Text(
                text = banner.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SunlitCream,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
              )
            }
          }

          if (snap.chaserDanger > 0.08f) {
            Text(
              text = "TEMPLE HOUNDS BEHIND YOU!",
              style = MaterialTheme.typography.labelSmall.copy(shadow = titleShadow),
              color = DangerCoral,
            )
          }
        }
      }

      // TITLE STATE
      if (snap.phase == GamePhase.TITLE) {
        Column(
          modifier =
            Modifier.fillMaxSize()
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                  if (!vaultOpen) {
                    glView.queueEvent { renderer.startRun() }
                  }
                },
              )
              .padding(horizontal = 24.dp, vertical = 28.dp)
              .testTag("title_tap_area"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(
            modifier = Modifier.padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "SUNLIT TEMPLE EXPEDITIONS",
              style = MaterialTheme.typography.labelSmall,
              color = ShadowSlate,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                buildAnnotatedString {
                  withStyle(SpanStyle(color = SunlitCream)) { append("opssy") }
                  withStyle(SpanStyle(color = BrightGold)) { append("run") }
                },
              style = MaterialTheme.typography.displayLarge.copy(shadow = titleShadow),
              textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(18.dp))
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              val best = if (snap.isDailyMode) snap.dailyHighScore else snap.highScore
              if (best > 0) {
                Surface(
                  shape = CircleShape,
                  color = ShadowSlate.copy(alpha = 0.58f),
                  border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.45f)),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Default.EmojiEvents,
                      contentDescription = null,
                      tint = BrightGold,
                      modifier = Modifier.size(16.dp),
                    )
                    Text(
                      text = "${if (snap.isDailyMode) "DAILY" else "BEST"} $best M",
                      style = MaterialTheme.typography.labelSmall,
                      color = BrightGold,
                    )
                  }
                }
              }

              Surface(
                shape = CircleShape,
                color = if (snap.isDailyMode) BrightGold else ShadowSlate.copy(alpha = 0.58f),
                border =
                  BorderStroke(
                    1.dp,
                    if (snap.isDailyMode) BrightGold else Sandstone.copy(alpha = 0.48f),
                  ),
                modifier =
                  Modifier.height(48.dp)
                    .clickable {
                      glView.queueEvent { renderer.setDailyMode(!snap.isDailyMode) }
                    }
                    .testTag("daily_seed_toggle"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector =
                      if (snap.isDailyMode) Icons.Default.CalendarToday
                      else Icons.Default.Explore,
                    contentDescription = null,
                    tint = if (snap.isDailyMode) ShadowSlate else BrightGold,
                    modifier = Modifier.size(16.dp),
                  )
                  Text(
                    text =
                      if (snap.isDailyMode) "DAILY SEED · ${snap.dailySeedDate.drop(5)}"
                      else "ENDLESS RUN",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snap.isDailyMode) ShadowSlate else SunlitCream,
                  )
                }
              }
            }
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Button(
              onClick = { vaultOpen = true },
              shape = CircleShape,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = ShadowSlate.copy(alpha = 0.78f),
                  contentColor = SunlitCream,
                ),
              border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.58f)),
              contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
              modifier = Modifier.height(48.dp).testTag("armory_open_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                tint = BrightGold,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = "ARMORY & RELICS", style = MaterialTheme.typography.labelLarge)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "TAP TO RUN",
                style = MaterialTheme.typography.titleMedium.copy(shadow = titleShadow),
                color = SunlitCream,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "SWIPE LEFT/RIGHT · JUMP · SLIDE · 90° TURNS",
                style = MaterialTheme.typography.labelSmall.copy(shadow = titleShadow),
                color = SunlitCream.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }

      // PAUSED OVERLAY
      AnimatedVisibility(
        visible = snap.phase == GamePhase.PAUSED,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(ShadowSlate.copy(alpha = 0.65f))
              .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text(
              text = "PAUSED",
              style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
              color = SunlitCream,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
              onClick = { glView.queueEvent { renderer.togglePause() } },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = BrightGold, contentColor = ShadowSlate),
              modifier = Modifier.height(48.dp).testTag("resume_button"),
            ) {
              Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("RESUME", style = MaterialTheme.typography.labelLarge)
            }
            Button(
              onClick = { glView.queueEvent { renderer.startRun() } },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SunlitCream),
              border = BorderStroke(1.dp, SunlitCream.copy(alpha = 0.5f)),
              modifier = Modifier.height(48.dp).testTag("restart_pause_button"),
            ) {
              Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("RESTART", style = MaterialTheme.typography.labelLarge)
            }
            Button(
              onClick = { glView.queueEvent { renderer.returnToTitle() } },
              shape = CircleShape,
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = FogMist),
              modifier = Modifier.height(48.dp).testTag("title_pause_button"),
            ) {
              Text("MAIN TITLE", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      // GAME OVER OVERLAY
      AnimatedVisibility(
        visible = snap.phase == GamePhase.GAMEOVER,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(ShadowSlate.copy(alpha = 0.68f))
              .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (snap.isNewRecord) {
              Surface(
                shape = CircleShape,
                color = BrightGold.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.75f)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = BrightGold,
                    modifier = Modifier.size(16.dp),
                  )
                  Text("NEW RECORD", style = MaterialTheme.typography.labelSmall, color = BrightGold)
                }
              }
              Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
              text = "RUN ENDED",
              style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
              color = SunlitCream,
            )

            Spacer(modifier = Modifier.height(22.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("DISTANCE", style = MaterialTheme.typography.labelSmall, color = FogMist)
                Text(
                  text = "${snap.distance} m",
                  style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
                  color = SunlitCream,
                )
              }
              Box(modifier = Modifier.width(1.dp).height(44.dp).background(FogMist.copy(alpha = 0.35f)))
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("COLLECTED", style = MaterialTheme.typography.labelSmall, color = FogMist)
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  NairaBadgeIcon(sizeDp = 24)
                  Text(
                    text = formatNaira(snap.runNaira),
                    style = MaterialTheme.typography.headlineLarge.copy(shadow = titleShadow),
                    color = BrightGold,
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            val best = if (snap.isDailyMode) snap.dailyHighScore else snap.highScore
            Text(
              text = "BEST $best M  ·  WALLET ${formatNaira(snap.totalNaira)}",
              style = MaterialTheme.typography.labelSmall,
              color = FogMist.copy(alpha = 0.90f),
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Button(
                onClick = { glView.queueEvent { renderer.startRun() } },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = HammeredGold, contentColor = ShadowSlate),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.height(52.dp).testTag("run_again_button"),
              ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RUN AGAIN", style = MaterialTheme.typography.labelLarge)
              }

              Button(
                onClick = { vaultOpen = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = DeepSlate, contentColor = SunlitCream),
                border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier.height(52.dp).testTag("gameover_armory_button"),
              ) {
                Icon(imageVector = Icons.Default.Storefront, contentDescription = null, tint = BrightGold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ARMORY", style = MaterialTheme.typography.labelLarge)
              }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = { glView.queueEvent { renderer.returnToTitle() } },
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = FogMist),
              modifier = Modifier.height(48.dp).testTag("back_to_title_button"),
            ) {
              Text("BACK TO TITLE", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }

      // TEMPLE ARMORY & RELIC VAULT OVERLAY
      if (vaultOpen) {
        ArmorySheet(
          progress = progress,
          onClose = { vaultOpen = false },
          onBuyOrEquipCharacter = { id -> glView.queueEvent { renderer.buyOrEquipCharacter(id) } },
          onBuyOrEquipTrail = { id -> glView.queueEvent { renderer.buyOrEquipTrail(id) } },
          onBuyUpgrade = { key -> glView.queueEvent { renderer.buyUpgrade(key) } },
        )
      }
    }
  }
}

@Composable
private fun RelicStatusPill(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  accent: Color,
) {
  Surface(
    shape = CircleShape,
    color = ShadowSlate.copy(alpha = 0.62f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
      )
    }
  }
}

@Composable
private fun NairaBadgeIcon(sizeDp: Int = 20) {
  Box(
    modifier =
      Modifier.size(sizeDp.dp)
        .background(BrightGold, CircleShape)
        .border(1.5.dp, HammeredGold, CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "₦",
      fontSize = (sizeDp * 0.58f).sp,
      fontWeight = FontWeight.Bold,
      color = ShadowSlate,
    )
  }
}
