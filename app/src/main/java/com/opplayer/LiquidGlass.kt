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
    // Shader per lente convessa ottica (Convex Magnification & Refraction Lens)
    private const val AGSL_CONVEX_LENS_SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float zoom;
        uniform float curvature;

        half4 main(float2 coord) {
            float2 center = size * 0.5;
            float radius = min(center.x, center.y);
            float2 norm = (coord - center) / radius;
            float distSq = dot(norm, norm);

            if (distSq < 1.0) {
                float dist = sqrt(distSq);
                // Rifrazione convessa e ingrandimento da lente ottica
                float factor = 1.0 - (1.0 - sqrt(1.0 - distSq * 0.82)) * curvature;
                float2 sampleNorm = norm * factor / zoom;
                float2 sampleCoord = center + sampleNorm * radius;

                // Sottile dispersione cromatica ai bordi della lente
                float chroma = dist * dist * 1.5;
                half4 colR = content.eval(sampleCoord + norm * chroma);
                half4 colG = content.eval(sampleCoord);
                half4 colB = content.eval(sampleCoord - norm * chroma);

                return half4(colR.r, colG.g, colB.b, colG.a);
            } else {
                return content.eval(coord);
            }
        }
    """

    // Shader per barra di cristallo cilindrica (Cylindrical Glass Lens per navbar)
    private const val AGSL_NAV_LENS_SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float refraction;

        half4 main(float2 coord) {
            float2 uv = coord / size;
            // Deformazione convessa verticale: la barra orizzontale di vetro agisce da lente cilindrica
            float dy = uv.y - 0.5;
            float offset = dy * abs(dy) * refraction * size.y;
            float2 sampleCoord = float2(coord.x, coord.y - offset);

            // Aberrazione cromatica sottile alle estremità del vetro
            float chroma = abs(dy) * 1.8;
            half4 colR = content.eval(sampleCoord + float2(0.0, chroma));
            half4 colG = content.eval(sampleCoord);
            half4 colB = content.eval(sampleCoord - float2(0.0, chroma));

            half4 col = half4(colR.r, colG.g, colB.b, colG.a);

            // Smusso speculare brillante sul bordo superiore e riflesso sottile inferiore
            float topGlare = smoothstep(0.06, 0.0, uv.y) * 0.25;
            float bottomGlare = smoothstep(0.94, 1.0, uv.y) * 0.12;

            col.rgb += half3(topGlare + bottomGlare);
            return col;
        }
    """

    // Shader ottico per goccia/capsula convessa d'acqua e vetro liquido
    private const val AGSL_DROPLET_CAPSULE_SRC = """
        uniform shader content;
        uniform float2 size;
        uniform float2 dropletCenter;
        uniform float2 dropletHalfSize;
        uniform float zoom;
        uniform float curvature;

        half4 main(float2 coord) {
            float2 d = coord - dropletCenter;
            
            // Distanza calcolata per profilo continuo a capsula / squircle
            float straightX = max(0.0, dropletHalfSize.x - dropletHalfSize.y);
            float2 p = d;
            p.x = max(0.0, abs(p.x) - straightX) * sign(p.x);
            float dist = length(p) / max(1.0, dropletHalfSize.y);

            if (dist < 1.0) {
                // Lente convessa ottica sferica: ingrandimento e rifrazione reale
                float z = sqrt(max(0.0, 1.0 - dist * dist));
                float factor = (1.0 - z * curvature) / zoom;
                float2 sampleOffset = d * (factor - 1.0);
                float2 sampleCoord = coord + sampleOffset;

                // Aberrazione cromatica prismatica ai bordi della lente
                float chroma = dist * dist * 2.8;
                float2 chromaDir = (dist > 0.001) ? normalize(p) * chroma : float2(0.0);

                half4 colR = content.eval(sampleCoord + chromaDir);
                half4 colG = content.eval(sampleCoord);
                half4 colB = content.eval(sampleCoord - chromaDir);
                half4 col = half4(colR.r, colG.g, colB.b, colG.a);

                // Menisco scuro di riflessione interna ai bordi (dist tra 0.72 e 0.95, effetto lente liquido)
                float meniscus = smoothstep(0.70, 0.92, dist) * (1.0 - smoothstep(0.95, 1.0, dist));
                col.rgb = mix(col.rgb, half3(0.08, 0.0, 0.02), meniscus * 0.40);

                // Tinta trasparente rosso rubino liquido (acqua colorata, limpida e brillante)
                half3 rubyTint = half3(0.95, 0.06, 0.12);
                col.rgb = mix(col.rgb, rubyTint, 0.26);

                // Glare speculare superiore a calotta convessa e caustica inferiore
                float normY = (coord.y - (dropletCenter.y - dropletHalfSize.y)) / max(1.0, 2.0 * dropletHalfSize.y);
                float topGlare = smoothstep(0.35, 0.05, normY) * (1.0 - smoothstep(0.85, 1.0, dist)) * 0.32;
                float bottomCaustic = smoothstep(0.65, 0.95, normY) * (1.0 - smoothstep(0.88, 0.98, dist)) * 0.18;

                col.rgb += half3(topGlare + bottomCaustic);
                return col;
            } else {
                return content.eval(coord);
            }
        }
    """

    fun createConvexLensShader(w: Float, h: Float, zoom: Float = 1.25f, curvature: Float = 0.40f): RuntimeShader {
        val shader = RuntimeShader(AGSL_CONVEX_LENS_SRC)
        shader.setFloatUniform("size", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        shader.setFloatUniform("zoom", zoom)
        shader.setFloatUniform("curvature", curvature)
        return shader
    }

    fun createNavLensShader(w: Float, h: Float, refraction: Float = 0.08f): RuntimeShader {
        val shader = RuntimeShader(AGSL_NAV_LENS_SRC)
        shader.setFloatUniform("size", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        shader.setFloatUniform("refraction", refraction)
        return shader
    }

    fun createDropletCapsuleShader(
        w: Float,
        h: Float,
        centerX: Float,
        centerY: Float,
        halfW: Float,
        halfH: Float,
        zoom: Float = 1.30f,
        curvature: Float = 0.40f
    ): RuntimeShader {
        val shader = RuntimeShader(AGSL_DROPLET_CAPSULE_SRC)
        shader.setFloatUniform("size", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        shader.setFloatUniform("dropletCenter", centerX, centerY)
        shader.setFloatUniform("dropletHalfSize", halfW.coerceAtLeast(1f), halfH.coerceAtLeast(1f))
        shader.setFloatUniform("zoom", zoom)
        shader.setFloatUniform("curvature", curvature)
        return shader
    }
}

/**
 * Liquid Glass specifico per la Floating Navigation Bar:
 * - Massima trasparenza cristallina (come nei video di riferimento)
 * - Nessun fondo nero o fumo opaco: il contenuto sottostante rimane nitidamente visibile
 * - Deformazione a lente cilindrica AGSL con riflessi di luce prismatici
 * - Bordo a smusso speculare ultra-pulito
 */
fun Modifier.liquidGlassNav(
    hazeState: HazeState,
    shape: Shape,
    tintColor: Color = Color(0x05FFFFFF),
    blurRadius: Dp = 3.dp,
    borderAlpha: Float = 0.60f
): Modifier {
    var modifier: Modifier = this
        .hazeChild(
            state = hazeState,
            shape = shape,
            style = HazeStyle(
                tint = tintColor,
                blurRadius = blurRadius,
                noiseFactor = 0f
            )
        )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        modifier = modifier.graphicsLayer {
            try {
                val shader = AgslGlassShaderHolder.createNavLensShader(size.width, size.height, 0.08f)
                val effect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Throwable) {
            }
        }
    }

    return modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f),
                    Color.White.copy(alpha = 0.06f)
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = borderAlpha),
                    Color.White.copy(alpha = borderAlpha * 0.18f),
                    Color.White.copy(alpha = borderAlpha * 0.40f)
                )
            ),
            shape = shape
        )
}

