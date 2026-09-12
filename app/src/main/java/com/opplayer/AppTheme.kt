package com.opplayer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design System "One Pace Premium"
 * Ispirato a iOS 17/18 — palette ridotta, tipografia a 6 livelli,
 * motion differenziato, haptics universali.
 */
object AppColors {
    // Accento principale (rosso iOS System)
    val Accent        = Color(0xFFFF453A)
    val AccentSoft    = Color(0x33FF453A)
    val AccentGlow    = Color(0x66FF453A)

    // Accenti secondari
    val Gold          = Color(0xFFFFD60A)
    val GoldSoft      = Color(0x33FFD60A)
    val Sky           = Color(0xFF0A84FF)
    val Success       = Color(0xFF30D158)
    val Warning       = Color(0xFFFF9F0A)
    val Purple        = Color(0xFFBF5AF2)

    // Superfici (iOS-style, scala di grigi puri)
    val Background    = Color(0xFF000000)
    val Surface1      = Color(0xFF0D0D0F)   // fondo card
    val Surface2      = Color(0xFF1C1C1E)   // card base
    val Surface3      = Color(0xFF2C2C2E)   // card elevata
    val Surface4      = Color(0xFF3A3A3C)   // card top

    // Testo
    val TextPrimary   = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)   // iOS system gray
    val TextTertiary  = Color(0xFF48484A)
    val Separator     = Color(0x1AFFFFFF)   // 10% bianco

    // Overlay / scrim
    val Scrim         = Color(0xCC000000)
    val GlassTint     = Color(0x1AFFFFFF)
}

object AppType {
    val Display   = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp
    )
    val Title     = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    )
    val Headline  = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    )
    val Body      = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )
    val Subhead   = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextSecondary
    )
    val Caption   = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TextTertiary,
        letterSpacing = 0.2.sp
    )
    val BigNumber = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp
    )
}

object AppMotion {
    // Tap leggero e scattante
    val Tap    = spring<Float>(dampingRatio = 0.72f, stiffness = 600f)
    // Sheet / pannelli
    val Sheet  = spring<Float>(dampingRatio = 0.85f, stiffness = 260f)
    // Transizioni hero
    val Hero   = tween<Float>(450, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
    // Dock di navigazione
    val Nav    = spring<Float>(dampingRatio = 0.75f, stiffness = 340f)
    // Scale tap default
    val Scale  = spring<Float>(dampingRatio = 0.65f, stiffness = 500f)
}

object AppShape {
    val Card     = RoundedCornerShape(22.dp)
    val CardBig  = RoundedCornerShape(28.dp)
    val Chip     = RoundedCornerShape(12.dp)
    val Button   = RoundedCornerShape(16.dp)
    val Dock     = RoundedCornerShape(32.dp)
    val Capsule  = RoundedCornerShape(28.dp)
}

/** Ombra direzionale iOS-style (offset Y, blur netto) */
fun Modifier.iosShadow(
    radius: Dp = 16.dp,
    alpha: Float = 0.35f
): Modifier = this.shadow(
    elevation = radius,
    shape = AppShape.Card,
    ambientColor = Color.Black.copy(alpha = alpha * 0.5f),
    spotColor = Color.Black.copy(alpha = alpha)
)

/** Tap con haptic feedback + scale spring */
fun Modifier.hapticPress(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = AppMotion.Scale,
        label = "hapticScale"
    )
    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        )
}

/** Haptic "leggero" per feedback brevi (senza click) */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember {
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}
