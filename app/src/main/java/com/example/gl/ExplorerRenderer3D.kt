package com.example.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.CharacterId
import com.example.game.GamePhysics
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class ExplorerRenderer3D {
  var targetLaneIndex = 1
  var laneX = 0f
  var y = 0f
  private var vy = 0f
  private var grounded = true
  private var slideTimer = 0f
  private var jumpBufferTimer = 0f
  private var stumbleTimer = 0f
  private var crashed = false
  var visualYaw = 0f

  private var bankAngle = 0f
  private var squashY = 1f
  private var squashXZ = 1f
  private var runPhase = 0f
  private var lastStepHalf = 0
  private var characterId = CharacterId.EXPLORER

  private val baseMatrix = FloatArray(16)
  private val partMatrix = FloatArray(16)

  fun setCharacter(id: CharacterId) {
    characterId = id
  }

  fun reset(startYaw: Float = 0f) {
    targetLaneIndex = 1
    laneX = GamePhysics.LANES[1]
    y = 0f
    vy = 0f
    grounded = true
    slideTimer = 0f
    jumpBufferTimer = 0f
    stumbleTimer = 0f
    crashed = false
    visualYaw = startYaw
    bankAngle = 0f
    squashY = 1f
    squashXZ = 1f
    runPhase = 0f
    lastStepHalf = 0
  }

  fun shiftLane(delta: Int): Boolean {
    if (crashed) return false
    val next = (targetLaneIndex + delta).coerceIn(0, 2)
    if (next == targetLaneIndex) return false
    targetLaneIndex = next
    return true
  }

  fun requestJump(onJump: () -> Unit) {
    if (crashed) return
    if (grounded) {
      executeJump(onJump)
    } else {
      jumpBufferTimer = GamePhysics.INPUT_BUFFER_SECONDS
    }
  }

  fun requestSlide(onSlideStart: () -> Unit) {
    if (crashed) return
    if (!grounded) {
      vy = minOf(vy, GamePhysics.FAST_FALL_VELOCITY)
      slideTimer = GamePhysics.SLIDE_DURATION
      return
    }
    if (slideTimer <= 0.08f) {
      onSlideStart()
    }
    slideTimer = GamePhysics.SLIDE_DURATION
    squashY = 0.65f
    squashXZ = 1.24f
  }

  fun boostJump(velocity: Float) {
    vy = velocity
    y = maxOf(y, 0.25f)
    grounded = false
    slideTimer = 0f
    squashY = 1.25f
    squashXZ = 0.85f
  }

  fun triggerStumble() {
    stumbleTimer = 0.72f
    squashY = 0.82f
    squashXZ = 1.15f
  }

  fun triggerCrash() {
    crashed = true
    slideTimer = 0f
  }

  fun isSliding(): Boolean = slideTimer > 0f && grounded

  fun getLocalHeight(): Float = if (slideTimer > 0f && grounded) 0.92f else 1.88f

  private fun executeJump(onJump: () -> Unit) {
    vy = GamePhysics.JUMP_VELOCITY
    grounded = false
    slideTimer = 0f
    jumpBufferTimer = 0f
    squashY = 1.24f
    squashXZ = 0.84f
    onJump()
  }

  fun update(
    dt: Float,
    forwardSpeed: Float,
    targetYaw: Float,
    onJump: () -> Unit,
    onFootstep: () -> Unit,
    onLand: () -> Unit,
    onSlideTick: () -> Unit,
  ) {
    // Lane smoothing & banking tilt
    val desiredX = GamePhysics.LANES[targetLaneIndex]
    val prevLaneX = laneX
    val damp = 1f - exp(-GamePhysics.LANE_SWITCH_DAMPING * dt)
    laneX += (desiredX - laneX) * damp
    val lateralVel = (laneX - prevLaneX) / dt.coerceAtLeast(0.001f)
    val desiredBank = (-lateralVel * 0.045f).coerceIn(-0.42f, 0.42f)
    bankAngle += (desiredBank - bankAngle) * (1f - exp(-14f * dt))

    // Yaw smoothing around 90-degree corners
    var diff = targetYaw - visualYaw
    while (diff > PI.toFloat()) diff -= (PI * 2.0).toFloat()
    while (diff < -PI.toFloat()) diff += (PI * 2.0).toFloat()
    visualYaw += diff * (1f - exp(-12f * dt))

    if (jumpBufferTimer > 0f) jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)
    if (stumbleTimer > 0f) stumbleTimer = (stumbleTimer - dt).coerceAtLeast(0f)

    if (!crashed) {
      if (!grounded) {
        vy -= GamePhysics.GRAVITY * dt
        y += vy * dt
        if (y <= 0f) {
          y = 0f
          vy = 0f
          grounded = true
          squashY = 0.78f
          squashXZ = 1.18f
          onLand()
          if (jumpBufferTimer > 0f) {
            executeJump(onJump)
          }
        }
      } else if (slideTimer > 0f) {
        slideTimer = (slideTimer - dt).coerceAtLeast(0f)
        if (slideTimer > 0.05f) {
          onSlideTick()
        }
      }
    }

    // Squash & stretch recovery
    val sqDamp = 1f - exp(-14f * dt)
    squashY += (1f - squashY) * sqDamp
    squashXZ += (1f - squashXZ) * sqDamp

    if (!crashed && grounded && slideTimer <= 0f) {
      runPhase += dt * (forwardSpeed * 0.68f)
      val half = (runPhase / PI.toFloat()).toInt()
      if (half != lastStepHalf) {
        lastStepHalf = half
        onFootstep()
      }
    }
  }

  fun render(
    shader: GlShaderProgram,
    whiteTex: Int,
    worldX: Float,
    worldZ: Float,
    timeSec: Float,
    hasShield: Boolean,
    hasMagnet: Boolean,
    hasDash: Boolean,
  ) {
    // Ground contact shadow
    val shadowScale = (1f - y * 0.16f).coerceIn(0.42f, 1f)
    Matrix.setIdentityM(shader.modelMatrix, 0)
    Matrix.translateM(shader.modelMatrix, 0, worldX, 0.03f, worldZ)
    Matrix.scaleM(shader.modelMatrix, 0, 0.92f * shadowScale, 0.02f, 0.92f * shadowScale)
    shader.drawMesh(shader.cylinderMesh, whiteTex, 0.12f, 0.16f, 0.18f, 0.55f)

    // Explorer root transform
    Matrix.setIdentityM(baseMatrix, 0)
    Matrix.translateM(baseMatrix, 0, worldX, y, worldZ)
    Matrix.rotateM(baseMatrix, 0, Math.toDegrees(visualYaw.toDouble()).toFloat(), 0f, 1f, 0f)

    if (crashed) {
      Matrix.translateM(baseMatrix, 0, 0f, 0.35f, 0.2f)
      Matrix.rotateM(baseMatrix, 0, -76f, 1f, 0f, 0f)
      Matrix.rotateM(baseMatrix, 0, 18f, 0f, 0f, 1f)
    } else {
      val bankDeg = Math.toDegrees(bankAngle.toDouble()).toFloat()
      Matrix.rotateM(baseMatrix, 0, bankDeg, 0f, 0f, 1f)
      if (slideTimer > 0f && grounded) {
        Matrix.translateM(baseMatrix, 0, 0f, 0.32f, 0f)
        Matrix.rotateM(baseMatrix, 0, 56f, 1f, 0f, 0f)
      } else {
        val bob = if (grounded) abs(sin(runPhase)) * 0.11f else 0f
        Matrix.translateM(baseMatrix, 0, 0f, bob, 0f)
        val stumblePitch = if (stumbleTimer > 0f) sin(stumbleTimer * 28f) * 14f else 0f
        Matrix.rotateM(baseMatrix, 0, -10f + stumblePitch, 1f, 0f, 0f)
      }
      Matrix.scaleM(baseMatrix, 0, squashXZ, squashY, squashXZ)
    }

    val legSwing = if (grounded && slideTimer <= 0f && !crashed) sin(runPhase) * 42f else -24f
    val armSwing = -legSwing * 0.85f

    when (characterId) {
      CharacterId.RONIN -> renderRonin(shader, whiteTex, legSwing, armSwing, timeSec)
      CharacterId.PANDA -> renderPanda(shader, whiteTex, legSwing, armSwing, timeSec)
      else -> renderTraveler(shader, whiteTex, legSwing, armSwing, timeSec)
    }

    // Active Relic Visuals
    if (hasMagnet) {
      Matrix.setIdentityM(shader.modelMatrix, 0)
      Matrix.translateM(shader.modelMatrix, 0, worldX, y + 0.95f, worldZ)
      Matrix.rotateM(shader.modelMatrix, 0, timeSec * 140f, 0f, 1f, 0f)
      Matrix.scaleM(shader.modelMatrix, 0, 1.35f, 0.08f, 1.35f)
      shader.drawMesh(shader.cylinderMesh, whiteTex, 0.32f, 0.72f, 0.53f, 0.48f, 0.12f, 0.45f, 0.25f)
    }
    if (hasDash) {
      Matrix.setIdentityM(shader.modelMatrix, 0)
      Matrix.translateM(shader.modelMatrix, 0, worldX, y + 1.0f, worldZ)
      Matrix.rotateM(shader.modelMatrix, 0, Math.toDegrees(visualYaw.toDouble()).toFloat(), 0f, 1f, 0f)
      val pulse = 1.15f + sin(timeSec * 18f) * 0.12f
      Matrix.scaleM(shader.modelMatrix, 0, 0.95f * pulse, 1.65f * pulse, 1.15f * pulse)
      shader.drawMesh(shader.octaMesh, whiteTex, 1.0f, 0.74f, 0.24f, 0.38f, 0.65f, 0.35f, 0.05f)
    }
    if (hasShield) {
      GLES20.glEnable(GLES20.GL_BLEND)
      GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
      Matrix.setIdentityM(shader.modelMatrix, 0)
      Matrix.translateM(shader.modelMatrix, 0, worldX, y + 1.05f, worldZ)
      Matrix.rotateM(shader.modelMatrix, 0, timeSec * 70f, 0f, 1f, 0f)
      Matrix.scaleM(shader.modelMatrix, 0, 1.48f, 2.15f, 1.48f)
      shader.drawMesh(shader.octaMesh, whiteTex, 1.0f, 0.82f, 0.40f, 0.30f, 0.32f, 0.22f, 0.04f)
    }
  }

  fun renderChasers(
    shader: GlShaderProgram,
    whiteTex: Int,
    worldX: Float,
    worldZ: Float,
    backX: Float,
    backZ: Float,
    yawRad: Float,
    danger: Float,
    timeSec: Float,
  ) {
    if (danger <= 0.02f) return
    val distBack = 5.4f - danger * 2.5f
    val rightX = cos(yawRad)
    val rightZ = -sin(yawRad)
    val offsets = floatArrayOf(-1.15f, 1.15f)
    for (idx in offsets.indices) {
      val ox = offsets[idx]
      val hx = worldX + backX * distBack + rightX * ox
      val hz = worldZ + backZ * distBack + rightZ * ox
      val leap = abs(sin(timeSec * 12f + idx * 1.4f)) * 0.32f

      Matrix.setIdentityM(baseMatrix, 0)
      Matrix.translateM(baseMatrix, 0, hx, 0.45f + leap, hz)
      Matrix.rotateM(baseMatrix, 0, Math.toDegrees(yawRad.toDouble()).toFloat(), 0f, 1f, 0f)

      // Spectral stone hound body
      drawPart(shader, whiteTex, 0f, 0.1f, 0f, 0.48f, 0.42f, 0.96f, 0.18f, 0.28f, 0.34f)
      // Head
      drawPart(shader, whiteTex, 0f, 0.34f, -0.52f, 0.36f, 0.32f, 0.42f, 0.20f, 0.30f, 0.36f)
      // Glowing amber eyes
      drawPart(shader, whiteTex, -0.10f, 0.38f, -0.74f, 0.08f, 0.07f, 0.06f, 1.0f, 0.42f, 0.18f, emissive = 0.9f)
      drawPart(shader, whiteTex, 0.10f, 0.38f, -0.74f, 0.08f, 0.07f, 0.06f, 1.0f, 0.42f, 0.18f, emissive = 0.9f)
    }
  }

  private fun renderTraveler(
    shader: GlShaderProgram,
    whiteTex: Int,
    legSwing: Float,
    armSwing: Float,
    timeSec: Float,
  ) {
    val tunicR: Float; val tunicG: Float; val tunicB: Float
    val scarfR: Float; val scarfG: Float; val scarfB: Float
    val hatR: Float; val hatG: Float; val hatB: Float
    when (characterId) {
      CharacterId.NOMAD -> {
        tunicR = 0.62f; tunicG = 0.18f; tunicB = 0.18f
        scarfR = 0.96f; scarfG = 0.64f; scarfB = 0.38f
        hatR = 0.50f; hatG = 0.14f; hatB = 0.14f
      }
      CharacterId.SCOUT -> {
        tunicR = 0.18f; tunicG = 0.42f; tunicB = 0.31f
        scarfR = 0.83f; scarfG = 0.64f; scarfB = 0.45f
        hatR = 0.22f; hatG = 0.48f; hatB = 0.34f
      }
      else -> {
        tunicR = 0.15f; tunicG = 0.27f; tunicB = 0.33f
        scarfR = 0.78f; scarfG = 0.35f; scarfB = 0.23f
        hatR = 0.29f; hatG = 0.21f; hatB = 0.15f
      }
    }

    // Legs
    drawLimb(shader, whiteTex, -0.14f, 0.72f, 0f, legSwing, 0.18f, 0.68f, 0.20f, 0.29f, 0.21f, 0.15f)
    drawLimb(shader, whiteTex, 0.14f, 0.72f, 0f, -legSwing, 0.18f, 0.68f, 0.20f, 0.29f, 0.21f, 0.15f)
    // Torso tunic
    drawPart(shader, whiteTex, 0f, 1.06f, 0f, 0.50f, 0.64f, 0.32f, tunicR, tunicG, tunicB)
    // Gold belt buckle
    drawPart(shader, whiteTex, 0f, 0.80f, 0f, 0.52f, 0.10f, 0.34f, 0.96f, 0.75f, 0.32f)
    // Arms
    drawLimb(shader, whiteTex, -0.34f, 1.28f, 0f, armSwing, 0.15f, 0.54f, 0.16f, 0.87f, 0.71f, 0.55f)
    drawLimb(shader, whiteTex, 0.34f, 1.28f, 0f, -armSwing, 0.15f, 0.54f, 0.16f, 0.87f, 0.71f, 0.55f)
    // Head
    drawPart(shader, whiteTex, 0f, 1.56f, 0f, 0.34f, 0.34f, 0.34f, 0.89f, 0.73f, 0.57f)
    // Explorer wide-brim hat / hood
    drawPart(shader, whiteTex, 0f, 1.74f, 0f, 0.66f, 0.06f, 0.66f, hatR, hatG, hatB, cylinder = true)
    drawPart(shader, whiteTex, 0f, 1.84f, 0f, 0.38f, 0.18f, 0.38f, hatR, hatG, hatB, cylinder = true)
    // Flowing scarf tail
    val flutter = sin(timeSec * 14f) * 10f
    drawLimb(shader, whiteTex, 0f, 1.36f, 0.18f, 52f + flutter, 0.18f, 0.52f, 0.08f, scarfR, scarfG, scarfB)
  }

  private fun renderRonin(
    shader: GlShaderProgram,
    whiteTex: Int,
    legSwing: Float,
    armSwing: Float,
    timeSec: Float,
  ) {
    // Obsidian armor legs & torso
    drawLimb(shader, whiteTex, -0.14f, 0.72f, 0f, legSwing, 0.19f, 0.68f, 0.21f, 0.11f, 0.13f, 0.17f)
    drawLimb(shader, whiteTex, 0.14f, 0.72f, 0f, -legSwing, 0.19f, 0.68f, 0.21f, 0.11f, 0.13f, 0.17f)
    drawPart(shader, whiteTex, 0f, 1.06f, 0f, 0.54f, 0.66f, 0.34f, 0.14f, 0.16f, 0.20f)
    // Pauldrons
    drawPart(shader, whiteTex, -0.34f, 1.32f, 0f, 0.22f, 0.14f, 0.32f, 0.22f, 0.25f, 0.30f)
    drawPart(shader, whiteTex, 0.34f, 1.32f, 0f, 0.22f, 0.14f, 0.32f, 0.22f, 0.25f, 0.30f)
    // Arms
    drawLimb(shader, whiteTex, -0.35f, 1.24f, 0f, armSwing, 0.14f, 0.52f, 0.15f, 0.15f, 0.18f, 0.22f)
    drawLimb(shader, whiteTex, 0.35f, 1.24f, 0f, -armSwing, 0.14f, 0.52f, 0.15f, 0.15f, 0.18f, 0.22f)
    // Twin katana scabbards on back
    drawPartRot(shader, whiteTex, 0.08f, 1.24f, 0.22f, 34f, 0.06f, 0.95f, 0.06f, 0.78f, 0.80f, 0.84f)
    drawPartRot(shader, whiteTex, -0.08f, 1.24f, 0.22f, -34f, 0.06f, 0.95f, 0.06f, 0.78f, 0.80f, 0.84f)
    // Helm & cyber visor
    drawPart(shader, whiteTex, 0f, 1.56f, 0f, 0.34f, 0.34f, 0.34f, 0.10f, 0.12f, 0.16f)
    drawPart(shader, whiteTex, 0f, 1.57f, -0.17f, 0.26f, 0.07f, 0.04f, 0.96f, 0.80f, 0.36f, emissive = 0.7f)
    // Wide conical Kasa hat
    drawPart(shader, whiteTex, 0f, 1.76f, 0f, 0.82f, 0.07f, 0.82f, 0.22f, 0.24f, 0.28f, cylinder = true)
    drawPart(shader, whiteTex, 0f, 1.84f, 0f, 0.44f, 0.12f, 0.44f, 0.16f, 0.18f, 0.22f, cylinder = true)
    // Flowing dark sash
    drawLimb(shader, whiteTex, 0f, 1.32f, 0.20f, 52f + sin(timeSec * 14f) * 10f, 0.16f, 0.48f, 0.06f, 0.76f, 0.22f, 0.20f)
  }

  private fun renderPanda(
    shader: GlShaderProgram,
    whiteTex: Int,
    legSwing: Float,
    armSwing: Float,
    timeSec: Float,
  ) {
    // Dark trousers & white/black panda fleece hoodie
    drawLimb(shader, whiteTex, -0.15f, 0.70f, 0f, legSwing, 0.21f, 0.66f, 0.22f, 0.16f, 0.16f, 0.18f)
    drawLimb(shader, whiteTex, 0.15f, 0.70f, 0f, -legSwing, 0.21f, 0.66f, 0.22f, 0.16f, 0.16f, 0.18f)
    // Cozy grey fleece torso
    drawPart(shader, whiteTex, 0f, 1.06f, 0f, 0.60f, 0.66f, 0.40f, 0.46f, 0.46f, 0.48f)
    // Chunky hammered gold chain on chest!
    drawPart(shader, whiteTex, 0f, 1.24f, -0.20f, 0.34f, 0.10f, 0.08f, 1.0f, 0.82f, 0.34f, emissive = 0.25f)
    // Arms
    drawLimb(shader, whiteTex, -0.38f, 1.26f, 0f, armSwing, 0.18f, 0.54f, 0.18f, 0.42f, 0.42f, 0.44f)
    drawLimb(shader, whiteTex, 0.38f, 1.26f, 0f, -armSwing, 0.18f, 0.54f, 0.18f, 0.42f, 0.42f, 0.44f)
    // White Panda Head + dark eye patches + round ears
    drawPart(shader, whiteTex, 0f, 1.58f, 0f, 0.42f, 0.38f, 0.38f, 0.96f, 0.95f, 0.92f)
    drawPart(shader, whiteTex, -0.18f, 1.80f, 0f, 0.14f, 0.14f, 0.12f, 0.12f, 0.12f, 0.14f, cylinder = true)
    drawPart(shader, whiteTex, 0.18f, 1.80f, 0f, 0.14f, 0.14f, 0.12f, 0.12f, 0.12f, 0.14f, cylinder = true)
    drawPart(shader, whiteTex, -0.10f, 1.60f, -0.19f, 0.10f, 0.09f, 0.04f, 0.10f, 0.10f, 0.12f)
    drawPart(shader, whiteTex, 0.10f, 1.60f, -0.19f, 0.10f, 0.09f, 0.04f, 0.10f, 0.10f, 0.12f)
    // Subtle hoodie tail bounce
    drawLimb(shader, whiteTex, 0f, 1.32f, 0.22f, 44f + sin(timeSec * 11f) * 8f, 0.24f, 0.24f, 0.12f, 0.44f, 0.44f, 0.46f)
  }

  private fun drawPart(
    shader: GlShaderProgram,
    tex: Int,
    lx: Float,
    ly: Float,
    lz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
    cylinder: Boolean = false,
    emissive: Float = 0f,
  ) {
    System.arraycopy(baseMatrix, 0, partMatrix, 0, 16)
    Matrix.translateM(partMatrix, 0, lx, ly, lz)
    Matrix.scaleM(partMatrix, 0, sx, sy, sz)
    System.arraycopy(partMatrix, 0, shader.modelMatrix, 0, 16)
    shader.drawMesh(
      mesh = if (cylinder) shader.cylinderMesh else shader.boxMesh,
      textureId = tex,
      r = r,
      g = g,
      b = b,
      emissiveR = r * emissive,
      emissiveG = g * emissive,
      emissiveB = b * emissive,
    )
  }

  private fun drawPartRot(
    shader: GlShaderProgram,
    tex: Int,
    lx: Float,
    ly: Float,
    lz: Float,
    rotZDeg: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
  ) {
    System.arraycopy(baseMatrix, 0, partMatrix, 0, 16)
    Matrix.translateM(partMatrix, 0, lx, ly, lz)
    Matrix.rotateM(partMatrix, 0, rotZDeg, 0f, 0f, 1f)
    Matrix.scaleM(partMatrix, 0, sx, sy, sz)
    System.arraycopy(partMatrix, 0, shader.modelMatrix, 0, 16)
    shader.drawMesh(shader.boxMesh, tex, r, g, b)
  }

  private fun drawLimb(
    shader: GlShaderProgram,
    tex: Int,
    px: Float,
    py: Float,
    pz: Float,
    rotXDeg: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
  ) {
    System.arraycopy(baseMatrix, 0, partMatrix, 0, 16)
    Matrix.translateM(partMatrix, 0, px, py, pz)
    Matrix.rotateM(partMatrix, 0, rotXDeg, 1f, 0f, 0f)
    Matrix.translateM(partMatrix, 0, 0f, -sy * 0.5f, 0f)
    Matrix.scaleM(partMatrix, 0, sx, sy, sz)
    System.arraycopy(partMatrix, 0, shader.modelMatrix, 0, 16)
    shader.drawMesh(shader.boxMesh, tex, r, g, b)
  }
}
