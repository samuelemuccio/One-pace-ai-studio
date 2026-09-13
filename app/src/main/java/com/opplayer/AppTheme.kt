package com.opplayer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Design System "One Pace Premium" — iOS 27 Liquid Glass style.
 * Palette ridotta, tipografia a 6 livelli, motion differenziato, haptic universali.
 */
object AppColors {
    // Un solo accento per schermata — i colori sono usati SPARINGLY
    val Accent        = Color(0xFFFF453A)  // iOS system red — Home/Player
    val AccentDim     = Color(0xFFB0281F)
    val AccentSoft    = Color(0x1AFF453A)  // 10% tint per sfondi
    val AccentGlow    = Color(0x66FF453A)

    val Gold          = Color(0xFFFFD60A)  // Registro
    val GoldSoft      = Color(0x1AFFD60A)

    val Sky           = Color(0xFF0A84FF)  // Download
    val SkySoft       = Color(0x1A0A84FF)

    val Purple        = Color(0xFFBF5AF2)  // Statistiche
    val PurpleSoft    = Color(0x1ABF5AF2)

    val Success       = Color(0xFF30D158)
    val Warning       = Color(0xFFFF9F0A)

    // Superfici (Liquid Glass — iOS pure grays)
    val Bg0           = Color(0xFF000000)  // nero puro OLED
    val Bg1           = Color(0xFF0A0A0C)  // fondo schermata
    val Glass0        = Color(0x14FFFFFF)  // 8% bianco (overlay su video)
    val Glass1        = Color(0x1FFFFFFF)  // 12% bianco (card secondaria)
    val Glass2        = Color(0x2BFFFFFF)  // 17% bianco (card principale)
    val GlassBorder   = Color(0x33FFFFFF)  // 20% bianco bordo
    val Scrim         = Color(0xB3000000)  // 70% nero per overlay modali

    // Compatibilità con dialogs e impostazioni
    val Background    = Bg0
    val Surface1      = Color(0xFF0D0D0F)
    val Surface2      = Color(0xFF1C1C1E)
    val Surface3      = Color(0xFF2C2C2E)
    val Surface4      = Color(0xFF3A3A3C)

    // Testo
    val TextPrimary   = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)  // 70% bianco
    val TextTertiary  = Color(0x80FFFFFF)  // 50% bianco
    val TextMuted     = Color(0x4DFFFFFF)  // 30% bianco

    // Separatori
    val Separator     = Color(0x14FFFFFF)
}

object AppType {
    val Display   = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 38.sp)
    val Title     = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 28.sp)
    val Headline  = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 22.sp)
    val Body      = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val Subhead   = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
    val Caption   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    val Mono      = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
        fontFeatureSettings = "tnum")  // tabular numbers
    val BigNumber = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.0).sp,
        fontFeatureSettings = "tnum")
}

object AppMotion {
    val Tap    = spring<Float>(dampingRatio = 0.72f, stiffness = 600f)
    val Sheet  = spring<Float>(dampingRatio = 0.88f, stiffness = 280f)
    val Nav    = spring<Float>(dampingRatio = 0.78f, stiffness = 340f)
    val Hero   = tween<Float>(500, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f))
    val Quick  = tween<Float>(180, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
    val Fade   = tween<Float>(300, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f))
    val Scale  = spring<Float>(dampingRatio = 0.65f, stiffness = 500f)
}

