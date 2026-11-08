package com.santafe.launcher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Ubicación de respaldo para el clima si el GPS aún no tiene fix (València)
const val DEFAULT_LAT = 39.4699
const val DEFAULT_LON = -0.3763

val Deep = Color(0xFF05161B)
val Hueso = Color(0xFFF2EFE8)
val Teal = Color(0xFF3DE0C5)
val Amber = Color(0xFFFFC857)
val Muted = Color(0xFF8FB0B3)
val Glass = Color(0x16FFFFFF)
val Line = Color(0x4D3DE0C5)

fun Modifier.glass(r: Dp = 24.dp): Modifier =
    this.clip(RoundedCornerShape(r)).background(Glass).border(1.dp, Line, RoundedCornerShape(r))

/** Estado del coche. Aquí se conectará el Canbus; por ahora se prueba tocando el coche. */
object CarState {
    var headlights by mutableStateOf(false)
    var brake by mutableStateOf(false)
    var reverse by mutableStateOf(false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        setContent { Home() }
    }
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    var wx by remember { mutableStateOf(NoWx) }
    LaunchedEffect(Unit) {
        while (true) {
            wx = withContext(Dispatchers.IO) { fetchWeather(ctx) }
            delay(600_000)
        }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B3138), Deep)))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBar()
        Hero(Modifier.weight(1f).heightIn(min = 160.dp))
        MusicCard()
        WeatherCard(wx)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DockItem("map", "Mapas", Color(0xFF5B9BFF)) { openApp(ctx, "com.google.android.apps.maps", "com.waze") }
            DockItem("yt", "YouTube", Color(0xFFFF4B3E)) { openApp(ctx, "com.google.android.youtube") }
            DockItem("spotify", "Spotify", Color(0xFF1ED760)) { openApp(ctx, "com.spotify.music") }
            DockItem("radio", "Radio", Amber) {
                openApp(ctx, "com.syu.radio", "com.nwd.radio", "com.microntek.radio", "com.android.fmradio")
            }
        }
    }
}

@Composable
fun TopBar() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val hour = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val dia = SimpleDateFormat("EEEE", Locale("es")).format(now).replaceFirstChar { it.uppercase() }
    val fecha = SimpleDateFormat("d 'de' MMMM", Locale("es")).format(now)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(hour, color = Hueso, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Column(horizontalAlignment = Alignment.End) {
            Text(dia, color = Hueso, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(fecha, color = Muted, fontSize = 16.sp)
        }
    }
}

class G(val x: Float, val y: Float, val r: Float, val k: Int) // k: 0 faro, 1 piloto/freno, 2 reversa

val TopGlows = listOf(G(0.277f, 0.744f, 0.12f, 0), G(0.041f, 0.577f, 0.07f, 0), G(0.25f, 0.879f, 0.05f, 0), G(0.977f, 0.292f, 0.07f, 1))
val FrontGlows = listOf(G(0.136f, 0.458f, 0.16f, 0), G(0.864f, 0.458f, 0.16f, 0), G(0.105f, 0.682f, 0.08f, 0), G(0.895f, 0.682f, 0.08f, 0))
val RearGlows = listOf(
    G(0.127f, 0.414f, 0.16f, 1), G(0.873f, 0.414f, 0.16f, 1), G(0.5f, 0.087f, 0.1f, 1),
    G(0.209f, 0.461f, 0.09f, 2), G(0.791f, 0.461f, 0.09f, 2), G(0.1f, 0.72f, 0.06f, 1), G(0.9f, 0.72f, 0.06f, 1)
)