/**
 * Deformazione e Ingrandimento Ottico da Goccia / Capsula Liquida:
 * Applica via AGSL una lente convessa ottica su tutta l'area coperta dalla capsula,
 * ingrandendo e rifrangendo in tempo reale qualunque icona o elemento grafico vi scorra sotto.
 */
fun Modifier.opticalDropletDistortion(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    zoom: Float = 1.30f,
    curvature: Float = 0.40f
): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return this.graphicsLayer {
            try {
                val shader = AgslGlassShaderHolder.createDropletCapsuleShader(
                    size.width,
                    size.height,
                    centerX,
                    centerY,
                    halfWidth,
                    halfHeight,
                    zoom,
                    curvature
                )
                val effect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Throwable) {
            }
        }
    }
    return this
}

/**
 * Deformazione ottica convessa AGSL (Lens Distortion generica)
 */
fun Modifier.lensDistortion(
    zoom: Float = 1.25f,
    curvature: Float = 0.40f
): Modifier {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return this.graphicsLayer {
            try {
                val shader = AgslGlassShaderHolder.createConvexLensShader(size.width, size.height, zoom, curvature)
                val effect = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
                renderEffect = effect.asComposeRenderEffect()
            } catch (_: Throwable) {
            }
        }
    }
    return this
}

/**
 * Guscio Speculare della Capsula di Vetro Liquido Rosso (Liquid Red Glass Capsule):
 * Finitura trasparente rubino che fa da guscio convesso: riflesso speculare ad arco e bordo prismatico.
 */
fun Modifier.liquidRedGlassCapsule(
    shape: Shape
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0x35FF4D4F),
                Color(0x18E50914),
                Color(0x289E000B)
            )
        )
    )
    .border(
        width = 1.2.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color(0xFFFF7875).copy(alpha = 0.45f),
                Color(0xFFFF3B30).copy(alpha = 0.70f)
            )
        ),
        shape = shape
    )

fun Modifier.liquidGlass(
    hazeState: HazeState,
    shape: Shape,
    tintColor: Color = Color(0x20101018),
    blurRadius: Dp = 12.dp,
    borderAlpha: Float = 0.35f
): Modifier = this
    .hazeChild(
        state = hazeState,
        shape = shape,
        style = HazeStyle(
            tint = tintColor,
            blurRadius = blurRadius,
            noiseFactor = 0.02f
        )
    )
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.03f),
                Color.Black.copy(alpha = 0.16f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = borderAlpha),
                Color.White.copy(alpha = borderAlpha * 0.35f),
                Color.White.copy(alpha = borderAlpha * 0.10f),
                Color.White.copy(alpha = borderAlpha * 0.50f)
            )
        ),
        shape = shape
    )