object AppShape {
    val Chip     = RoundedCornerShape(10.dp)
    val Small    = RoundedCornerShape(14.dp)
    val Card     = RoundedCornerShape(20.dp)
    val CardBig  = RoundedCornerShape(26.dp)
    val Sheet    = RoundedCornerShape(28.dp)
    val Dock     = RoundedCornerShape(30.dp)
    val Hero     = RoundedCornerShape(32.dp)
    val Pill     = RoundedCornerShape(100.dp)
    val Button   = RoundedCornerShape(16.dp)
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

/** Tap con haptic feedback + scale spring. Uso: Modifier.hapticPress { onClick() } */
@Composable
fun Modifier.hapticPress(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = AppMotion.Tap,
        label = "hapticScale"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
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

/** Haptic "tick" per feedback brevi senza click */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember {
        { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
}

/**
 * GlassSurface — superficie Liquid Glass con bordo sfumato.
 * Uso semplice, senza Haze. Per il blur reale del backdrop usare hazeEffect().
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = AppShape.Card,
    intensity: GlassIntensity = GlassIntensity.Medium,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = when (intensity) {
        GlassIntensity.UltraThin -> AppColors.Glass0
        GlassIntensity.Thin      -> AppColors.Glass1
        GlassIntensity.Medium    -> AppColors.Glass2
        GlassIntensity.Thick     -> Color(0x3DFFFFFF)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .then(
                Modifier.borderTopGradient(shape)
            ),
        content = content
    )
}

enum class GlassIntensity { UltraThin, Thin, Medium, Thick }

/** Bordo superiore sfumato — rim light effetto Apple */
private fun Modifier.borderTopGradient(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(
        border(
            width = 0.5.dp,
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = 0.02f)
                )
            ),
            shape = shape
        )
    )

/** Palette narrativa per saga — colori ispirati al mondo di One Piece */
object SagaPalette {
    data class Colors(
        val gradientStart: Color,
        val gradientEnd: Color,
        val accent: Color
    )

    fun forSaga(sagaName: String): Colors = when {
        sagaName.contains("East Blue", true)      -> Colors(Color(0xFF1A2F5F), Color(0xFF0A0A0C), Color(0xFF3B82F6))
        sagaName.contains("Arabasta", true)       -> Colors(Color(0xFF7A4B1A), Color(0xFF0A0A0C), Color(0xFFEAB308))
        sagaName.contains("Sky Island", true)     -> Colors(Color(0xFF1E5C7A), Color(0xFF0A0A0C), Color(0xFF06B6D4))
        sagaName.contains("Water 7", true)        -> Colors(Color(0xFF3F3F7A), Color(0xFF0A0A0C), Color(0xFF8B5CF6))
        sagaName.contains("Thriller Bark", true)  -> Colors(Color(0xFF4A1A5C), Color(0xFF0A0A0C), Color(0xFFA855F7))
        sagaName.contains("Sabaody", true)        -> Colors(Color(0xFF5C1A1A), Color(0xFF0A0A0C), Color(0xFFEF4444))
        sagaName.contains("Impel Down", true)     -> Colors(Color(0xFF4A0F0F), Color(0xFF0A0A0C), Color(0xFFDC2626))
        sagaName.contains("Marineford", true)     -> Colors(Color(0xFF6B1A1A), Color(0xFF0A0A0C), Color(0xFFF87171))
        sagaName.contains("Post-War", true)       -> Colors(Color(0xFF3A1A3A), Color(0xFF0A0A0C), Color(0xFFEC4899))
        sagaName.contains("Fish-Man", true)       -> Colors(Color(0xFF0F3A5C), Color(0xFF0A0A0C), Color(0xFF0EA5E9))
        sagaName.contains("Punk Hazard", true)    -> Colors(Color(0xFF5C3A0F), Color(0xFF0A0A0C), Color(0xFFF97316))
        sagaName.contains("Dressrosa", true)      -> Colors(Color(0xFF6B0F3A), Color(0xFF0A0A0C), Color(0xFFF43F5E))
        sagaName.contains("Zou", true)            -> Colors(Color(0xFF0F4A2A), Color(0xFF0A0A0C), Color(0xFF22C55E))
        sagaName.contains("Whole Cake", true)     -> Colors(Color(0xFF5C1A4A), Color(0xFF0A0A0C), Color(0xFFEC4899))
        sagaName.contains("Levely", true)         -> Colors(Color(0xFF4A4A0F), Color(0xFF0A0A0C), Color(0xFFEAB308))
        sagaName.contains("Wano", true)           -> Colors(Color(0xFF5C0F0F), Color(0xFF0A0A0C), Color(0xFFDC2626))
        sagaName.contains("Egghead", true)        -> Colors(Color(0xFF2A0F5C), Color(0xFF0A0A0C), Color(0xFFA78BFA))
        else                                       -> Colors(Color(0xFF1A1A2E), Color(0xFF0A0A0C), Color(0xFFFF453A))
    }
}
