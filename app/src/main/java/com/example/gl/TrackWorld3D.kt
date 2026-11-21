package com.example.gl

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.game.BiomeId
import com.example.game.GamePhysics
import com.example.game.RelicKind
import com.example.game.TurnHint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class CardinalDir {
  NORTH,
  EAST,
  SOUTH,
  WEST,
}

enum class SegmentType {
  STRAIGHT,
  GAP,
  CORNER_LEFT,
  CORNER_RIGHT,
  T_JUNCTION,
}

enum class ObstacleKind {
  LOG,
  ARCH,
  PILLAR,
}

data class ObstacleData(
  val kind: ObstacleKind,
  val lane: Int,
  val minX: Float,
  val maxX: Float,
  val minY: Float,
  val maxY: Float,
  val minZ: Float,
  val maxZ: Float,
  var resolved: Boolean = false,
  var nearMissChecked: Boolean = false,
)

data class CoinData(
  val localX: Float,
  val localY: Float,
  val localZ: Float,
  var worldX: Float,
  var worldY: Float,
  var worldZ: Float,
  var collected: Boolean = false,
  var magnetized: Boolean = false,
)

data class RelicData(
  val kind: RelicKind,
  val localX: Float,
  val localZ: Float,
  var worldX: Float,
  var worldZ: Float,
  var collected: Boolean = false,
)

data class PropColumn(
  val localX: Float,
  val localZ: Float,
  val height: Float,
  val hasBrazier: Boolean,
  val hasVine: Boolean,
)

class TrackSegment3D(
  val id: Int,
  val type: SegmentType,
  val dir: CardinalDir,
  val originX: Float,
  val originZ: Float,
  val length: Float,
  val biome: BiomeId,
  val gapStart: Float? = null,
  val gapEnd: Float? = null,
) {
  var nextStraight: TrackSegment3D? = null
  var leftBranch: TrackSegment3D? = null
  var rightBranch: TrackSegment3D? = null
  var committedTurn: String? = null // "left" | "right" | null
  val obstacles = ArrayList<ObstacleData>()
  val coins = ArrayList<CoinData>()
  val relics = ArrayList<RelicData>()
  val props = ArrayList<PropColumn>()
}

fun dirToYaw(dir: CardinalDir): Float =
  when (dir) {
    CardinalDir.NORTH -> 0f
    CardinalDir.EAST -> (-PI / 2.0).toFloat()
    CardinalDir.SOUTH -> PI.toFloat()
    CardinalDir.WEST -> (PI / 2.0).toFloat()
  }

fun forwardX(dir: CardinalDir): Float =
  when (dir) {
    CardinalDir.NORTH -> 0f
    CardinalDir.EAST -> 1f
    CardinalDir.SOUTH -> 0f
    CardinalDir.WEST -> -1f
  }

fun forwardZ(dir: CardinalDir): Float =
  when (dir) {
    CardinalDir.NORTH -> -1f
    CardinalDir.EAST -> 0f
    CardinalDir.SOUTH -> 1f
    CardinalDir.WEST -> 0f
  }

fun rightX(dir: CardinalDir): Float =
  when (dir) {
    CardinalDir.NORTH -> 1f
    CardinalDir.EAST -> 0f
    CardinalDir.SOUTH -> -1f
    CardinalDir.WEST -> 0f
  }

fun rightZ(dir: CardinalDir): Float =
  when (dir) {
    CardinalDir.NORTH -> 0f
    CardinalDir.EAST -> 1f
    CardinalDir.SOUTH -> 0f
    CardinalDir.WEST -> -1f
  }

fun rotateDir(dir: CardinalDir, turn: String): CardinalDir {
  val order = arrayOf(CardinalDir.NORTH, CardinalDir.EAST, CardinalDir.SOUTH, CardinalDir.WEST)
  val idx = order.indexOf(dir)
  return if (turn == "right") order[(idx + 1) % 4] else order[(idx + 3) % 4]
}

