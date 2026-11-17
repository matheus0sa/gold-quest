package com.goldquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.data.Gem
import com.goldquest.data.GemBag
import com.goldquest.domain.GemReward

/** Cores da pedra: corpo, faces de cima (claras) e contorno/faces de baixo (escuras). */
private class GemPalette(val base: Color, val light: Color, val dark: Color)

private fun paletteOf(gem: Gem): GemPalette = when (gem) {
    Gem.DIAMANTE -> GemPalette(Color(0xFF9FDDF0), Color(0xFFEAFBFF), Color(0xFF3A8AA3))
    Gem.ESMERALDA -> GemPalette(Color(0xFF1FAE6C), Color(0xFF7FE2B2), Color(0xFF0A6038))
    Gem.RUBI -> GemPalette(Color(0xFFDC2F49), Color(0xFFFF8B9C), Color(0xFF840D20))
    Gem.SAFIRA -> GemPalette(Color(0xFF2D67DD), Color(0xFF8DB3FF), Color(0xFF123885))
    Gem.AMETISTA -> GemPalette(Color(0xFF9549D6), Color(0xFFD3A9F6), Color(0xFF4F1D7A))
    Gem.TOPAZIO -> GemPalette(Color(0xFFF0951A), Color(0xFFFFD28C), Color(0xFF955200))
}

/** Cor principal da joia, para textos e destaques. */
fun gemColor(gem: Gem): Color = paletteOf(gem).base

/** Pedra lapidada desenhada no app: coroa clara em cima, pavilhão facetado embaixo. */
@Composable
fun GemIcon(gem: Gem, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val pal = paletteOf(gem)
    Canvas(modifier.size(size).semantics { contentDescription = gem.label }) {
        val w = this.size.width
        val h = this.size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        fun poly(pts: List<Offset>) = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }

        val tl = p(0.27f, 0.12f)
        val tr = p(0.73f, 0.12f)
        val r = p(0.97f, 0.38f)
        val l = p(0.03f, 0.38f)
        val b = p(0.5f, 0.95f)
        val ml = p(0.36f, 0.38f)
        val mr = p(0.64f, 0.38f)

        drawPath(poly(listOf(tl, tr, r, b, l)), pal.base)
        // Coroa: mesa no meio, faces laterais em tons claros
        drawPath(poly(listOf(tl, tr, mr, ml)), pal.light)
        drawPath(poly(listOf(tl, ml, l)), pal.light.copy(alpha = 0.75f))
        drawPath(poly(listOf(tr, r, mr)), pal.light.copy(alpha = 0.55f))
        // Pavilhão: faces laterais mais escuras dão volume
        drawPath(poly(listOf(l, ml, b)), pal.dark.copy(alpha = 0.35f))
        drawPath(poly(listOf(mr, r, b)), pal.dark.copy(alpha = 0.55f))
        // Brilho
        drawLine(Color.White.copy(alpha = 0.85f), p(0.34f, 0.2f), p(0.46f, 0.2f), strokeWidth = w * 0.06f)
        drawPath(
            poly(listOf(tl, tr, r, b, l)),
            pal.dark,
            style = Stroke(width = w * 0.07f, join = StrokeJoin.Round),
        )
    }
}

/** Ícone + quantidade, ex.: [💎] +1. */
@Composable
fun GemAmount(
    gem: Gem,
    text: String,
    size: Dp = 16.dp,
    fontSize: TextUnit = 14.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        GemIcon(gem, size)
        Text(text, fontSize = fontSize, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Joia ganha por uma tarefa: [💎] +1 */
@Composable
fun GemRewardBadge(reward: GemReward, fontSize: TextUnit = 14.sp) =
    GemAmount(reward.gem, "+${reward.amount}", size = 16.dp, fontSize = fontSize, color = MaterialTheme.colorScheme.tertiary)

/** Custo de joias em linha: [💎]1 [💎]2 */
@Composable
fun GemCostRow(gems: GemBag, size: Dp = 16.dp, fontSize: TextUnit = 15.sp, missing: GemBag = emptyMap()) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (gem in Gem.entries) {
            val n = gems[gem] ?: continue
            GemAmount(
                gem, n.toString(), size = size, fontSize = fontSize,
                color = if (gem in missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Pílula com o saldo de uma joia, para a barra do topo. */
@Composable
fun GemBalancePill(gem: Gem, amount: Int) {
    Surface(shape = RoundedCornerShape(50), color = gemColor(gem).copy(alpha = 0.16f)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            GemIcon(gem, 16.dp)
            Text(amount.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

/** Linha "[ícone] Esmeralda   −  2  +", para escolher quantas joias um item pede. */
@Composable
fun GemStepperRow(gem: Gem, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GemIcon(gem, 20.dp)
        Text(gem.label, modifier = Modifier.padding(start = 8.dp).weight(1f), fontSize = 15.sp)
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 0) { Text("−", fontSize = 20.sp) }
        Text(
            value.toString(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) { Text("+", fontSize = 20.sp) }
    }
}
