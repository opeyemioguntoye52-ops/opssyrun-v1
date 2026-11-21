package com.example.game

import androidx.annotation.DrawableRes
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GamePalette {
  const val SANDSTONE = 0xFFD4A373.toInt()
  const val SANDSTONE_LIGHT = 0xFFE3BA8E.toInt()
  const val SANDSTONE_DARK = 0xFFB98554.toInt()
  const val MOSS_GREEN = 0xFF588157.toInt()
  const val MOSS_DEEP = 0xFF3B5E3D.toInt()
  const val TIMBER_DARK = 0xFF4A3525.toInt()
  const val TIMBER_LIGHT = 0xFF674B35.toInt()
  const val SHADOW_SLATE = 0xFF264653.toInt()
  const val VAULT_SLATE = 0xFF2F3E46.toInt()
  const val AQUEDUCT_TEAL = 0xFF3A7D7C.toInt()
  const val HAMMERED_GOLD = 0xFFF4A261.toInt()
  const val BRIGHT_GOLD = 0xFFFFD166.toInt()
  const val TERRACOTTA = 0xFFC85A3A.toInt()
  const val JADE_RELIC = 0xFF52B788.toInt()
  const val SUNLIGHT = 0xFFFFDDA1.toInt()
  const val FOG_MIST = 0xFFE6CCB2.toInt()
}

object GamePhysics {
  val LANES = floatArrayOf(-2.85f, 0f, 2.85f)
  const val LANE_WIDTH = 2.85f
  const val CAUSEWAY_WIDTH = 9.4f
  const val NAIRA_PER_COIN = 10
  const val CLOSE_CALL_BONUS_METERS = 25

  const val BASE_SPEED = 16.5f
  const val MAX_SPEED = 34.5f
  const val SPEED_RAMP_PER_SEC = 0.22f
  const val JUMP_VELOCITY = 16.6f
  const val GRAVITY = 44.0f
  const val FAST_FALL_VELOCITY = -29.0f
  const val SLIDE_DURATION = 0.68f
  const val INPUT_BUFFER_SECONDS = 0.17f
  const val LANE_SWITCH_DAMPING = 15.0f
}

enum class GamePhase {
  TITLE,
  PLAYING,
  PAUSED,
  GAMEOVER,
}

enum class BiomeId {
  COURTYARD,
  AQUEDUCT,
  VAULT,
}

enum class RelicKind {
  MAGNET,
  SHIELD,
  DASH,
}

enum class TurnHint {
  NONE,
  LEFT,
  RIGHT,
  BOTH,
}

enum class CharacterId(val rawId: String) {
  EXPLORER("explorer"),
  NOMAD("nomad"),
  SCOUT("scout"),
  RONIN("ronin"),
  PANDA("panda");

  companion object {
    fun fromRaw(raw: String?): CharacterId = entries.firstOrNull { it.rawId == raw } ?: EXPLORER
  }
}

enum class TrailId(val rawId: String) {
  DUST("dust"),
  GOLDEN("golden"),
  EMBER("ember");

  companion object {
    fun fromRaw(raw: String?): TrailId = entries.firstOrNull { it.rawId == raw } ?: DUST
  }
}

enum class UpgradeKey(val rawKey: String) {
  MAGNET("magnet"),
  SHIELD("shield"),
  DASH("dash"),
  HEADSTART("headstart");
}

data class CharacterDefinition(
  val id: CharacterId,
  val name: String,
  val subtitle: String,
  val priceNaira: Int,
  @DrawableRes val portraitResId: Int? = null,
  val badgeColorHex: Long,
)

data class TrailDefinition(
  val id: TrailId,
  val name: String,
  val subtitle: String,
  val priceNaira: Int,
  val swatchColorHex: Long,
)

data class UpgradeDefinition(
  val key: UpgradeKey,
  val name: String,
  val description: String,
  val maxLevel: Int,
  val costs: List<Int>,
)

object GameCatalog {
  val CHARACTERS =
    listOf(
      CharacterDefinition(
        id = CharacterId.EXPLORER,
        name = "Sunlit Explorer",
        subtitle = "Slate tunic & terracotta scarf",
        priceNaira = 0,
        badgeColorHex = 0xFF264653,
      ),
      CharacterDefinition(
        id = CharacterId.NOMAD,
        name = "Crimson Nomad",
        subtitle = "Woven desert cloak & crimson mantle",
        priceNaira = 2500,
        badgeColorHex = 0xFF9B2C2C,
      ),
      CharacterDefinition(
        id = CharacterId.SCOUT,
        name = "Emerald Scout",
        subtitle = "Moss-hooded jungle pathfinder",
        priceNaira = 4000,
        badgeColorHex = 0xFF2D6A4F,
      ),
      CharacterDefinition(
        id = CharacterId.RONIN,
        name = "Obsidian Ronin",
        subtitle = "Cyber-plated samurai with twin blades",
        priceNaira = 12000,
        portraitResId = R.drawable.char_ronin,
        badgeColorHex = 0xFF1B263B,
      ),
      CharacterDefinition(
        id = CharacterId.PANDA,
        name = "Street Panda",
        subtitle = "Fleece hoodie & hammered gold chain",
        priceNaira = 18000,
        portraitResId = R.drawable.char_panda,
        badgeColorHex = 0xFF4A3525,
      ),
    )

