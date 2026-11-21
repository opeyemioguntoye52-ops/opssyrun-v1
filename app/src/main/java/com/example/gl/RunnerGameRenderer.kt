package com.example.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.GamePreferencesRepository
import com.example.game.ActiveRelicTimers
import com.example.game.BiomeId
import com.example.game.CharacterId
import com.example.game.GameCatalog
import com.example.game.GamePhase
import com.example.game.GamePhysics
import com.example.game.GameSnapshot
import com.example.game.HapticEngine
import com.example.game.HapticKind
import com.example.game.PlayerProgress
import com.example.game.RelicKind
import com.example.game.SeededRng
import com.example.game.SoundEngine
import com.example.game.TrailId
import com.example.game.UpgradeKey
import com.example.game.getDashDuration
import com.example.game.getDashSpeedMultiplier
import com.example.game.getHeadStartDuration
import com.example.game.getMagnetDuration
import com.example.game.todaySeedDate
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class RunnerAction {
  LEFT,
  RIGHT,
  JUMP,
  SLIDE,
}

class RunnerGameRenderer(
  private val prefsRepository: GamePreferencesRepository,
  private val haptics: HapticEngine,
  val sound: SoundEngine,
) : GLSurfaceView.Renderer {

  private lateinit var shader: GlShaderProgram
  private lateinit var textures: ProceduralTextures

  private val track = TrackWorld3D()
  private val explorer = ExplorerRenderer3D()
  private val particles = ParticleSystem3D()

  private var progress: PlayerProgress = prefsRepository.cachedProgress
  private val _progressFlow = MutableStateFlow(progress)
  val progressFlow: StateFlow<PlayerProgress> = _progressFlow.asStateFlow()

  private val _snapshotFlow = MutableStateFlow(GameSnapshot())
  val snapshotFlow: StateFlow<GameSnapshot> = _snapshotFlow.asStateFlow()

  @Volatile private var phase = GamePhase.TITLE
  @Volatile private var isDailyMode = false

  private var currentSeg: TrackSegment3D = track.reset({ Random.nextFloat() }, spawnObstacles = false)
  private var segProgress = 6f
  private var distance = 0f
  private var bonusDistance = 0f
  private var runNaira = 0
  private var isNewRecord = false
  private var forwardSpeed = GamePhysics.BASE_SPEED
  private var stumbleSlowTimer = 0f
  private var chaserTimer = 0f
  private var invulnTimer = 0f

  // Active Relics
  private var magnetRemaining = 0f
  private var magnetMax = 8f
  private var hasShield = false
  private var dashRemaining = 0f
  private var dashMax = 4.5f

  private var closeCallCounter = 0
  private var lastBiome = BiomeId.COURTYARD
  private var biomeAnnouncement: String? = null
  private var biomeBannerTimer = 0f

  // Chase Camera
  private var camYaw = 0f
  private var camX = 0f
  private var camY = 4.35f
  private var camZ = 14f
  private var camFov = 52f
  private var camAspect = 0.56f
  private var shakeIntensity = 0f
  private var titleCruiseTimer = 0f

  private var lastFrameNs = System.nanoTime()
  private var elapsedSec = 0f
  private var lastReportedDist = -1

  init {
    sound.isMuted = progress.isMuted
    explorer.setCharacter(progress.equippedCharacter)
    particles.setTrail(progress.equippedTrail)
    emitSnapshot()
  }

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glCullFace(GLES20.GL_BACK)
    shader = GlShaderProgram()
    textures = ProceduralTextures()
    lastFrameNs = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    GLES20.glViewport(0, 0, w, h)
    camAspect = w.toFloat() / h.toFloat()
    Matrix.perspectiveM(shader.projMatrix, 0, camFov, camAspect, 0.25f, 165f)
  }

  override fun onDrawFrame(gl: GL10?) {
    val nowNs = System.nanoTime()
    val rawDt = (nowNs - lastFrameNs) / 1_000_000_000f
    lastFrameNs = nowNs
    val dt = rawDt.coerceIn(0.001f, 0.05f)
    elapsedSec += dt

    stepSimulation(dt)

    val fog = track.fogColor
    GLES20.glClearColor(fog[0], fog[1], fog[2], 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    shader.beginFrame(
      camX = camX,
      camY = camY,
      camZ = camZ,
      sunColor = track.sunColor,
      hemiSky = track.hemiSky,
      hemiGround = track.hemiGround,
      fogColor = track.fogColor,
      fogDensity = track.fogDensity,
    )

    val playerX = currentWorldX()
    val playerZ = currentWorldZ()
    val totalDist = (distance + bonusDistance).toInt()

    track.render(shader, textures, playerX, playerZ, elapsedSec, totalDist)
    explorer.render(
      shader = shader,
      whiteTex = textures.whiteTex,
      worldX = playerX,
      worldZ = playerZ,
      timeSec = elapsedSec,
      hasShield = hasShield,
      hasMagnet = magnetRemaining > 0f,
      hasDash = dashRemaining > 0f,
    )
    if (phase == GamePhase.PLAYING || phase == GamePhase.GAMEOVER) {
      val backX = -forwardX(currentSeg.dir)
      val backZ = -forwardZ(currentSeg.dir)
      val danger = if (phase == GamePhase.GAMEOVER) 0.9f else (chaserTimer / 8.5f).coerceIn(0f, 1f)
      explorer.renderChasers(
        shader = shader,
        whiteTex = textures.whiteTex,
        worldX = playerX,
        worldZ = playerZ,
        backX = backX,
        backZ = backZ,
        yawRad = explorer.visualYaw,
        danger = danger,
        timeSec = elapsedSec,
      )
    }
    particles.render(shader, textures.whiteTex)
  }

  private fun currentWorldX(): Float =
    currentSeg.originX + forwardX(currentSeg.dir) * segProgress + rightX(currentSeg.dir) * explorer.laneX

  private fun currentWorldZ(): Float =
    currentSeg.originZ + forwardZ(currentSeg.dir) * segProgress + rightZ(currentSeg.dir) * explorer.laneX

  private fun stepSimulation(dt: Float) {
    when (phase) {
      GamePhase.TITLE -> {
        titleCruiseTimer += dt
        if (titleCruiseTimer > 3.2f) {
          titleCruiseTimer = 0f
          explorer.shiftLane(if (Random.nextBoolean()) 1 else -1)
        }
        segProgress += 11.5f * dt
        val next = currentSeg.nextStraight
        if (segProgress >= currentSeg.length && next != null) {
          segProgress -= currentSeg.length
          currentSeg = next
          track.advanceMaintenance(currentSeg, spawnObstacles = false)
        }
        val px = currentWorldX()
        val pz = currentWorldZ()
        val bx = -forwardX(currentSeg.dir)
        val bz = -forwardZ(currentSeg.dir)
        explorer.update(
          dt = dt,
          forwardSpeed = 11.5f,
          targetYaw = dirToYaw(currentSeg.dir),
          onJump = {},
          onFootstep = { particles.spawnFootTrail(px, 0f, pz, bx, bz, 2, 0.28f) },
          onLand = {},
          onSlideTick = {},
        )
        track.updateAtmosphereAndPickups(dt, px, explorer.y, pz, 0, false, false, { _, _, _ -> }, { _, _, _, _ -> })
        particles.update(dt)
        updateChaseCamera(dt, px, pz, dirToYaw(currentSeg.dir), 1.3f)
      }
      GamePhase.PLAYING -> {
        if (magnetRemaining > 0f) magnetRemaining = (magnetRemaining - dt).coerceAtLeast(0f)
        if (dashRemaining > 0f) dashRemaining = (dashRemaining - dt).coerceAtLeast(0f)
        if (chaserTimer > 0f) chaserTimer = (chaserTimer - dt).coerceAtLeast(0f)
        if (stumbleSlowTimer > 0f) stumbleSlowTimer = (stumbleSlowTimer - dt).coerceAtLeast(0f)
        if (invulnTimer > 0f) invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
        if (biomeBannerTimer > 0f) {
          biomeBannerTimer -= dt
          if (biomeBannerTimer <= 0f) {
            biomeAnnouncement = null
            emitSnapshot()
          }
        }

        forwardSpeed = (forwardSpeed + GamePhysics.SPEED_RAMP_PER_SEC * dt).coerceAtMost(GamePhysics.MAX_SPEED)
        val dashMult = if (dashRemaining > 0f) getDashSpeedMultiplier(progress.dashLevel) else 1f
        val stumbleMult = if (stumbleSlowTimer > 0f) 0.78f else 1f
        val activeSpeed = forwardSpeed * dashMult * stumbleMult

        val stepDist = activeSpeed * dt
        segProgress += stepDist
        distance += stepDist

        val seg = currentSeg
        if (seg.type == SegmentType.STRAIGHT || seg.type == SegmentType.GAP) {
          val next = seg.nextStraight
          if (segProgress >= seg.length && next != null) {
            segProgress -= seg.length
            currentSeg = next
            track.advanceMaintenance(currentSeg, spawnObstacles = true)
          }
        } else {
          // 90-Degree Turn or T-Junction
          if (dashRemaining > 0f && seg.committedTurn == null) {
            seg.committedTurn =
              when (seg.type) {
                SegmentType.CORNER_RIGHT -> "right"
                SegmentType.CORNER_LEFT -> "left"
                else -> if (Random.nextBoolean()) "left" else "right"
              }
            haptics.trigger(HapticKind.TURN)
          }
          val committed = seg.committedTurn
          if (committed != null && segProgress >= 8.6f) {
            executeBranchTransition(seg, committed)
          } else if (committed == null && segProgress >= seg.length - 1.1f) {
            if (hasShield) {
              shatterShield(boostOverGap = false)
              val fallback = if (seg.type == SegmentType.CORNER_RIGHT) "right" else "left"
              haptics.trigger(HapticKind.TURN)
              executeBranchTransition(seg, fallback)
            } else {
              handleCrash()
            }
          }
        }

        val px = currentWorldX()
        val pz = currentWorldZ()
        val bx = -forwardX(currentSeg.dir)
        val bz = -forwardZ(currentSeg.dir)
        val targetYaw = dirToYaw(currentSeg.dir)

        explorer.update(
          dt = dt,
          forwardSpeed = activeSpeed,
          targetYaw = targetYaw,
          onJump = {
            sound.playJump()
            particles.spawnFootTrail(px, explorer.y, pz, bx, bz, 5, 0.4f)
            haptics.trigger(HapticKind.LIGHT)
          },
          onFootstep = {
            sound.playFootstep(heavy = false)
            particles.spawnFootTrail(px, explorer.y, pz, bx, bz, 2, 0.3f)
          },
          onLand = {
            sound.playFootstep(heavy = true)
            particles.spawnFootTrail(px, 0f, pz, bx, bz, 6, 0.55f)
          },
          onSlideTick = {
            particles.spawnFootTrail(px, 0f, pz, bx, bz, 2, 0.42f)
          },
        )

        val totalDist = (distance + bonusDistance).toInt()
        val curBiome = track.getBiomeAtDistance(totalDist)
        if (curBiome != lastBiome) {
          lastBiome = curBiome
          biomeAnnouncement =
            when (curBiome) {
              BiomeId.AQUEDUCT -> "Flooded Sunken Aqueducts"
              BiomeId.VAULT -> "Torchlit Subterranean Vaults"
              BiomeId.COURTYARD -> null
            }
          biomeBannerTimer = 3.2f
          emitSnapshot()
        }

        if (curBiome == BiomeId.VAULT && Random.nextFloat() < dt * 10f) {
          val fx = px + forwardX(currentSeg.dir) * 12f
          val fz = pz + forwardZ(currentSeg.dir) * 12f
          particles.spawnVaultEmber(fx, 0.4f, fz)
        }

        track.updateAtmosphereAndPickups(
          dt = dt,
          playerX = px,
          playerY = explorer.y,
          playerZ = pz,
          distMeters = totalDist,
          magnetActive = magnetRemaining > 0f,
          dashActive = dashRemaining > 0f,
          onCoinCollected = { cx, cy, cz ->
            runNaira += GamePhysics.NAIRA_PER_COIN
            progress = progress.copy(naira = progress.naira + GamePhysics.NAIRA_PER_COIN)
            sound.playCoin()
            particles.spawnBurst(cx, cy, cz, 1.0f, 0.84f, 0.36f, count = 8)
            haptics.trigger(HapticKind.LIGHT)
            emitSnapshot()
          },
          onRelicCollected = { kind, rx, ry, rz ->
            sound.playRelicPickup(kind)
            haptics.trigger(HapticKind.RELIC)
            when (kind) {
              RelicKind.MAGNET -> {
                particles.spawnBurst(rx, ry, rz, 0.32f, 0.72f, 0.53f, count = 12)
                magnetMax = getMagnetDuration(progress.magnetLevel)
                magnetRemaining = magnetMax
              }
              RelicKind.SHIELD -> {
                particles.spawnBurst(rx, ry, rz, 1.0f, 0.82f, 0.40f, count = 12)
                hasShield = true
              }
              RelicKind.DASH -> {
                particles.spawnBurst(rx, ry, rz, 1.0f, 0.62f, 0.12f, count = 12)
                dashMax = getDashDuration(progress.dashLevel)
                dashRemaining = dashMax
              }
            }
            emitSnapshot()
          },
        )

        checkCollisionsAndNearMisses()
        particles.update(dt)
        updateChaseCamera(dt, px, pz, explorer.visualYaw, 0f)

        val nowDist = (distance + bonusDistance).toInt()
        if (nowDist != lastReportedDist) {
          emitSnapshot()
        }
      }
      GamePhase.PAUSED -> Unit
      GamePhase.GAMEOVER -> {
        val px = currentWorldX()
        val pz = currentWorldZ()
        explorer.update(dt, 0f, dirToYaw(currentSeg.dir), {}, {}, {}, {})
        particles.update(dt)
        updateChaseCamera(dt, px, pz, explorer.visualYaw, 0f)
      }
    }
  }

  private fun checkCollisionsAndNearMisses() {
    val seg = currentSeg
    val gStart = seg.gapStart
    val gEnd = seg.gapEnd
    if (gStart != null && gEnd != null && segProgress in gStart..gEnd) {
      if (explorer.y <= 0.14f && dashRemaining <= 0f && invulnTimer <= 0f) {
        if (hasShield) {
          shatterShield(boostOverGap = true)
        } else {
          handleCrash()
          return
        }
      }
    }

    val pMinX = explorer.laneX - 0.34f
    val pMaxX = explorer.laneX + 0.34f
    val pMinY = explorer.y + 0.05f
    val pMaxY = explorer.y + explorer.getLocalHeight()
    val pMinZ = segProgress - 0.28f
    val pMaxZ = segProgress + 0.28f

    for (obs in seg.obstacles) {
      if (!obs.resolved) {
        val hitX = pMaxX > obs.minX && pMinX < obs.maxX
        val hitY = pMaxY > obs.minY && pMinY < obs.maxY
        val hitZ = pMaxZ > obs.minZ && pMinZ < obs.maxZ
        if (hitX && hitY && hitZ) {
          obs.resolved = true
          if (dashRemaining > 0f) {
            bonusDistance += 10f
            shakeIntensity = 0.25f
            particles.spawnBurst(currentWorldX(), explorer.y + 1f, currentWorldZ(), 1.0f, 0.62f, 0.12f, 10)
            continue
          }
          if (invulnTimer > 0f) continue
          if (hasShield) {
            shatterShield(boostOverGap = false)
            continue
          }
          val lateralOverlap = minOf(pMaxX - obs.minX, obs.maxX - pMinX)
          val isGraze = obs.kind == ObstacleKind.LOG || (obs.kind == ObstacleKind.PILLAR && lateralOverlap < 0.26f)
          if (isGraze) {
            handleStumble()
          } else {
            handleCrash()
          }
          return
        }
      }

      if (!obs.nearMissChecked && !obs.resolved && segProgress > obs.maxZ + 0.45f) {
        obs.nearMissChecked = true
        val laneDist = abs(explorer.laneX - GamePhysics.LANES[obs.lane])
        val closeCall =
          when (obs.kind) {
            ObstacleKind.PILLAR -> laneDist < 1.65f
            ObstacleKind.LOG -> laneDist < 1.15f && explorer.y in 0.72f..1.55f
            ObstacleKind.ARCH -> laneDist < 1.15f && explorer.isSliding()
          }
        if (closeCall) {
          bonusDistance += GamePhysics.CLOSE_CALL_BONUS_METERS
          closeCallCounter += 1
          sound.playNearMiss()
          haptics.trigger(HapticKind.LIGHT)
          emitSnapshot()
        }
      }
    }
  }

  private fun executeBranchTransition(junction: TrackSegment3D, side: String) {
    val worldBeforeX = currentWorldX()
    val worldBeforeZ = currentWorldZ()
    val chosen = track.commitJunctionBranch(junction, side) ?: return

    val newFwdX = forwardX(chosen.dir)
    val newFwdZ = forwardZ(chosen.dir)
    val newRightX = rightX(chosen.dir)
    val newRightZ = rightZ(chosen.dir)
    val dx = worldBeforeX - chosen.originX
    val dz = worldBeforeZ - chosen.originZ

    val nextProg = dx * newFwdX + dz * newFwdZ
    val nextLaneX = (dx * newRightX + dz * newRightZ).coerceIn(-2.85f, 2.85f)

    currentSeg = chosen
    segProgress = nextProg
    explorer.laneX = nextLaneX

    var bestLane = 1
    var bestDist = Float.MAX_VALUE
    for (i in GamePhysics.LANES.indices) {
      val d = abs(GamePhysics.LANES[i] - nextLaneX)
      if (d < bestDist) {
        bestDist = d
        bestLane = i
      }
    }
    explorer.targetLaneIndex = bestLane
    track.advanceMaintenance(currentSeg, spawnObstacles = true)
  }

  private fun shatterShield(boostOverGap: Boolean) {
    hasShield = false
    invulnTimer = 1.0f + (progress.shieldLevel - 1) * 0.25f
    shakeIntensity = 0.45f
    sound.playShieldShatter()
    particles.spawnBurst(currentWorldX(), explorer.y + 1f, currentWorldZ(), 1.0f, 0.82f, 0.40f, 12)
    haptics.trigger(HapticKind.MEDIUM)
    if (boostOverGap) {
      explorer.boostJump(15.2f)
    }
    emitSnapshot()
  }

  private fun handleStumble() {
    if (chaserTimer > 0f) {
      handleCrash()
      return
    }
    chaserTimer = 8.5f
    stumbleSlowTimer = 0.9f
    invulnTimer = 0.7f
    shakeIntensity = 0.42f
    explorer.triggerStumble()
    sound.playStumble()
    haptics.trigger(HapticKind.MEDIUM)
    emitSnapshot()
  }

  private fun handleCrash() {
    if (phase != GamePhase.PLAYING) return
    phase = GamePhase.GAMEOVER
    shakeIntensity = 0.78f
    explorer.triggerCrash()
    particles.spawnFootTrail(
      currentWorldX(),
      0.2f,
      currentWorldZ(),
      -forwardX(currentSeg.dir),
      -forwardZ(currentSeg.dir),
      10,
      0.8f,
    )
    sound.playCrash()
    haptics.trigger(HapticKind.HEAVY)

    val finalDist = (distance + bonusDistance).toInt()
    val today = todaySeedDate()
    var nextDailyHigh = if (progress.dailySeedDate == today) progress.dailyHighScore else 0
    var nextHigh = progress.highScore
    if (isDailyMode && finalDist > nextDailyHigh) {
      nextDailyHigh = finalDist
      isNewRecord = true
    }
    if (finalDist > nextHigh) {
      nextHigh = finalDist
      isNewRecord = true
    }
    progress =
      progress.copy(
        highScore = nextHigh,
        dailyHighScore = nextDailyHigh,
        dailySeedDate = today,
      )
    saveAndNotifyProgress()
  }

  private fun updateChaseCamera(
    dt: Float,
    playerX: Float,
    playerZ: Float,
    targetYaw: Float,
    sideOffset: Float,
  ) {
    val speedRatio =
      ((forwardSpeed - GamePhysics.BASE_SPEED) / (GamePhysics.MAX_SPEED - GamePhysics.BASE_SPEED))
        .coerceIn(0f, 1f)
    val dashFovBoost = if (dashRemaining > 0f) 10f else 0f
    val targetFov = 52f + speedRatio * 13f + dashFovBoost
    if (abs(camFov - targetFov) > 0.05f) {
      camFov += (targetFov - camFov) * (1f - exp(-5f * dt))
      Matrix.perspectiveM(shader.projMatrix, 0, camFov, camAspect, 0.25f, 165f)
    }

    var diff = targetYaw - camYaw
    while (diff > PI.toFloat()) diff -= (PI * 2.0).toFloat()
    while (diff < -PI.toFloat()) diff += (PI * 2.0).toFloat()
    camYaw += diff * (dt * 8.5f).coerceAtMost(1f)

    val fwdX = -sin(camYaw)
    val fwdZ = -cos(camYaw)
    val rightX = cos(camYaw)
    val rightZ = -sin(camYaw)

    val desiredX = playerX - fwdX * 7.6f + rightX * sideOffset
    val desiredZ = playerZ - fwdZ * 7.6f + rightZ * sideOffset
    val desiredY = 4.35f + explorer.y * 0.48f

    val posDamp = 1f - exp(-10.5f * dt)
    val yDamp = 1f - exp(-7.5f * dt)
    camX += (desiredX - camX) * posDamp
    camY += (desiredY - camY) * yDamp
    camZ += (desiredZ - camZ) * posDamp

    var shakeX = 0f
    var shakeY = 0f
    if (shakeIntensity > 0.002f) {
      shakeX = (Random.nextFloat() - 0.5f) * shakeIntensity * 0.85f
      shakeY = (Random.nextFloat() - 0.5f) * shakeIntensity * 0.70f
      shakeIntensity *= exp(-7.5f * dt)
    } else {
      shakeIntensity = 0f
    }

    val lookX = playerX + fwdX * 10.5f
    val lookY = 1.65f + explorer.y * 0.55f
    val lookZ = playerZ + fwdZ * 10.5f
    Matrix.setLookAtM(
      shader.viewMatrix,
      0,
      camX + shakeX,
      camY + shakeY,
      camZ,
      lookX,
      lookY,
      lookZ,
      0f,
      1f,
      0f,
    )
  }

  // --- Commands invoked from UI / Input (executed safely on GL thread or synchronized) ---

  fun handleAction(action: RunnerAction) {
    if (phase != GamePhase.PLAYING) return

    if (action == RunnerAction.LEFT || action == RunnerAction.RIGHT) {
      val side = if (action == RunnerAction.LEFT) "left" else "right"
      val seg = currentSeg
      val inTurnWindow =
        (seg.type == SegmentType.CORNER_LEFT ||
          seg.type == SegmentType.CORNER_RIGHT ||
          seg.type == SegmentType.T_JUNCTION) &&
          seg.committedTurn == null &&
          segProgress >= 1.5f &&
          segProgress <= seg.length - 0.5f

      if (inTurnWindow) {
        val valid =
          seg.type == SegmentType.T_JUNCTION ||
            (seg.type == SegmentType.CORNER_LEFT && action == RunnerAction.LEFT) ||
            (seg.type == SegmentType.CORNER_RIGHT && action == RunnerAction.RIGHT)
        if (valid) {
          seg.committedTurn = side
          sound.playTurn()
          haptics.trigger(HapticKind.TURN)
          if (segProgress >= 8.2f) {
            executeBranchTransition(seg, side)
          }
          emitSnapshot()
          return
        }
      }

      if (explorer.shiftLane(if (action == RunnerAction.LEFT) -1 else 1)) {
        sound.playLaneShift()
        haptics.trigger(HapticKind.LIGHT)
      }
      return
    }

    if (action == RunnerAction.JUMP) {
      val px = currentWorldX()
      val pz = currentWorldZ()
      val bx = -forwardX(currentSeg.dir)
      val bz = -forwardZ(currentSeg.dir)
      explorer.requestJump {
        sound.playJump()
        particles.spawnFootTrail(px, explorer.y, pz, bx, bz, 5, 0.4f)
        haptics.trigger(HapticKind.LIGHT)
      }
    } else {
      explorer.requestSlide {
        sound.playSlide()
        haptics.trigger(HapticKind.LIGHT)
      }
    }
  }

  fun startRun() {
    val rng: () -> Float =
      if (isDailyMode) {
        val seeded = SeededRng("opssyrun-daily-${todaySeedDate()}")
        val fn: () -> Float = { seeded.nextFloat() }
        fn
      } else {
        { Random.nextFloat() }
      }

    currentSeg = track.reset(rng, spawnObstacles = true)
    segProgress = 6f
    explorer.reset(0f)
    particles.reset()

    distance = 0f
    bonusDistance = 0f
    runNaira = 0
    isNewRecord = false
    forwardSpeed = GamePhysics.BASE_SPEED
    stumbleSlowTimer = 0f
    chaserTimer = 0f
    invulnTimer = 0f
    closeCallCounter = 0
    lastBiome = BiomeId.COURTYARD
    biomeAnnouncement = null
    biomeBannerTimer = 0f

    magnetRemaining = 0f
    magnetMax = getMagnetDuration(progress.magnetLevel)
    hasShield = progress.shieldLevel >= 3
    val headStartDur = getHeadStartDuration(progress.headstartLevel)
    dashRemaining = headStartDur
    dashMax = maxOf(headStartDur, getDashDuration(progress.dashLevel))
    if (headStartDur > 0f) {
      sound.playRelicPickup(RelicKind.DASH)
    }

    camYaw = 0f
    camX = 0f
    camZ = 14f
    shakeIntensity = 0f
    lastFrameNs = System.nanoTime()
    phase = GamePhase.PLAYING
    haptics.trigger(HapticKind.LIGHT)
    emitSnapshot()
  }

  fun togglePause() {
    if (phase == GamePhase.PLAYING) {
      phase = GamePhase.PAUSED
      emitSnapshot()
    } else if (phase == GamePhase.PAUSED) {
      lastFrameNs = System.nanoTime()
      phase = GamePhase.PLAYING
      emitSnapshot()
    }
  }

  fun pauseIfPlaying() {
    if (phase == GamePhase.PLAYING) {
      phase = GamePhase.PAUSED
      emitSnapshot()
    }
  }

  fun returnToTitle() {
    currentSeg = track.reset({ Random.nextFloat() }, spawnObstacles = false)
    segProgress = 8f
    explorer.reset(0f)
    particles.reset()
    chaserTimer = 0f
    magnetRemaining = 0f
    hasShield = false
    dashRemaining = 0f
    phase = GamePhase.TITLE
    emitSnapshot()
  }

  fun setDailyMode(enabled: Boolean) {
    isDailyMode = enabled
    emitSnapshot()
  }

  fun toggleMute() {
    val next = !sound.isMuted
    sound.isMuted = next
    progress = progress.copy(isMuted = next)
    saveAndNotifyProgress()
  }

  fun buyOrEquipCharacter(id: CharacterId) {
    val def = GameCatalog.CHARACTERS.firstOrNull { it.id == id } ?: return
    if (id in progress.unlockedCharacters) {
      progress = progress.copy(equippedCharacter = id)
      explorer.setCharacter(id)
      haptics.trigger(HapticKind.LIGHT)
      saveAndNotifyProgress()
      return
    }
    if (progress.naira >= def.priceNaira) {
      progress =
        progress.copy(
          naira = progress.naira - def.priceNaira,
          unlockedCharacters = progress.unlockedCharacters + id,
          equippedCharacter = id,
        )
      explorer.setCharacter(id)
      sound.playRelicPickup(RelicKind.SHIELD)
      haptics.trigger(HapticKind.MEDIUM)
      saveAndNotifyProgress()
    }
  }

  fun buyOrEquipTrail(id: TrailId) {
    val def = GameCatalog.TRAILS.firstOrNull { it.id == id } ?: return
    if (id in progress.unlockedTrails) {
      progress = progress.copy(equippedTrail = id)
      particles.setTrail(id)
      haptics.trigger(HapticKind.LIGHT)
      saveAndNotifyProgress()
      return
    }
    if (progress.naira >= def.priceNaira) {
      progress =
        progress.copy(
          naira = progress.naira - def.priceNaira,
          unlockedTrails = progress.unlockedTrails + id,
          equippedTrail = id,
        )
      particles.setTrail(id)
      sound.playCoin()
      haptics.trigger(HapticKind.MEDIUM)
      saveAndNotifyProgress()
    }
  }

  fun buyUpgrade(key: UpgradeKey) {
    val def = GameCatalog.UPGRADES.firstOrNull { it.key == key } ?: return
    val currentLv = progress.upgradeLevel(key)
    if (currentLv >= def.maxLevel) return
    val cost = def.costs[currentLv - 1]
    if (progress.naira < cost) return

    progress =
      progress
        .copy(naira = progress.naira - cost)
        .withUpgradeLevel(key, currentLv + 1)
    sound.playRelicPickup(RelicKind.MAGNET)
    haptics.trigger(HapticKind.MEDIUM)
    saveAndNotifyProgress()
  }

  private fun saveAndNotifyProgress() {
    prefsRepository.saveProgress(progress)
    _progressFlow.value = progress
    emitSnapshot()
  }

  private fun emitSnapshot() {
    val totalDist = (distance + bonusDistance).toInt()
    lastReportedDist = totalDist
    val turnHint =
      if (phase == GamePhase.PLAYING) {
        track.getTurnHint(currentSeg, segProgress)
      } else {
        com.example.game.TurnHint.NONE
      }
    _snapshotFlow.value =
      GameSnapshot(
        phase = phase,
        distance = totalDist,
        runNaira = runNaira,
        totalNaira = progress.naira,
        highScore = progress.highScore,
        dailyHighScore = progress.dailyHighScore,
        isDailyMode = isDailyMode,
        dailySeedDate = progress.dailySeedDate,
        isNewRecord = isNewRecord,
        isMuted = sound.isMuted,
        speedRatio =
          ((forwardSpeed - GamePhysics.BASE_SPEED) / (GamePhysics.MAX_SPEED - GamePhysics.BASE_SPEED))
            .coerceIn(0f, 1f),
        biome = track.getBiomeAtDistance(totalDist),
        turnHint = turnHint,
        chaserDanger = (chaserTimer / 8.5f).coerceIn(0f, 1f),
        relics =
          ActiveRelicTimers(
            magnetRemaining = magnetRemaining,
            magnetMax = magnetMax,
            hasShield = hasShield,
            dashRemaining = dashRemaining,
            dashMax = dashMax,
          ),
        closeCallCounter = closeCallCounter,
        biomeAnnouncement = biomeAnnouncement,
      )
  }
}
