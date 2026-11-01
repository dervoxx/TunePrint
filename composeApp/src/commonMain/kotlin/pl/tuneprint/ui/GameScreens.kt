package pl.tuneprint.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

// All screens render public game state. Correct answers are supplied only for results.
data class UiPlayer(
    val id: String,
    val name: String,
    val score: Int = 0,
    val answered: Boolean = false,
    val ready: Boolean = false,
    val roundPoints: Int = 0,
    val outcome: UiOutcome = UiOutcome.NONE,
    val answerName: String? = null,
)

enum class UiOutcome { NONE, CORRECT, WRONG, TIMEOUT }

private object Ink {
    val Ground = Color(0xFF100C18)
    val Surface = Color(0xFF21192E)
    val Raised = Color(0xFF30243F)
    val Violet = Color(0xFF7739E8)
    val Highlight = Color(0xFFB58AFF)
    val Text = Color(0xFFF9F6FF)
    val Muted = Color(0xFFBBB1CA)
    val Green = Color(0xFF6EE7AE)
    val Red = Color(0xFFFF8E9D)
}

@Composable
fun TunePrintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Violet, onPrimary = Color.White,
            primaryContainer = Ink.Raised, onPrimaryContainer = Ink.Text,
            secondary = Ink.Highlight, background = Ink.Ground, onBackground = Ink.Text,
            surface = Ink.Surface, onSurface = Ink.Text,
            surfaceVariant = Ink.Raised, onSurfaceVariant = Ink.Muted,
            outline = Ink.Muted, error = Ink.Red,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
        ),
        content = content,
    )
}

@Composable
private fun Screen(
    compact: Boolean = false,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Ink.Ground, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val gutter = if (maxWidth > 600.dp) 32.dp else 24.dp
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = gutter, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
                    content = content,
                )
                if (footer != null) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = gutter)
                            .padding(top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = footer,
                    )
                }
            }
        }
    }
}

/** The parent phase transition also drives each panel's entrance and exit. */
val LocalScreenTransition = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
private fun Modifier.panelMotion(order: Int = 0): Modifier {
    val scope = LocalScreenTransition.current ?: return this
    return with(scope) {
        this@panelMotion.animateEnterExit(
            enter = fadeIn(tween(240, delayMillis = order * 35)) +
                slideInVertically(tween(260, delayMillis = order * 35)) { it.coerceAtMost(24) / 2 },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(160)) { -it.coerceAtMost(20) / 2 },
        )
    }
}

@Composable
private fun Title(text: String, order: Int = 0) {
    Text(
        text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().panelMotion(order).semantics { heading() },
    )
}

@Composable
private fun RoundNumber(round: Int, totalRounds: Int) {
    Text("Runda $round z $totalRounds", color = Ink.Muted, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().panelMotion())
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).panelMotion(3),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) { Text(text, textAlign = TextAlign.Center) }
}