class TrackWorld3D {
  private val activeSegments = ArrayList<TrackSegment3D>()
  private var nextId = 1
  private var rng: () -> Float = { Math.random().toFloat() }
  private var waveCounter = 0
  private var generatedDistance = 0f

  // Interpolated Biome Atmosphere
  val sunColor = floatArrayOf(1.0f, 0.87f, 0.63f)
  val hemiSky = floatArrayOf(0.24f, 0.42f, 0.48f)
  val hemiGround = floatArrayOf(0.36f, 0.48f, 0.35f)
  val fogColor = floatArrayOf(0.90f, 0.80f, 0.70f)
  var fogDensity = 0.0145f
    private set

  fun getBiomeAtDistance(distMeters: Int): BiomeId =
    when {
      distMeters < 500 -> BiomeId.COURTYARD
      distMeters < 1000 -> BiomeId.AQUEDUCT
      else -> BiomeId.VAULT
    }

  fun reset(rngFn: () -> Float, spawnObstacles: Boolean): TrackSegment3D {
    activeSegments.clear()
    rng = rngFn
    nextId = 1
    waveCounter = 0
    generatedDistance = 0f
    fogDensity = 0.0145f

    val root = createSegment(SegmentType.STRAIGHT, CardinalDir.NORTH, 0f, 12f, spawnObstacles = false)
    activeSegments.add(root)
    extendFromTail(root, count = 6, spawnObstacles = spawnObstacles)
    return root
  }

  fun advanceMaintenance(currentSeg: TrackSegment3D, spawnObstacles: Boolean) {
    // Keep only current segment and reachable ahead segments
    val keep = HashSet<TrackSegment3D>()
    fun collect(seg: TrackSegment3D?, depth: Int) {
      if (seg == null || depth > 7 || seg in keep) return
      keep.add(seg)
      collect(seg.nextStraight, depth + 1)
      collect(seg.leftBranch, depth + 1)
      collect(seg.rightBranch, depth + 1)
    }
    collect(currentSeg, 0)
    activeSegments.retainAll(keep)

    // Ensure linear tail ahead has enough depth
    var tail: TrackSegment3D = currentSeg
    var steps = 0
    while (steps < 6) {
      val next = tail.nextStraight ?: tail.rightBranch ?: tail.leftBranch
      if (next == null) {
        extendFromTail(tail, count = 2, spawnObstacles = spawnObstacles)
        break
      }
      tail = next
      steps++
    }
  }

  fun commitJunctionBranch(junction: TrackSegment3D, side: String): TrackSegment3D? {
    val chosen = if (side == "left") junction.leftBranch else junction.rightBranch
    if (chosen != null) {
      extendFromTail(chosen, count = 5, spawnObstacles = true)
    }
    return chosen
  }

  fun getTurnHint(seg: TrackSegment3D, segProgress: Float): TurnHint {
    if (segProgress < 1.0f || segProgress > seg.length - 0.3f || seg.committedTurn != null) {
      return TurnHint.NONE
    }
    return when (seg.type) {
      SegmentType.CORNER_LEFT -> TurnHint.LEFT
      SegmentType.CORNER_RIGHT -> TurnHint.RIGHT
      SegmentType.T_JUNCTION -> TurnHint.BOTH
      else -> TurnHint.NONE
    }
  }