@Composable
fun Hero(modifier: Modifier) {
    val head by animateFloatAsState(if (CarState.headlights) 1f else 0f, tween(450), label = "head")
    val tail by animateFloatAsState(if (CarState.brake) 1f else if (CarState.headlights) 0.55f else 0f, tween(300), label = "tail")
    val rev by animateFloatAsState(if (CarState.reverse) 1f else 0f, tween(300), label = "rev")
    var sel by remember { mutableStateOf(0) }
    val view = if (CarState.reverse || CarState.brake) 2 else sel
    val painter = painterResource(when (view) { 1 -> R.drawable.view_front; 2 -> R.drawable.view_rear; else -> R.drawable.view_top })
    val glows = when (view) { 1 -> FrontGlows; 2 -> RearGlows; else -> TopGlows }
    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
    Box(modifier.fillMaxWidth().glass(28.dp)) {
        Canvas(Modifier.fillMaxSize()) { drawStage() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = minOf(maxWidth * 0.94f, maxHeight * 0.82f * ratio)
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).width(w).aspectRatio(ratio)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { CarState.headlights = !CarState.headlights },
                            onLongPress = {
                                CarState.reverse = !CarState.reverse
                                CarState.brake = CarState.reverse
                            }
                        )
                    }
            ) {
                Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()) {
                    for (g in glows) {
                        val a = when (g.k) { 0 -> head; 1 -> tail; else -> rev }
                        if (a > 0.01f) {
                            val c = Offset(g.x * size.width, g.y * size.height)
                            val rad = g.r * size.width
                            val col = if (g.k == 1) Color(0xFFFF2A1A) else if (g.k == 2) Color.White else Color(0xFFFFE9A8)
                            drawCircle(
                                Brush.radialGradient(listOf(col.copy(alpha = 0.85f * a), Color.Transparent), center = c, radius = rad),
                                radius = rad, center = c, blendMode = BlendMode.Plus
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(18.dp)) {
            Text("HYUNDAI", color = Teal, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 4.sp)
            Text("Grand Santa Fe", color = Hueso, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("2016 · 7 plazas", color = Muted, fontSize = 14.sp)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("3/4", "Frente", "Atrás").forEachIndexed { i, t ->
                Text(
                    t, color = if (view == i) Deep else Hueso, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (view == i) Teal else Glass)
                        .clickable {
                            sel = i
                            CarState.reverse = false
                            CarState.brake = false
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Text(
            "Modo prueba · toca = luces · mantén = reversa",
            color = Color(0x998FB0B3), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp)
        )
    }
}

/** Fondo del escenario: rayos de luz y suelo luminoso. */
fun DrawScope.drawStage() {
    val ray = Brush.verticalGradient(listOf(Color(0x403DE0C5), Color.Transparent))
    val r1 = Path().apply {
        moveTo(size.width * 0.18f, 0f); lineTo(size.width * 0.34f, 0f)
        lineTo(size.width * 0.52f, size.height * 0.8f); lineTo(size.width * 0.40f, size.height * 0.8f); close()
    }
    val r2 = Path().apply {
        moveTo(size.width * 0.82f, 0f); lineTo(size.width * 0.66f, 0f)
        lineTo(size.width * 0.48f, size.height * 0.8f); lineTo(size.width * 0.60f, size.height * 0.8f); close()
    }
    drawPath(r1, ray)
    drawPath(r2, ray)
    drawOval(
        brush = Brush.radialGradient(listOf(Color(0x883DE0C5), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.9f), radius = size.width * 0.4f),
        topLeft = Offset(size.width * 0.1f, size.height * 0.78f),
        size = Size(size.width * 0.8f, size.height * 0.22f)
    )
}

@Composable
fun MusicCard() {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf("Sin reproducción") }
    var artist by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf(false) }
    var ctrl by remember { mutableStateOf<MediaController?>(null) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val c = msm.getActiveSessions(ComponentName(ctx, NotifListener::class.java)).firstOrNull()
                ctrl = c
                denied = false
                title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Sin reproducción"
                artist = c?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
            } catch (e: SecurityException) {
                denied = true
                ctrl = null
            }
            delay(1500)
        }
    }
    Row(
        Modifier.fillMaxWidth().glass()
            .clickable(enabled = denied) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("MÚSICA", color = Teal, fontSize = 12.sp, letterSpacing = 3.sp)
            Text(if (denied) "Activa el acceso a la música" else title, color = Hueso, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (denied) "Toca aquí para dar permiso" else artist.ifEmpty { "Spotify, radio o Bluetooth" }, color = Muted, fontSize = 16.sp, maxLines = 1)
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Glass).clickable { ctrl?.transportControls?.skipToPrevious() }, contentAlignment = Alignment.Center) {
            Glyph("prev", Hueso, Modifier.size(28.dp))
        }
        Box(Modifier.padding(horizontal = 10.dp).size(64.dp).clip(CircleShape).background(Teal).clickable {
            if (playing) ctrl?.transportControls?.pause() else ctrl?.transportControls?.play()
        }, contentAlignment = Alignment.Center) {
            Glyph(if (playing) "pause" else "play", Deep, Modifier.size(36.dp))
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Glass).clickable { ctrl?.transportControls?.skipToNext() }, contentAlignment = Alignment.Center) {
            Glyph("next", Hueso, Modifier.size(28.dp))
        }
    }
}

