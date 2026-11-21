package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.CharacterId
import com.example.game.PlayerProgress
import com.example.game.TrailId
import com.example.game.todaySeedDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
  name = "sunken_ruins_prefs"
)

class GamePreferencesRepository(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private object Keys {
    val PROGRESS_JSON = stringPreferencesKey("progress_json")
  }

  @Volatile
  var cachedProgress: PlayerProgress = PlayerProgress()
    private set

  init {
    runBlocking(Dispatchers.IO) {
      val prefs = appContext.dataStore.data.first()
      val raw = prefs[Keys.PROGRESS_JSON]
      if (!raw.isNullOrBlank()) {
        cachedProgress = decodeAndSanitize(raw)
      }
    }
  }

  fun saveProgress(progress: PlayerProgress) {
    cachedProgress = progress
    val serialized = encode(progress)
    scope.launch {
      appContext.dataStore.edit { prefs ->
        prefs[Keys.PROGRESS_JSON] = serialized
      }
    }
  }

  private fun decodeAndSanitize(jsonStr: String): PlayerProgress {
    return try {
      val obj = JSONObject(jsonStr)
      val today = todaySeedDate()
      val sameDay = obj.optString("dailySeedDate") == today

      val unlockedChars = mutableSetOf(CharacterId.EXPLORER)
      val charArr = obj.optJSONArray("unlockedCharacters")
      if (charArr != null) {
        for (i in 0 until charArr.length()) {
          unlockedChars.add(CharacterId.fromRaw(charArr.optString(i)))
        }
      }

      val unlockedTrails = mutableSetOf(TrailId.DUST)
      val trailArr = obj.optJSONArray("unlockedTrails")
      if (trailArr != null) {
        for (i in 0 until trailArr.length()) {
          unlockedTrails.add(TrailId.fromRaw(trailArr.optString(i)))
        }
      }

      val equippedChar = CharacterId.fromRaw(obj.optString("equippedCharacter"))
      val equippedTrail = TrailId.fromRaw(obj.optString("equippedTrail"))
      val upg = obj.optJSONObject("upgrades") ?: JSONObject()

      PlayerProgress(
        naira = obj.optInt("naira", 0).coerceAtLeast(0),
        highScore = obj.optInt("highScore", 0).coerceAtLeast(0),
        dailyHighScore = if (sameDay) obj.optInt("dailyHighScore", 0).coerceAtLeast(0) else 0,
        dailySeedDate = today,
        unlockedCharacters = unlockedChars,
        equippedCharacter = if (equippedChar in unlockedChars) equippedChar else CharacterId.EXPLORER,
        unlockedTrails = unlockedTrails,
        equippedTrail = if (equippedTrail in unlockedTrails) equippedTrail else TrailId.DUST,
        magnetLevel = upg.optInt("magnet", 1).coerceIn(1, 5),
        shieldLevel = upg.optInt("shield", 1).coerceIn(1, 5),
        dashLevel = upg.optInt("dash", 1).coerceIn(1, 5),
        headstartLevel = upg.optInt("headstart", 1).coerceIn(1, 5),
        isMuted = obj.optBoolean("isMuted", false),
      )
    } catch (_: Exception) {
      PlayerProgress()
    }
  }

  private fun encode(progress: PlayerProgress): String {
    val obj = JSONObject()
    obj.put("naira", progress.naira)
    obj.put("highScore", progress.highScore)
    obj.put("dailyHighScore", progress.dailyHighScore)
    obj.put("dailySeedDate", progress.dailySeedDate)
    obj.put("unlockedCharacters", JSONArray(progress.unlockedCharacters.map { it.rawId }))
    obj.put("equippedCharacter", progress.equippedCharacter.rawId)
    obj.put("unlockedTrails", JSONArray(progress.unlockedTrails.map { it.rawId }))
    obj.put("equippedTrail", progress.equippedTrail.rawId)
    val upg = JSONObject()
    upg.put("magnet", progress.magnetLevel)
    upg.put("shield", progress.shieldLevel)
    upg.put("dash", progress.dashLevel)
    upg.put("headstart", progress.headstartLevel)
    obj.put("upgrades", upg)
    obj.put("isMuted", progress.isMuted)
    return obj.toString()
  }
}