  private fun extendFromTail(tailStart: TrackSegment3D, count: Int, spawnObstacles: Boolean) {
    var cursor = tailStart
    repeat(count) {
      val fwdX = forwardX(cursor.dir)
      val fwdZ = forwardZ(cursor.dir)
      val endX = cursor.originX + fwdX * cursor.length
      val endZ = cursor.originZ + fwdZ * cursor.length

      waveCounter++
      val biome = getBiomeAtDistance(generatedDistance.toInt())

      val segType =
        when {
          !spawnObstacles -> SegmentType.STRAIGHT
          waveCounter > 4 && waveCounter % 6 == 0 -> {
            val pick = rng()
            when {
              pick < 0.36f -> SegmentType.CORNER_LEFT
              pick < 0.72f -> SegmentType.CORNER_RIGHT
              else -> SegmentType.T_JUNCTION
            }
          }
          biome == BiomeId.AQUEDUCT && waveCounter % 3 == 0 -> SegmentType.GAP
          else -> SegmentType.STRAIGHT
        }

      if (segType == SegmentType.CORNER_LEFT || segType == SegmentType.CORNER_RIGHT || segType == SegmentType.T_JUNCTION) {
        val junction = createSegment(segType, cursor.dir, endX, endZ, spawnObstacles = false)
        activeSegments.add(junction)
        cursor.nextStraight = junction

        val junctionCenterX = endX + fwdX * (junction.length - 4.7f)
        val junctionCenterZ = endZ + fwdZ * (junction.length - 4.7f)

        if (segType == SegmentType.CORNER_LEFT || segType == SegmentType.T_JUNCTION) {
          val lDir = rotateDir(cursor.dir, "left")
          val lStartX = junctionCenterX + forwardX(lDir) * 4.7f
          val lStartZ = junctionCenterZ + forwardZ(lDir) * 4.7f
          val lSeg = createSegment(SegmentType.STRAIGHT, lDir, lStartX, lStartZ, spawnObstacles = true)
          activeSegments.add(lSeg)
          junction.leftBranch = lSeg
        }
        if (segType == SegmentType.CORNER_RIGHT || segType == SegmentType.T_JUNCTION) {
          val rDir = rotateDir(cursor.dir, "right")
          val rStartX = junctionCenterX + forwardX(rDir) * 4.7f
          val rStartZ = junctionCenterZ + forwardZ(rDir) * 4.7f
          val rSeg = createSegment(SegmentType.STRAIGHT, rDir, rStartX, rStartZ, spawnObstacles = true)
          activeSegments.add(rSeg)
          junction.rightBranch = rSeg
        }
        // Keep cursor on primary branch for lookahead depth
        cursor = junction.rightBranch ?: junction.leftBranch ?: junction
      } else {
        val seg = createSegment(segType, cursor.dir, endX, endZ, spawnObstacles = spawnObstacles)
        activeSegments.add(seg)
        cursor.nextStraight = seg
        cursor = seg
      }
    }
  }

