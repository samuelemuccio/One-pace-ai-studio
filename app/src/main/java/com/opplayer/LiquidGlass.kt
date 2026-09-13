package com.opplayer

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * CompositionLocal per condividere lo stato di cattura backdrop Haze
 * attraverso l'intera gerarchia dell'applicazione.
 */
val LocalHazeState = compositionLocalOf { HazeState() }

/**
 * Modificatore da applicare al contenitore / scrollview che fa da sfondo
 * da cui campionare i pixel per l'effetto Liquid Glass.
 */
fun Modifier.liquidGlassSource(hazeState: HazeState): Modifier = this.haze(hazeState)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AgslGlassShaderHolder {
    private const val AGSL_GLASS_SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float refraction;

        half4 main(float2 coord) {
            float2 uv = coord / size;
            float2 dir = uv - float2(0.5, 0.5);
            float dist = length(dir);
            // Distorsione ottica radiale convessa (lente Apple Liquid Glass)
            float2 offset = dir * (dist * dist) * refraction * 15.0;
            
            // Aberrazione cromatica prismatica ai bordi
            half4 colR = content.eval(coord + offset * 1.04);
            half4 colG = content.eval(coord + offset);
            half4 colB = content.eval(coord + offset * 0.96);
            
            // Specular highlight superiore
            float specularRim = smoothstep(0.35, 0.50, dist) * max(0.0, 1.0 - uv.y * 1.8) * 0.12;
            
            half4 res = half4(colR.r, colG.g, colB.b, colG.a);
            res.rgb += half3(specularRim);
            return res;
        }
    """

    fun createShader(w: Float, h: Float, refraction: Float = 0.08f): RuntimeShader {
        val shader = RuntimeShader(AGSL_GLASS_SRC)
        shader.setFloatUniform("size", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        shader.setFloatUniform("refraction", refraction)
        return shader
    }
}

/**
 * Ricostruzione avanzata dell'effetto Apple Liquid Glass:
 * 1. Cattura e Blur GPU del backdrop reale (tramite Haze)
 * 2. Shader AGSL RuntimeShader su Android 13+ con rifrazione convessa e aberrazione cromatica
 * 3. Riflesso speculare angolare con gradiente luce a 45°
 * 4. Bordo speculare con gradiente metallico/iridescente
 * 5. Tinta ultra-trasparente scura per preservare contrasto e leggibilità
 */
fun Modifier.liquidGlass(
    hazeState: HazeState,
    shape: Shape,
    tintColor: Color = Color(0x2212121A),
    blurRadius: Dp = 28.dp,
    borderAlpha: Float = 0.35f,
    enableAgslRefraction: Boolean = false
): Modifier {
    var modifier: Modifier = this.hazeChild(
        state = hazeState,
        shape = shape,
        style = HazeStyle(
            tint = tintColor,
            blurRadius = blurRadius,
            noiseFactor = 0.03f
        )
    )

    if (enableAgslRefraction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        modifier = modifier.graphicsLayer {
            try {
                val shader = AgslGlassShaderHolder.createShader(size.width, size.height, 0.06f)
                val effect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Throwable) {
                // Fallback silenzioso su blur standard se non supportato da GPU
            }
        }
    }

    return modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.02f),
                    Color.Black.copy(alpha = 0.18f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = borderAlpha * 0.35f),
                    Color.White.copy(alpha = borderAlpha * 0.08f),
                    Color.White.copy(alpha = borderAlpha * 0.45f)
                )
            ),
            shape = shape
        )
}

