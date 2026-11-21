package com.example.gl

import android.opengl.Matrix
import com.example.game.TrailId
import kotlin.random.Random

class ParticleSystem3D {
  private class Particle {
    var active = false
    var x = 0f
    var y = 0f
    var z = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var life = 0f
    var maxLife = 0.5f
    var size = 0.14f
    var r = 1f
    var g = 1f
    var b = 1f
    var emissive = 0f
  }

  private val pool = Array(100) { Particle() }
  private var trailId = TrailId.DUST

  fun setTrail(id: TrailId) {
    trailId = id
  }

  fun reset() {
    for (p in pool) p.active = false
  }

  fun spawnFootTrail(x: Float, y: Float, z: Float, backX: Float, backZ: Float, count: Int, scale: Float) {
    repeat(count) {
      val p = acquire() ?: return
      p.x = x + (Random.nextFloat() - 0.5f) * 0.45f
      p.y = y + 0.08f + Random.nextFloat() * 0.12f
      p.z = z + (Random.nextFloat() - 0.5f) * 0.45f
      p.vx = backX * (1.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * 1.4f
      p.vy = 0.8f + Random.nextFloat() * 1.6f
      p.vz = backZ * (1.5f + Random.nextFloat() * 2f) + (Random.nextFloat() - 0.5f) * 1.4f
      p.maxLife = 0.35f + Random.nextFloat() * 0.25f
      p.life = p.maxLife
      p.size = (0.12f + Random.nextFloat() * 0.10f) * scale * 2.5f
      when (trailId) {
        TrailId.DUST -> {
          p.r = 0.86f; p.g = 0.72f; p.b = 0.56f; p.emissive = 0.05f
        }
        TrailId.GOLDEN -> {
          p.r = 1.0f; p.g = 0.84f; p.b = 0.40f; p.emissive = 0.55f
        }
        TrailId.EMBER -> {
          p.r = 0.98f; p.g = 0.46f; p.b = 0.16f; p.emissive = 0.65f
        }
      }
    }
  }

  fun spawnBurst(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, count: Int = 10) {
    repeat(count) {
      val p = acquire() ?: return
      p.x = x
      p.y = y
      p.z = z
      p.vx = (Random.nextFloat() - 0.5f) * 5.2f
      p.vy = 1.8f + Random.nextFloat() * 4.2f
      p.vz = (Random.nextFloat() - 0.5f) * 5.2f
      p.maxLife = 0.42f + Random.nextFloat() * 0.25f
      p.life = p.maxLife
      p.size = 0.14f + Random.nextFloat() * 0.08f
      p.r = r; p.g = g; p.b = b; p.emissive = 0.65f
    }
  }

  fun spawnVaultEmber(x: Float, y: Float, z: Float) {
    val p = acquire() ?: return
    p.x = x + (Random.nextFloat() - 0.5f) * 9f
    p.y = y + Random.nextFloat() * 1.8f
    p.z = z + (Random.nextFloat() - 0.5f) * 9f
    p.vx = (Random.nextFloat() - 0.5f) * 0.7f
    p.vy = 1.4f + Random.nextFloat() * 1.8f
    p.vz = (Random.nextFloat() - 0.5f) * 0.7f
    p.maxLife = 1.1f + Random.nextFloat() * 0.7f
    p.life = p.maxLife
    p.size = 0.09f + Random.nextFloat() * 0.06f
    p.r = 1.0f; p.g = 0.55f; p.b = 0.18f; p.emissive = 0.85f
  }

  fun update(dt: Float) {
    for (p in pool) {
      if (!p.active) continue
      p.life -= dt
      if (p.life <= 0f) {
        p.active = false
        continue
      }
      p.x += p.vx * dt
      p.y += p.vy * dt
      p.z += p.vz * dt
      p.vy -= 4.5f * dt
    }
  }

  fun render(shader: GlShaderProgram, whiteTex: Int) {
    for (p in pool) {
      if (!p.active) continue
      val t = (p.life / p.maxLife).coerceIn(0f, 1f)
      val s = p.size * (0.4f + 0.6f * t)
      Matrix.setIdentityM(shader.modelMatrix, 0)
      Matrix.translateM(shader.modelMatrix, 0, p.x, p.y, p.z)
      Matrix.scaleM(shader.modelMatrix, 0, s, s, s)
      shader.drawMesh(
        mesh = shader.boxMesh,
        textureId = whiteTex,
        r = p.r,
        g = p.g,
        b = p.b,
        a = t,
        emissiveR = p.r * p.emissive,
        emissiveG = p.g * p.emissive,
        emissiveB = p.b * p.emissive,
      )
    }
  }

  private fun acquire(): Particle? {
    for (p in pool) {
      if (!p.active) {
        p.active = true
        return p
      }
    }
    return null
  }
}