  private fun createSegment(
    type: SegmentType,
    dir: CardinalDir,
    originX: Float,
    originZ: Float,
    spawnObstacles: Boolean,
  ): TrackSegment3D {
    val length =
      when (type) {
        SegmentType.CORNER_LEFT, SegmentType.CORNER_RIGHT, SegmentType.T_JUNCTION -> 14.0f
        else -> 22.0f
      }
    val biome = getBiomeAtDistance(generatedDistance.toInt())
    generatedDistance += length

    val gapStart = if (type == SegmentType.GAP) 9.4f else null
    val gapEnd = if (type == SegmentType.GAP) 13.0f else null
    val seg = TrackSegment3D(nextId++, type, dir, originX, originZ, length, biome, gapStart, gapEnd)

    // Flanking stone pillars / vines / braziers
    val propCount = if (length > 16f) 3 else 2
    for (i in 0 until propCount) {
      val pz = 3.5f + i * 6.8f
      seg.props.add(
        PropColumn(
          localX = -6.0f,
          localZ = pz,
          height = 5.2f + (i % 2) * 1.4f,
          hasBrazier = biome == BiomeId.VAULT,
          hasVine = biome != BiomeId.VAULT && i % 2 == 0,
        )
      )
      seg.props.add(
        PropColumn(
          localX = 6.0f,
          localZ = pz,
          height = 5.4f + ((i + 1) % 2) * 1.2f,
          hasBrazier = biome == BiomeId.VAULT,
          hasVine = biome != BiomeId.VAULT && i % 2 == 1,
        )
      )
    }

    if (type == SegmentType.GAP) {
      // Jump arc coins across the broken aqueduct gap
      val lane = (rng() * 3f).toInt().coerceIn(0, 2)
      val lx = GamePhysics.LANES[lane]
      for (k in -1..1) {
        val cz = 11.2f + k * 1.3f
        val cy = if (k == 0) 2.25f else 1.55f
        addCoin(seg, lx, cy, cz)
      }
      return seg
    }

    if (!spawnObstacles || type != SegmentType.STRAIGHT) return seg

    // Curated rhythmic obstacle wave (guaranteed safe lane!)
    val safeLane = (rng() * 3f).toInt().coerceIn(0, 2)
    val pattern = rng()
    val zPos = 10.5f

    if (pattern < 0.34f) {
      // Fallen Timber Log + 3-coin jump arc
      val logLane = (safeLane + 1) % 3
      val lx = GamePhysics.LANES[logLane]
      seg.obstacles.add(
        ObstacleData(
          kind = ObstacleKind.LOG,
          lane = logLane,
          minX = lx - 1.22f,
          maxX = lx + 1.22f,
          minY = 0f,
          maxY = 1.05f,
          minZ = zPos - 0.55f,
          maxZ = zPos + 0.55f,
        )
      )
      for (k in -1..1) {
        addCoin(seg, lx, if (k == 0) 2.3f else 1.55f, zPos + k * 1.45f)
      }
      // Also a ground coin trail in safe lane
      addCoin(seg, GamePhysics.LANES[safeLane], 0.9f, zPos + 5.0f)
    } else if (pattern < 0.66f) {
      // Stone Archway (slide under!) + low coins underneath
      val archLane = (safeLane + 2) % 3
      val ax = GamePhysics.LANES[archLane]
      seg.obstacles.add(
        ObstacleData(
          kind = ObstacleKind.ARCH,
          lane = archLane,
          minX = ax - 1.32f,
          maxX = ax + 1.32f,
          minY = 1.18f,
          maxY = 3.15f,
          minZ = zPos - 0.52f,
          maxZ = zPos + 0.52f,
        )
      )
      for (k in -1..1) {
        addCoin(seg, ax, 0.55f, zPos + k * 1.45f)
      }
    } else {
      // One or two Crumbling Totem Pillars, leaving safeLane open
      for (l in 0..2) {
        if (l == safeLane) continue
        if (l != (safeLane + 1) % 3 && rng() < 0.45f) continue
        val px = GamePhysics.LANES[l]
        seg.obstacles.add(
          ObstacleData(
            kind = ObstacleKind.PILLAR,
            lane = l,
            minX = px - 0.92f,
            maxX = px + 0.92f,
            minY = 0f,
            maxY = 3.4f,
            minZ = zPos - 0.72f,
            maxZ = zPos + 0.72f,
          )
        )
      }
      // Coin line down safe lane
      val sx = GamePhysics.LANES[safeLane]
      for (k in -1..1) {
        addCoin(seg, sx, 0.95f, zPos + k * 1.6f)
      }
    }

    // Occasional Relic spawn on risky/blocked-side lane
    if (rng() < 0.24f) {
      val riskyLane = (safeLane + 1) % 3
      val rx = GamePhysics.LANES[riskyLane]
      val rKind =
        when ((rng() * 3f).toInt()) {
          0 -> RelicKind.MAGNET
          1 -> RelicKind.SHIELD
          else -> RelicKind.DASH
        }
      val rz = zPos + 5.8f
      val wx = seg.originX + forwardX(seg.dir) * rz + rightX(seg.dir) * rx
      val wz = seg.originZ + forwardZ(seg.dir) * rz + rightZ(seg.dir) * rx
      seg.relics.add(RelicData(rKind, rx, rz, wx, wz))
    }

    return seg
  }

  private fun addCoin(seg: TrackSegment3D, lx: Float, ly: Float, lz: Float) {
    val wx = seg.originX + forwardX(seg.dir) * lz + rightX(seg.dir) * lx
    val wz = seg.originZ + forwardZ(seg.dir) * lz + rightZ(seg.dir) * lx
    seg.coins.add(CoinData(lx, ly, lz, wx, ly, wz))
  }