@Composable
fun LobbyScreen(players: List<UiPlayer>, onStart: () -> Unit) {
    Screen(footer = { PrimaryAction("Zaczynamy", onStart) }) {
        Title("TunePrint")
        Text("3 rundy · 15 s", color = Ink.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().panelMotion(1))
        PlayerGrid(players) { player, index ->
            Surface(color = Ink.Surface, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().panelMotion(index + 2)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(player.name, player.id.hashCode(), 36)
                    Text(player.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun CountdownScreen(round: Int, totalRounds: Int, count: Int) {
    val fontScale = LocalDensity.current.fontScale
    Surface(color = Ink.Ground, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.align(Alignment.Center).widthIn(max = 632.dp).fillMaxWidth().fillMaxHeight()) {
                RoundNumber(round, totalRounds)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().panelMotion(1), contentAlignment = Alignment.Center) {
                    val numberSize = (maxHeight.value * .55f / fontScale).coerceIn(24f, 144f)
                    AnimatedContent(targetState = count,
                        transitionSpec = { (fadeIn(tween(140)) + scaleIn(tween(180), initialScale = .9f)) togetherWith fadeOut(tween(100)) },
                        label = "countdown") { number ->
                        Text(if (number > 0) "$number" else "Start",
                            fontSize = (if (number > 0) numberSize else numberSize * .65f).sp,
                            lineHeight = (numberSize * 1.1f).sp, fontWeight = FontWeight.Black, color = Ink.Text)
                    }
                }
            }
        }
    }
}

@Composable
fun RoundScreen(
    round: Int, totalRounds: Int, remainingMillis: Long, availablePoints: Int,
    players: List<UiPlayer>, selectedPlayerId: String?, onAnswer: (String) -> Unit,
    audioLevels: List<Float> = emptyList(),
    acceptingAnswers: Boolean = true,
) {
    val seconds = ceil(remainingMillis.coerceAtLeast(0) / 1000.0).toInt()
    Screen(compact = true) {
        RoundNumber(round, totalRounds)
        Title("Czyj to utwór?", 1)
        Row(Modifier.fillMaxWidth().panelMotion(1), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selectedPlayerId == null) "$availablePoints pkt" else "Odpowiedź zapisana",
                style = MaterialTheme.typography.titleLarge, color = Ink.Highlight, modifier = Modifier.weight(1f))
            Text("$seconds s", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"), color = if (seconds <= 5) Ink.Red else Ink.Text)
        }
        LinearProgressIndicator(progress = { (remainingMillis / 15000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).panelMotion(1),
            color = if (seconds <= 5) Ink.Red else Ink.Highlight, trackColor = Ink.Raised)
        AudioWaveform(audioLevels, Modifier.fillMaxWidth().height(64.dp).panelMotion(2))
        // Two equal tiles + 12dp gap fill the width; text can grow and wrap.
        PlayerGrid(players) { player, index ->
            val chosen = player.id == selectedPlayerId
            OutlinedButton(
                onClick = { onAnswer(player.id) }, enabled = selectedPlayerId == null && acceptingAnswers,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Ink.Surface, contentColor = Ink.Text,
                    disabledContainerColor = if (chosen) Ink.Violet else Ink.Surface,
                    disabledContentColor = if (chosen) Color.White else Ink.Muted,
                ),
                border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp,
                    if (chosen) Ink.Highlight else Ink.Muted.copy(alpha = .6f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp).panelMotion(index + 2).semantics { selected = chosen },
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(player.name, player.id.hashCode())
                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (chosen) Text("Wybrano", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Odpowiedzi ${players.count { it.answered }}/${players.size}", color = Ink.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().panelMotion(4))
        Row(Modifier.fillMaxWidth().panelMotion(4), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            players.forEach { player ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (player.answered) "✓" else "—", color = if (player.answered) Ink.Highlight else Ink.Muted)
                    Text(player.name, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ResultsScreen(
    round: Int, totalRounds: Int, trackTitle: String, trackArtist: String, ownerName: String,
    players: List<UiPlayer>, remainingSeconds: Int, isReady: Boolean, onReady: () -> Unit,
) {
    Screen(footer = {
        Text("Gotowi ${players.count { it.ready }}/${players.size} · ${remainingSeconds.coerceAtLeast(0)} s",
            color = Ink.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().panelMotion(3))
        PrimaryAction(if (isReady) "Gotowy" else "Dalej", onReady, !isReady)
    }) {
        RoundNumber(round, totalRounds)
        Title("Wyniki rundy", 1)
        Surface(color = Ink.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().panelMotion(1)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(trackTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(trackArtist, color = Ink.Muted, textAlign = TextAlign.Center)
                Text("Właściciel: $ownerName", color = Ink.Highlight, textAlign = TextAlign.Center)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val ranking = players.sortedByDescending { it.score }
            ranking.forEachIndexed { index, player ->
                ResultRow(player, ranking.indexOfFirst { it.score == player.score }, showAnswer = true,
                    modifier = Modifier.panelMotion(index + 2))
            }
        }
    }
}

@Composable
fun FinalScreen(players: List<UiPlayer>, onReplay: () -> Unit) {
    val ranking = players.sortedByDescending { it.score }
    Screen(footer = { PrimaryAction("Zagraj ponownie", onReplay) }) {
        Title("Ranking")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ranking.forEachIndexed { index, player ->
                ResultRow(player, ranking.indexOfFirst { it.score == player.score }, showAnswer = false,
                    modifier = Modifier.panelMotion(index + 1))
            }
        }
    }
}

@Composable
private fun ResultRow(player: UiPlayer, rank: Int, showAnswer: Boolean, modifier: Modifier = Modifier) {
    val result = when (player.outcome) {
        UiOutcome.CORRECT -> "Trafiona" to Ink.Green
        UiOutcome.WRONG -> "Błędna" to Ink.Red
        UiOutcome.TIMEOUT -> "Brak odpowiedzi" to Ink.Muted
        UiOutcome.NONE -> "" to Ink.Muted
    }
    Surface(color = Ink.Surface, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${rank + 1}.", color = Ink.Muted, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (showAnswer) {
                    Text(result.first, color = result.second, style = MaterialTheme.typography.bodyMedium)
                    player.answerName?.let { Text(it, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium) }
                }
                if (showAnswer && player.ready) Text("Gotowy", color = Ink.Highlight, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${player.score} pkt", fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
                if (showAnswer) Text("+${player.roundPoints}", color = if (player.roundPoints > 0) Ink.Green else Ink.Muted)
            }
        }
    }
}

@Composable
private fun PlayerGrid(players: List<UiPlayer>, tile: @Composable (UiPlayer, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        players.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { column, player -> Box(Modifier.weight(1f)) { tile(player, row * 2 + column) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Avatar(name: String, index: Int, size: Int = 44) {
    val shapes = listOf(RoundedCornerShape(14.dp), CircleShape, RoundedCornerShape(4.dp), RoundedCornerShape(topStart = 18.dp, bottomEnd = 18.dp))
    Box(Modifier.size(size.dp).background(Ink.Raised, shapes[index.mod(shapes.size)]), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Ink.Text, fontWeight = FontWeight.ExtraBold, fontSize = (size * .45f / LocalDensity.current.fontScale).sp)
    }
}

@Composable
private fun AudioWaveform(levels: List<Float>, modifier: Modifier = Modifier) {
    val barCount = 24
    val heights = (0 until barCount).map { index ->
        val target = if (levels.isEmpty()) 0f else levels[(index * levels.size / barCount).coerceAtMost(levels.lastIndex)].coerceIn(0f, 1f)
        val height by animateFloatAsState(targetValue = target, animationSpec = tween(100), label = "audio-bar-$index")
        height
    }
    Canvas(modifier) {
        val slot = size.width / barCount
        val width = slot * .45f
        heights.forEachIndexed { index, level ->
            val barHeight = (size.height * level).coerceAtLeast(3.dp.toPx())
            drawRoundRect(Ink.Highlight,
                Offset(index * slot + (slot - width) / 2f, (size.height - barHeight) / 2f),
                Size(width, barHeight), CornerRadius(width / 2f))
        }
    }
}