@Composable
fun WeatherCard(wx: Wx) {
    Column(Modifier.fillMaxWidth().glass().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("VALÈNCIA", color = Teal, fontSize = 12.sp, letterSpacing = 3.sp)
            Text("${wx.temp}  ${wx.cond}", color = Hueso, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (d in wx.days) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.first, color = Muted, fontSize = 14.sp)
                    Text(d.second, color = Hueso, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(d.third, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun DockItem(kind: String, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Glass).border(1.dp, Line, CircleShape),
            contentAlignment = Alignment.Center
        ) { Glyph(kind, tint, Modifier.size(38.dp)) }
        Text(label, color = Muted, fontSize = 14.sp)
    }
}

/** Iconos dibujados a mano, sin depender de emojis ni de librerías. */
@Composable
fun Glyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun tri(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val p = Path().apply { moveTo(x1 * w, y1 * h); lineTo(x2 * w, y2 * h); lineTo(x3 * w, y3 * h); close() }
            drawPath(p, c)
        }
        when (kind) {
            "play" -> tri(color, 0.3f, 0.2f, 0.3f, 0.8f, 0.82f, 0.5f)
            "pause" -> {
                drawRect(color, Offset(0.26f * w, 0.2f * h), Size(0.17f * w, 0.6f * h))
                drawRect(color, Offset(0.57f * w, 0.2f * h), Size(0.17f * w, 0.6f * h))
            }
            "next" -> {
                tri(color, 0.18f, 0.25f, 0.18f, 0.75f, 0.7f, 0.5f)
                drawRect(color, Offset(0.76f * w, 0.25f * h), Size(0.09f * w, 0.5f * h))
            }
            "prev" -> {
                tri(color, 0.82f, 0.25f, 0.82f, 0.75f, 0.3f, 0.5f)
                drawRect(color, Offset(0.15f * w, 0.25f * h), Size(0.09f * w, 0.5f * h))
            }
            "map" -> {
                drawCircle(color, 0.24f * w, Offset(0.5f * w, 0.38f * h))
                tri(color, 0.29f, 0.5f, 0.71f, 0.5f, 0.5f, 0.92f)
                drawCircle(Deep, 0.09f * w, Offset(0.5f * w, 0.38f * h))
            }
            "yt" -> {
                drawRoundRect(color, Offset(0.06f * w, 0.24f * h), Size(0.88f * w, 0.52f * h), CornerRadius(0.15f * w, 0.15f * w))
                tri(Deep, 0.42f, 0.38f, 0.42f, 0.62f, 0.64f, 0.5f)
            }
            "spotify" -> {
                drawCircle(color, 0.46f * w, Offset(0.5f * w, 0.5f * h))
                for (r in listOf(0.34f, 0.25f, 0.16f)) {
                    drawArc(Deep, 205f, 130f, false, Offset((0.5f - r) * w, (0.74f - r) * h), Size(2f * r * w, 2f * r * h), style = Stroke(0.06f * w, cap = StrokeCap.Round))
                }
            }
            "radio" -> {
                drawCircle(color, 0.07f * w, Offset(0.5f * w, 0.66f * h))
                for (r in listOf(0.2f, 0.34f, 0.48f)) {
                    drawArc(color, 215f, 110f, false, Offset((0.5f - r) * w, (0.66f - r) * h), Size(2f * r * w, 2f * r * h), style = Stroke(0.055f * w, cap = StrokeCap.Round))
                }
            }
        }
    }
}

fun openApp(ctx: Context, vararg pkgs: String) {
    for (p in pkgs) {
        val i = ctx.packageManager.getLaunchIntentForPackage(p)
        if (i != null) {
            ctx.startActivity(i)
            return
        }
    }
    Toast.makeText(ctx, "App no instalada en esta unidad", Toast.LENGTH_SHORT).show()
}

data class Wx(val temp: String, val cond: String, val days: List<Triple<String, String, String>>)

val NoWx = Wx("--", "Sin conexión", emptyList())

fun fetchWeather(ctx: Context): Wx = try {
    var lat = DEFAULT_LAT
    var lon = DEFAULT_LON
    try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let {
            lat = it.latitude
            lon = it.longitude
        }
    } catch (e: SecurityException) {
    }
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=3&timezone=auto"
    )
    val j = JSONObject(url.readText())
    val cur = j.getJSONObject("current")
    val d = j.getJSONObject("daily")
    val times = d.getJSONArray("time")
    val mx = d.getJSONArray("temperature_2m_max")
    val mn = d.getJSONArray("temperature_2m_min")
    val wc = d.getJSONArray("weather_code")
    val fin = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val fout = SimpleDateFormat("EEE", Locale("es"))
    val days = (0 until times.length()).map { i ->
        val label = if (i == 0) "Hoy" else fout.format(fin.parse(times.getString(i))!!).trimEnd('.').replaceFirstChar { it.uppercase() }
        Triple(label, "${mx.getDouble(i).roundToInt()}° / ${mn.getDouble(i).roundToInt()}°", wmo(wc.getInt(i)))
    }
    Wx("${cur.getDouble("temperature_2m").roundToInt()}°", wmo(cur.getInt("weather_code")), days)
} catch (e: Exception) {
    NoWx
}

fun wmo(code: Int): String = when (code) {
    0 -> "Despejado"
    1, 2 -> "Parcial"
    3 -> "Nublado"
    in 45..48 -> "Niebla"
    in 51..67 -> "Lluvia"
    in 71..77 -> "Nieve"
    in 80..82 -> "Chubascos"
    in 95..99 -> "Tormenta"
    else -> ""
}