  fun updateAtmosphereAndPickups(
    dt: Float,
    playerX: Float,
    playerY: Float,
    playerZ: Float,
    distMeters: Int,
    magnetActive: Boolean,
    dashActive: Boolean,
    onCoinCollected: (Float, Float, Float) -> Unit,
    onRelicCollected: (RelicKind, Float, Float, Float) -> Unit,
  ) {
    val targetBiome = getBiomeAtDistance(distMeters)
    val tSun: FloatArray
    val tSky: FloatArray
    val tGround: FloatArray
    val tFog: FloatArray
    val tFogDens: Float
    when (targetBiome) {
      BiomeId.COURTYARD -> {
        tSun = floatArrayOf(1.0f, 0.87f, 0.63f)
        tSky = floatArrayOf(0.24f, 0.42f, 0.48f)
        tGround = floatArrayOf(0.36f, 0.48f, 0.35f)
        tFog = floatArrayOf(0.90f, 0.80f, 0.70f)
        tFogDens = 0.0145f
      }
      BiomeId.AQUEDUCT -> {
        tSun = floatArrayOf(0.84f, 0.96f, 0.94f)
        tSky = floatArrayOf(0.22f, 0.49f, 0.50f)
        tGround = floatArrayOf(0.20f, 0.44f, 0.45f)
        tFog = floatArrayOf(0.56f, 0.75f, 0.74f)
        tFogDens = 0.0165f
      }
      BiomeId.VAULT -> {
        tSun = floatArrayOf(1.0f, 0.65f, 0.34f)
        tSky = floatArrayOf(0.16f, 0.22f, 0.26f)
        tGround = floatArrayOf(0.24f, 0.16f, 0.14f)
        tFog = floatArrayOf(0.12f, 0.18f, 0.22f)
        tFogDens = 0.0195f
      }
    }
    val blend = (dt * 1.4f).coerceIn(0f, 1f)
    for (i in 0..2) {
      sunColor[i] += (tSun[i] - sunColor[i]) * blend
      hemiSky[i] += (tSky[i] - hemiSky[i]) * blend
      hemiGround[i] += (tGround[i] - hemiGround[i]) * blend
      fogColor[i] += (tFog[i] - fogColor[i]) * blend
    }
    fogDensity += (tFogDens - fogDensity) * blend

    val pickupRadiusSq = if (dashActive) 13.5f else 2.25f
    val magnetRadiusSq = 210f
    for (seg in activeSegments) {
      for (coin in seg.coins) {
        if (coin.collected) continue
        val dx = playerX - coin.worldX
        val dy = (playerY + 0.95f) - coin.worldY
        val dz = playerZ - coin.worldZ
        val d2 = dx * dx + dy * dy + dz * dz

        if (magnetActive && d2 < magnetRadiusSq) {
          coin.magnetized = true
        }
        if (coin.magnetized) {
          val pull = (dt * 16f).coerceIn(0f, 1f)
          coin.worldX += dx * pull
          coin.worldY += dy * pull
          coin.worldZ += dz * pull
        }
        val ndx = playerX - coin.worldX
        val ndy = (playerY + 0.95f) - coin.worldY
        val ndz = playerZ - coin.worldZ
        if (ndx * ndx + ndy * ndy + ndz * ndz < pickupRadiusSq) {
          coin.collected = true
          onCoinCollected(coin.worldX, coin.worldY, coin.worldZ)
        }
      }

      for (relic in seg.relics) {
        if (relic.collected) continue
        val dx = playerX - relic.worldX
        val dy = (playerY + 0.95f) - 1.15f
        val dz = playerZ - relic.worldZ
        if (dx * dx + dy * dy + dz * dz < (if (dashActive) 9.5f else 2.6f)) {
          relic.collected = true
          onRelicCollected(relic.kind, relic.worldX, 1.15f, relic.worldZ)
        }
      }
    }
  }

