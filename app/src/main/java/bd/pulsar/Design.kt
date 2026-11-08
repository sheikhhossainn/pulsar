package bd.pulsar

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

internal val Paper = Color(0xFFF7F8F5)
internal val Ink = Color(0xFF172D30)
internal val Quiet = Color(0xFF4D6564)
internal val Teal = Color(0xFF164E50)
internal val Leaf = Color(0xFFD9F5AB)
internal val Mist = Color(0xFFE7F0E8)
internal val Border = Color(0xFFD8E1DA)
private val paths =
    mapOf(
        "mic" to "M9 6a3 3 0 0 1 6 0v6a3 3 0 0 1 -6 0z M5 11v1a7 7 0 0 0 14 0v-1 M12 19v3 M9 22h6",
        "clock" to "M21 12a9 9 0 1 1 -18 0a9 9 0 0 1 18 0z M12 7v5l3 2",
        "phone" to
            "M7 3l3 5 -3 2c1.5 3 4 5.5 7 7l2 -3 5 3c-1 4 -3 5 -6 4C9 19 5 15 3 9C2 6 3 4 7 3z",
        "apps" to "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z",
        "home" to "M3 10l9 -7 9 7 M5 9v12h5v-7h4v7h5V9",
        "person" to "M16 8a4 4 0 1 1 -8 0a4 4 0 0 1 8 0z M4 21a8 8 0 0 1 16 0",
        "help" to
            "M21 12a9 9 0 1 1 -18 0a9 9 0 0 1 18 0z M9.5 9a2.5 2.5 0 1 1 4 2c-1 .7 -1.5 1 -1.5 3 M12 17h.01",
        "plus" to "M12 5v14 M5 12h14",
        "next" to "M9 5l7 7 -7 7",
        "close" to "M6 6l12 12 M18 6L6 18",
        "check" to "M4 12l5 5L20 6",
        "wave" to "M2 11v2 M6 7v10 M10 3v18 M14 6v12 M18 9v6 M22 11v2",
    )

@Composable
internal fun Glyph(key: String, size: Dp = 24.dp, color: Color = Ink) {
    val image =
        remember(key) {
            ImageVector.Builder(key, 24.dp, 24.dp, 24f, 24f)
                .apply {
                    addPath(
                        pathData = PathParser().parsePathString(paths.getValue(key)).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
                .build()
        }
    Icon(image, null, Modifier.size(size), tint = color)
}

@Composable
internal fun TalkHero(start: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Teal)
            .clickable(role = Role.Button, onClick = start)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(78.dp).align(Alignment.TopCenter)) {
            val mid = this.size.height / 2
            for (i in 0..57) {
                val x = this.size.width * i / 57f
                val distance = abs(x - this.size.width / 2) / (this.size.width / 2)
                if (distance > 0.29f) {
                    val len =
                        (4 + abs(sin(i * .79)) * 19 + abs(cos(i * .31)) * 12).toFloat() *
                            (1 - distance * .7f) *
                            density
                    drawLine(
                        Color(0xFF50817A),
                        Offset(x, mid - len / 2),
                        Offset(x, mid + len / 2),
                        2.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(78.dp)
                    .border(1.dp, Color(0xFF74958A), CircleShape)
                    .padding(6.dp)
                    .background(Leaf, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Glyph("mic", 32.dp, Teal)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "চাপ দিয়ে কথা বলুন",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("যেমন: “ক্যালকুলেটর খোলো”", color = Color(0xFFCEE0D9), fontSize = 15.sp)
        }
    }
}

@Composable
internal fun ActionTile(
    icon: String,
    label: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(42.dp).background(tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun Heading(text: String) {
    Text(text, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = Ink)
}

@Composable
internal fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Teal),
    ) {
        Text(text, fontSize = 17.sp)
    }
}

@Composable
internal fun EmptyNote(icon: String, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(18.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Glyph(icon, 24.dp, Quiet)
        Column {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(body, color = Quiet, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