  val TRAILS =
    listOf(
      TrailDefinition(
        id = TrailId.DUST,
        name = "Sandstone Dust",
        subtitle = "Warm temple stone puffs",
        priceNaira = 0,
        swatchColorHex = 0xFFD4A373,
      ),
      TrailDefinition(
        id = TrailId.GOLDEN,
        name = "Sunlit Gold Trail",
        subtitle = "Luminous hammered gold sparkles",
        priceNaira = 3500,
        swatchColorHex = 0xFFFFD166,
      ),
      TrailDefinition(
        id = TrailId.EMBER,
        name = "Vault Ember Trail",
        subtitle = "Glowing subterranean sparks",
        priceNaira = 5000,
        swatchColorHex = 0xFFF4722B,
      ),
    )

  val UPGRADES =
    listOf(
      UpgradeDefinition(
        key = UpgradeKey.MAGNET,
        name = "Jade Magnet",
        description = "+2s coin pull duration per level",
        maxLevel = 5,
        costs = listOf(500, 1200, 2500, 4500),
      ),
      UpgradeDefinition(
        key = UpgradeKey.SHIELD,
        name = "Amber Aegis",
        description = "Longer shatter grace; Lv 3+ starts with Shield",
        maxLevel = 5,
        costs = listOf(600, 1400, 2800, 5000),
      ),
      UpgradeDefinition(
        key = UpgradeKey.DASH,
        name = "Sunburst Dash",
        description = "+0.8s duration & faster solar surge",
        maxLevel = 5,
        costs = listOf(600, 1500, 3000, 5200),
      ),
      UpgradeDefinition(
        key = UpgradeKey.HEADSTART,
        name = "Temple Head-Start",
        description = "Launch into runs with an initial Dash boost",
        maxLevel = 5,
        costs = listOf(800, 1800, 3500, 6000),
      ),
    )
}

data class ActiveRelicTimers(
  val magnetRemaining: Float = 0f,
  val magnetMax: Float = 8f,
  val hasShield: Boolean = false,
  val dashRemaining: Float = 0f,
  val dashMax: Float = 4.5f,
)

data class PlayerProgress(
  val naira: Int = 0,
  val highScore: Int = 0,
  val dailyHighScore: Int = 0,
  val dailySeedDate: String = todaySeedDate(),
  val unlockedCharacters: Set<CharacterId> = setOf(CharacterId.EXPLORER),
  val equippedCharacter: CharacterId = CharacterId.EXPLORER,
  val unlockedTrails: Set<TrailId> = setOf(TrailId.DUST),
  val equippedTrail: TrailId = TrailId.DUST,
  val magnetLevel: Int = 1,
  val shieldLevel: Int = 1,
  val dashLevel: Int = 1,
  val headstartLevel: Int = 1,
  val isMuted: Boolean = false,
) {
  fun upgradeLevel(key: UpgradeKey): Int =
    when (key) {
      UpgradeKey.MAGNET -> magnetLevel
      UpgradeKey.SHIELD -> shieldLevel
      UpgradeKey.DASH -> dashLevel
      UpgradeKey.HEADSTART -> headstartLevel
    }

  fun withUpgradeLevel(key: UpgradeKey, level: Int): PlayerProgress =
    when (key) {
      UpgradeKey.MAGNET -> copy(magnetLevel = level)
      UpgradeKey.SHIELD -> copy(shieldLevel = level)
      UpgradeKey.DASH -> copy(dashLevel = level)
      UpgradeKey.HEADSTART -> copy(headstartLevel = level)
    }
}

data class GameSnapshot(
  val phase: GamePhase = GamePhase.TITLE,
  val distance: Int = 0,
  val runNaira: Int = 0,
  val totalNaira: Int = 0,
  val highScore: Int = 0,
  val dailyHighScore: Int = 0,
  val isDailyMode: Boolean = false,
  val dailySeedDate: String = todaySeedDate(),
  val isNewRecord: Boolean = false,
  val isMuted: Boolean = false,
  val speedRatio: Float = 0f,
  val biome: BiomeId = BiomeId.COURTYARD,
  val turnHint: TurnHint = TurnHint.NONE,
  val chaserDanger: Float = 0f,
  val relics: ActiveRelicTimers = ActiveRelicTimers(),
  val closeCallCounter: Int = 0,
  val biomeAnnouncement: String? = null,
)

fun todaySeedDate(): String {
  val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
  return fmt.format(Date())
}

fun getMagnetDuration(level: Int): Float = 8f + (level - 1) * 2f

fun getDashDuration(level: Int): Float = 4.5f + (level - 1) * 0.8f

fun getDashSpeedMultiplier(level: Int): Float = 2.0f + (level - 1) * 0.1f

fun getHeadStartDuration(level: Int): Float {
  if (level <= 1) return 0f
  return 1.6f + (level - 2) * 1.1f
}

class SeededRng(seedStr: String) {
  private var state: Int

  init {
    var h = 0x811c9dc5.toInt()
    for (ch in seedStr) {
      h = h xor ch.code
      h *= 16777619
    }
    state = h
  }

  fun nextFloat(): Float {
    state += 0x6d2b79f5
    var t = (state xor (state ushr 15)) * (1 or state)
    t = t xor (t + (t xor (t ushr 7)) * (61 or t))
    val u = (t xor (t ushr 14)).toLong() and 0xffffffffL
    return u.toFloat() / 4294967296f
  }
}