  fun render(
    shader: GlShaderProgram,
    textures: ProceduralTextures,
    playerX: Float,
    playerZ: Float,
    timeSec: Float,
    distMeters: Int,
  ) {
    // Flooded Aqueduct water plane below causeway (renders smoothly when near/in aqueducts)
    if (distMeters >= 380 && distMeters <= 1120) {
      Matrix.setIdentityM(shader.modelMatrix, 0)
      Matrix.translateM(shader.modelMatrix, 0, playerX, -1.6f, playerZ)
      Matrix.scaleM(shader.modelMatrix, 0, 140f, 0.1f, 140f)
      shader.drawMesh(
        shader.boxMesh,
        textures.whiteTex,
        0.16f,
        0.46f,
        0.48f,
        emissiveR = 0.04f,
        emissiveG = 0.12f,
        emissiveB = 0.14f,
      )
    }

    for (seg in activeSegments) {
      val yawDeg = Math.toDegrees(dirToYaw(seg.dir).toDouble()).toFloat()
      val fwdX = forwardX(seg.dir)
      val fwdZ = forwardZ(seg.dir)

      val floorTint =
        when (seg.biome) {
          BiomeId.COURTYARD -> floatArrayOf(0.95f, 0.88f, 0.78f)
          BiomeId.AQUEDUCT -> floatArrayOf(0.76f, 0.88f, 0.86f)
          BiomeId.VAULT -> floatArrayOf(0.42f, 0.48f, 0.54f)
        }

      // Roadway surface (split if broken aqueduct gap)
      if (seg.gapStart != null && seg.gapEnd != null) {
        drawFloorSpan(shader, textures.sandstoneFloorTex, seg, yawDeg, 0f, seg.gapStart, floorTint)
        drawFloorSpan(shader, textures.sandstoneFloorTex, seg, yawDeg, seg.gapEnd, seg.length, floorTint)
      } else {
        drawFloorSpan(shader, textures.sandstoneFloorTex, seg, yawDeg, 0f, seg.length, floorTint)
      }

      // Low carved stone curb rims on both sides
      drawCurb(shader, textures.carvedPillarTex, seg, yawDeg, -4.85f)
      drawCurb(shader, textures.carvedPillarTex, seg, yawDeg, 4.85f)

      // Junction directional chevron glow
      if (seg.type != SegmentType.STRAIGHT && seg.type != SegmentType.GAP) {
        val pulse = 0.45f + 0.35f * sin(timeSec * 6f)
        val cx = seg.originX + fwdX * (seg.length - 4.7f)
        val cz = seg.originZ + fwdZ * (seg.length - 4.7f)
        Matrix.setIdentityM(shader.modelMatrix, 0)
        Matrix.translateM(shader.modelMatrix, 0, cx, 0.05f, cz)
        Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(shader.modelMatrix, 0, 5.2f, 0.04f, 1.8f)
        shader.drawMesh(
          shader.boxMesh,
          textures.whiteTex,
          1.0f,
          0.82f,
          0.40f,
          emissiveR = 0.5f * pulse,
          emissiveG = 0.38f * pulse,
          emissiveB = 0.1f * pulse,
        )
      }

      // Flanking Ruin Columns, Vines, and Vault Braziers
      for (prop in seg.props) {
        val wx = seg.originX + fwdX * prop.localZ + rightX(seg.dir) * prop.localX
        val wz = seg.originZ + fwdZ * prop.localZ + rightZ(seg.dir) * prop.localX
        Matrix.setIdentityM(shader.modelMatrix, 0)
        Matrix.translateM(shader.modelMatrix, 0, wx, prop.height * 0.5f - 1.2f, wz)
        Matrix.scaleM(shader.modelMatrix, 0, 1.35f, prop.height, 1.35f)
        shader.drawMesh(
          shader.boxMesh,
          textures.carvedPillarTex,
          floorTint[0] * 0.9f,
          floorTint[1] * 0.9f,
          floorTint[2] * 0.9f,
          repeatX = 1f,
          repeatY = 2f,
        )

        if (prop.hasVine) {
          Matrix.setIdentityM(shader.modelMatrix, 0)
          Matrix.translateM(shader.modelMatrix, 0, wx, prop.height - 1.0f, wz)
          Matrix.scaleM(shader.modelMatrix, 0, 1.65f, 0.9f, 1.65f)
          shader.drawMesh(shader.boxMesh, textures.whiteTex, 0.34f, 0.51f, 0.34f)
        }

        if (prop.hasBrazier) {
          val flicker = 0.75f + 0.25f * sin(timeSec * 15f + wx)
          Matrix.setIdentityM(shader.modelMatrix, 0)
          Matrix.translateM(shader.modelMatrix, 0, wx * 1f, prop.height - 0.9f, wz * 1f)
          Matrix.scaleM(shader.modelMatrix, 0, 0.65f * flicker, 0.85f * flicker, 0.65f * flicker)
          shader.drawMesh(
            shader.octaMesh,
            textures.whiteTex,
            1.0f,
            0.62f,
            0.20f,
            emissiveR = 0.95f * flicker,
            emissiveG = 0.45f * flicker,
            emissiveB = 0.12f,
          )
        }
      }

      // Obstacles
      for (obs in seg.obstacles) {
        val cx = (obs.minX + obs.maxX) * 0.5f
        val cz = (obs.minZ + obs.maxZ) * 0.5f
        val wx = seg.originX + fwdX * cz + rightX(seg.dir) * cx
        val wz = seg.originZ + fwdZ * cz + rightZ(seg.dir) * cx

        when (obs.kind) {
          ObstacleKind.LOG -> {
            Matrix.setIdentityM(shader.modelMatrix, 0)
            Matrix.translateM(shader.modelMatrix, 0, wx, 0.48f, wz)
            Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.rotateM(shader.modelMatrix, 0, 90f, 0f, 0f, 1f)
            Matrix.scaleM(shader.modelMatrix, 0, 0.94f, 2.45f, 0.94f)
            shader.drawMesh(shader.cylinderMesh, textures.timberLogTex, 1f, 1f, 1f)
          }
          ObstacleKind.ARCH -> {
            // Left & right arch jambs + high lintel beam (slide under!)
            for (side in floatArrayOf(-1.12f, 1.12f)) {
              val px = seg.originX + fwdX * cz + rightX(seg.dir) * (cx + side)
              val pz = seg.originZ + fwdZ * cz + rightZ(seg.dir) * (cx + side)
              Matrix.setIdentityM(shader.modelMatrix, 0)
              Matrix.translateM(shader.modelMatrix, 0, px, 1.55f, pz)
              Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
              Matrix.scaleM(shader.modelMatrix, 0, 0.42f, 3.1f, 0.55f)
              shader.drawMesh(shader.boxMesh, textures.carvedPillarTex, 0.92f, 0.85f, 0.78f)
            }
            Matrix.setIdentityM(shader.modelMatrix, 0)
            Matrix.translateM(shader.modelMatrix, 0, wx, 2.15f, wz)
            Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(shader.modelMatrix, 0, 2.75f, 1.85f, 0.92f)
            shader.drawMesh(shader.boxMesh, textures.carvedPillarTex, 0.90f, 0.82f, 0.72f)
          }
          ObstacleKind.PILLAR -> {
            Matrix.setIdentityM(shader.modelMatrix, 0)
            Matrix.translateM(shader.modelMatrix, 0, wx, 1.72f, wz)
            Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(shader.modelMatrix, 0, 1.72f, 3.45f, 1.38f)
            shader.drawMesh(shader.boxMesh, textures.carvedPillarTex, 0.95f, 0.90f, 0.84f, repeatY = 1.6f)
          }
        }
      }

      // ₦10 Gold Coins
      for (coin in seg.coins) {
        if (coin.collected) continue
        val bob = sin(timeSec * 4.5f + coin.localZ) * 0.10f
        Matrix.setIdentityM(shader.modelMatrix, 0)
        Matrix.translateM(shader.modelMatrix, 0, coin.worldX, coin.worldY + bob, coin.worldZ)
        Matrix.rotateM(shader.modelMatrix, 0, timeSec * 170f + coin.localZ * 20f, 0f, 1f, 0f)
        Matrix.rotateM(shader.modelMatrix, 0, 90f, 1f, 0f, 0f)
        Matrix.scaleM(shader.modelMatrix, 0, 0.76f, 0.14f, 0.76f)
        shader.drawMesh(
          shader.coinMesh,
          textures.nairaCoinTex,
          1.0f,
          0.95f,
          0.78f,
          emissiveR = 0.30f,
          emissiveG = 0.22f,
          emissiveB = 0.04f,
        )
      }

      // Relic Power-Ups
      for (relic in seg.relics) {
        if (relic.collected) continue
        val bob = sin(timeSec * 3.8f + relic.localZ) * 0.16f
        Matrix.setIdentityM(shader.modelMatrix, 0)
        Matrix.translateM(shader.modelMatrix, 0, relic.worldX, 1.18f + bob, relic.worldZ)
        Matrix.rotateM(shader.modelMatrix, 0, timeSec * 120f, 0f, 1f, 0f)
        Matrix.scaleM(shader.modelMatrix, 0, 0.85f, 1.1f, 0.85f)
        val (r, g, b) =
          when (relic.kind) {
            RelicKind.MAGNET -> Triple(0.32f, 0.72f, 0.53f)
            RelicKind.SHIELD -> Triple(1.0f, 0.82f, 0.40f)
            RelicKind.DASH -> Triple(1.0f, 0.58f, 0.12f)
          }
        shader.drawMesh(
          shader.octaMesh,
          textures.whiteTex,
          r,
          g,
          b,
          emissiveR = r * 0.55f,
          emissiveG = g * 0.55f,
          emissiveB = b * 0.55f,
        )
      }
    }
  }

