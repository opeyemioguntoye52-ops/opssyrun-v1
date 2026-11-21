package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.CharacterId
import com.example.game.GameCatalog
import com.example.game.PlayerProgress
import com.example.game.TrailId
import com.example.game.UpgradeKey
import com.example.ui.theme.BrightGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.FogMist
import com.example.ui.theme.HammeredGold
import com.example.ui.theme.JadeRelic
import com.example.ui.theme.MossGreen
import com.example.ui.theme.Sandstone
import com.example.ui.theme.ShadowSlate
import com.example.ui.theme.SunlitCream
import java.text.NumberFormat
import java.util.Locale

private enum class ArmoryTab(val label: String) {
  CHARACTERS("Characters"),
  RELICS("Relic Upgrades"),
  TRAILS("Trails"),
}

fun formatNaira(amount: Int): String {
  val formatted = NumberFormat.getIntegerInstance(Locale.US).format(amount)
  return "₦$formatted"
}

@Composable
fun ArmorySheet(
  progress: PlayerProgress,
  onClose: () -> Unit,
  onBuyOrEquipCharacter: (CharacterId) -> Unit,
  onBuyOrEquipTrail: (TrailId) -> Unit,
  onBuyUpgrade: (UpgradeKey) -> Unit,
) {
  var selectedTab by remember { mutableStateOf(ArmoryTab.CHARACTERS) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(DeepSlate.copy(alpha = 0.80f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClose,
        )
        .safeDrawingPadding()
        .padding(14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier =
        Modifier.widthIn(max = 560.dp)
          .fillMaxWidth()
          .fillMaxHeight(0.88f)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          )
          .testTag("armory_sheet"),
      shape = RoundedCornerShape(20.dp),
      color = ShadowSlate.copy(alpha = 0.96f),
      border = BorderStroke(1.dp, Sandstone.copy(alpha = 0.42f)),
      tonalElevation = 8.dp,
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Carved Stone Header
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "TEMPLE ARMORY",
              style = MaterialTheme.typography.titleLarge,
              color = SunlitCream,
            )
            Text(
              text = "Spend collected Naira on warriors, relics & trails",
              style = MaterialTheme.typography.bodyMedium,
              color = FogMist.copy(alpha = 0.78f),
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              shape = CircleShape,
              color = DeepSlate.copy(alpha = 0.85f),
              border = BorderStroke(1.dp, BrightGold.copy(alpha = 0.5f)),
            ) {
              Text(
                text = formatNaira(progress.naira),
                style = MaterialTheme.typography.labelLarge,
                color = BrightGold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
              )
            }

            IconButton(
              onClick = onClose,
              modifier =
                Modifier.size(48.dp)
                  .border(1.dp, Sandstone.copy(alpha = 0.35f), CircleShape)
                  .testTag("armory_close_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Armory",
                tint = SunlitCream,
              )
            }
          }
        }

        // Tabs
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .background(DeepSlate.copy(alpha = 0.75f))
              .padding(horizontal = 10.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ArmoryTab.entries.forEach { tab ->
            val active = selectedTab == tab
            Surface(
              modifier =
                Modifier.weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { selectedTab = tab }
                  .testTag("armory_tab_${tab.name.lowercase()}"),
              shape = RoundedCornerShape(12.dp),
              color = if (active) Sandstone.copy(alpha = 0.25f) else Color.Transparent,
              border = if (active) BorderStroke(1.dp, BrightGold.copy(alpha = 0.5f)) else null,
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                  text = tab.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (active) BrightGold else FogMist.copy(alpha = 0.78f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        // Content List
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          when (selectedTab) {
            ArmoryTab.CHARACTERS -> {
              items(GameCatalog.CHARACTERS, key = { it.id.name }) { char ->
                val unlocked = char.id in progress.unlockedCharacters
                val equipped = progress.equippedCharacter == char.id
                val canAfford = progress.naira >= char.priceNaira

                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (equipped) Sandstone.copy(alpha = 0.20f)
                    else DeepSlate.copy(alpha = 0.65f),
                  border =
                    BorderStroke(
                      1.dp,
                      if (equipped) BrightGold.copy(alpha = 0.75f)
                      else Sandstone.copy(alpha = 0.25f),
                    ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(64.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .background(Color(char.badgeColorHex))
                          .border(1.dp, BrightGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      if (char.portraitResId != null) {
                        Image(
                          painter = painterResource(id = char.portraitResId),
                          contentDescription = char.name,
                          contentScale = ContentScale.Crop,
                          alignment = Alignment.TopCenter,
                          modifier = Modifier.fillMaxSize(),
                        )
                      } else {
                        Icon(
                          imageVector = Icons.Default.Person,
                          contentDescription = char.name,
                          tint = BrightGold,
                          modifier = Modifier.size(30.dp),
                        )
                      }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        Text(
                          text = char.name,
                          style = MaterialTheme.typography.titleMedium,
                          color = SunlitCream,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        if (char.portraitResId != null) {
                          Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BrightGold.copy(alpha = 0.20f),
                            border = BorderStroke(0.5.dp, BrightGold.copy(alpha = 0.4f)),
                          ) {
                            Text(
                              text = "HERO",
                              fontSize = 9.sp,
                              fontWeight = FontWeight.Bold,
                              color = BrightGold,
                              modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                          }
                        }
                      }
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = char.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FogMist.copy(alpha = 0.80f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }

                    ActionPillButton(
                      label =
                        when {
                          equipped -> "Equipped"
                          unlocked -> "Equip"
                          else -> formatNaira(char.priceNaira)
                        },
                      isEquipped = equipped,
                      isUnlocked = unlocked,
                      enabled = unlocked || canAfford,
                      testTag = "char_btn_${char.id.rawId}",
                      onClick = { onBuyOrEquipCharacter(char.id) },
                    )
                  }
                }
              }
            }

            ArmoryTab.RELICS -> {
              items(GameCatalog.UPGRADES, key = { it.key.name }) { upg ->
                val lv = progress.upgradeLevel(upg.key)
                val isMax = lv >= upg.maxLevel
                val cost = if (isMax) 0 else upg.costs[lv - 1]
                val canAfford = !isMax && progress.naira >= cost

                val (icon, tint) =
                  when (upg.key) {
                    UpgradeKey.MAGNET -> Icons.Default.Attractions to JadeRelic
                    UpgradeKey.SHIELD -> Icons.Default.Shield to BrightGold
                    UpgradeKey.DASH -> Icons.Default.Bolt to HammeredGold
                    UpgradeKey.HEADSTART -> Icons.Default.LocalFireDepartment to HammeredGold
                  }

                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = DeepSlate.copy(alpha = 0.65f),
                  border = BorderStroke(1.dp, Sandstone.copy(alpha = 0.25f)),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(46.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .background(ShadowSlate)
                          .border(1.dp, Sandstone.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(imageVector = icon, contentDescription = upg.name, tint = tint)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = upg.name,
                          style = MaterialTheme.typography.titleMedium,
                          color = SunlitCream,
                        )
                        Text(
                          text = "Lv $lv/${upg.maxLevel}",
                          style = MaterialTheme.typography.labelSmall,
                          color = BrightGold,
                        )
                      }
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = upg.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FogMist.copy(alpha = 0.78f),
                      )
                      Spacer(modifier = Modifier.height(7.dp))
                      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 0 until upg.maxLevel) {
                          Box(
                            modifier =
                              Modifier.weight(1f)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(
                                  if (i < lv) BrightGold else FogMist.copy(alpha = 0.20f)
                                )
                          )
                        }
                      }
                    }

                    ActionPillButton(
                      label = if (isMax) "Maxed" else formatNaira(cost),
                      isEquipped = isMax,
                      isUnlocked = false,
                      enabled = canAfford,
                      testTag = "upgrade_btn_${upg.key.rawKey}",
                      onClick = { onBuyUpgrade(upg.key) },
                    )
                  }
                }
              }
            }

            ArmoryTab.TRAILS -> {
              items(GameCatalog.TRAILS, key = { it.id.name }) { trail ->
                val unlocked = trail.id in progress.unlockedTrails
                val equipped = progress.equippedTrail == trail.id
                val canAfford = progress.naira >= trail.priceNaira
                val swatch = Color(trail.swatchColorHex)

                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color =
                    if (equipped) Sandstone.copy(alpha = 0.20f)
                    else DeepSlate.copy(alpha = 0.65f),
                  border =
                    BorderStroke(
                      1.dp,
                      if (equipped) BrightGold.copy(alpha = 0.75f)
                      else Sandstone.copy(alpha = 0.25f),
                    ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    Box(
                      modifier =
                        Modifier.size(46.dp)
                          .clip(RoundedCornerShape(12.dp))
                          .background(swatch.copy(alpha = 0.22f))
                          .border(1.dp, SunlitCream.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                      contentAlignment = Alignment.Center,
                    ) {
                      Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = trail.name,
                        tint = swatch,
                      )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = trail.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = SunlitCream,
                      )
                      Text(
                        text = trail.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FogMist.copy(alpha = 0.78f),
                      )
                    }

                    ActionPillButton(
                      label =
                        when {
                          equipped -> "Equipped"
                          unlocked -> "Equip"
                          else -> formatNaira(trail.priceNaira)
                        },
                      isEquipped = equipped,
                      isUnlocked = unlocked,
                      enabled = unlocked || canAfford,
                      testTag = "trail_btn_${trail.id.rawId}",
                      onClick = { onBuyOrEquipTrail(trail.id) },
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ActionPillButton(
  label: String,
  isEquipped: Boolean,
  isUnlocked: Boolean,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val containerColor =
    when {
      isEquipped -> MossGreen
      isUnlocked -> Sandstone.copy(alpha = 0.28f)
      enabled -> HammeredGold
      else -> DeepSlate
    }
  val contentColor =
    when {
      isEquipped || isUnlocked -> SunlitCream
      enabled -> ShadowSlate
      else -> FogMist.copy(alpha = 0.42f)
    }

  Button(
    onClick = onClick,
    enabled = isEquipped || isUnlocked || enabled,
    shape = RoundedCornerShape(12.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = DeepSlate,
        disabledContentColor = FogMist.copy(alpha = 0.40f),
      ),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    modifier = Modifier.height(48.dp).widthIn(min = 92.dp).testTag(testTag),
  ) {
    if (isEquipped) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
    )
  }
}