  private fun drawFloorSpan(
    shader: GlShaderProgram,
    floorTex: Int,
    seg: TrackSegment3D,
    yawDeg: Float,
    zStart: Float,
    zEnd: Float,
    tint: FloatArray,
  ) {
    val spanLen = (zEnd - zStart).coerceAtLeast(0.5f)
    val midZ = (zStart + zEnd) * 0.5f
    val cx = seg.originX + forwardX(seg.dir) * midZ
    val cz = seg.originZ + forwardZ(seg.dir) * midZ
    Matrix.setIdentityM(shader.modelMatrix, 0)
    Matrix.translateM(shader.modelMatrix, 0, cx, -0.35f, cz)
    Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
    Matrix.scaleM(shader.modelMatrix, 0, GamePhysics.CAUSEWAY_WIDTH, 0.70f, spanLen)
    shader.drawMesh(
      shader.boxMesh,
      floorTex,
      tint[0],
      tint[1],
      tint[2],
      repeatX = 2.0f,
      repeatY = (spanLen / 4.5f).coerceAtLeast(1f),
    )
  }

  private fun drawCurb(
    shader: GlShaderProgram,
    tex: Int,
    seg: TrackSegment3D,
    yawDeg: Float,
    offsetX: Float,
  ) {
    val midZ = seg.length * 0.5f
    val cx = seg.originX + forwardX(seg.dir) * midZ + rightX(seg.dir) * offsetX
    val cz = seg.originZ + forwardZ(seg.dir) * midZ + rightZ(seg.dir) * offsetX
    Matrix.setIdentityM(shader.modelMatrix, 0)
    Matrix.translateM(shader.modelMatrix, 0, cx, 0.14f, cz)
    Matrix.rotateM(shader.modelMatrix, 0, yawDeg, 0f, 1f, 0f)
    Matrix.scaleM(shader.modelMatrix, 0, 0.52f, 0.36f, seg.length)
    shader.drawMesh(shader.boxMesh, tex, 0.78f, 0.72f, 0.65f, repeatY = 3f)
  }
}
