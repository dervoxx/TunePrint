package pl.tuneprint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
private fun Screen(compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Ink.Ground, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val gutter = if (maxWidth > 600.dp) 32.dp else 24.dp
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(horizontal = gutter, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Brand(trailing: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Rhythm(Modifier.size(26.dp), Ink.Highlight)
            Text("TunePrint", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.5).sp)
        }
        if (trailing.isNotEmpty()) Text(trailing, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Title(text: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, color = Ink.Muted, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) { Text(text, textAlign = TextAlign.Center) }
}

@Composable
fun LobbyScreen(nickname: String, onNicknameChange: (String) -> Unit, players: List<UiPlayer>, onStart: () -> Unit) {
    Screen {
        Brand("Lobby")
        Title("Poznaj ich po muzyce.", "Jeden fragment. Czterech graczy. Czyj to utwór?")
        Surface(color = Ink.Surface, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Rhythm(Modifier.fillMaxWidth().height(64.dp), Ink.Highlight)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Rule("3", "rundy")
                    Rule("15 s", "na odpowiedź")
                    Rule("100", "maks. punktów")
                }
            }
        }
        OutlinedTextField(
            value = nickname, onValueChange = { onNicknameChange(it.take(20)) },
            label = { Text("Twój nick") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Przy wspólnym stole", style = MaterialTheme.typography.titleLarge)
            PlayerGrid(players) { player, index ->
                Surface(color = Ink.Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(player.name, player.id.hashCode(), 36)
                        Text(player.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        PrimaryAction("Zaczynamy", onStart, nickname.isNotBlank())
        Text("Słuchaj i wybierz gracza. Im szybciej trafisz, tym więcej punktów.", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Rule(value: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(caption, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CountdownScreen(round: Int, totalRounds: Int, count: Int) {
    Surface(color = Ink.Ground, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp)) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 632.dp)) { Brand("Runda $round z $totalRounds") }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Nadstaw uszu", style = MaterialTheme.typography.titleLarge, color = Ink.Muted)
                Text(if (count > 0) "$count" else "Start", fontSize = if (count > 0) 144.sp else 72.sp, lineHeight = 160.sp, fontWeight = FontWeight.Black, color = Ink.Text)
                Rhythm(Modifier.width(120.dp).height(40.dp), Ink.Highlight)
            }
            Text("Za chwilę usłyszysz utwór", modifier = Modifier.align(Alignment.BottomCenter), color = Ink.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RoundScreen(
    round: Int, totalRounds: Int, remainingMillis: Long, availablePoints: Int,
    players: List<UiPlayer>, selectedPlayerId: String?, onAnswer: (String) -> Unit,
) {
    val seconds = ceil(remainingMillis.coerceAtLeast(0) / 1000.0).toInt()
    Screen(compact = true) {
        Brand("Runda $round z $totalRounds")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (selectedPlayerId == null) "Do zdobycia" else "Odpowiedź zapisana", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(if (selectedPlayerId == null) "$availablePoints pkt" else "Czekamy na resztę", style = MaterialTheme.typography.titleLarge, color = Ink.Highlight)
            }
            Surface(shape = CircleShape, color = Ink.Surface) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Text("$seconds", fontSize = 28.sp, fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"), color = if (seconds <= 5) Ink.Red else Ink.Text)
                }
            }
        }
        LinearProgressIndicator(progress = { (remainingMillis / 15000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = if (seconds <= 5) Ink.Red else Ink.Highlight, trackColor = Ink.Raised)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Czyj to utwór?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(if (selectedPlayerId == null) "Posłuchaj. Wybierz właściciela." else "Czekamy na pozostałych graczy.", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
            Rhythm(Modifier.fillMaxWidth().height(28.dp), Ink.Highlight)
        }
        // Two equal tiles + 12dp gap use the full available width. At large font scale
        // labels can wrap and height grows; no fixed height clips the answer.
        PlayerGrid(players) { player, index ->
            val chosen = player.id == selectedPlayerId
            OutlinedButton(
                onClick = { onAnswer(player.id) }, enabled = selectedPlayerId == null,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Ink.Surface, contentColor = Ink.Text,
                    disabledContainerColor = if (chosen) Ink.Violet else Ink.Surface,
                    disabledContentColor = if (chosen) Color.White else Ink.Muted,
                ),
                border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp, if (chosen) Ink.Highlight else Ink.Muted.copy(alpha = .6f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 116.dp).semantics { selected = chosen },
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(player.name, player.id.hashCode())
                    Text(player.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (chosen) Text("Twój wybór", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("Odpowiedzieli: ${players.count { it.answered }} z ${players.size}", color = Ink.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            players.forEach { player ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (player.answered) "✓" else "···", color = if (player.answered) Ink.Highlight else Ink.Muted, fontSize = 24.sp)
                    Text(player.name, color = Ink.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Screen {
        Brand("Runda $round z $totalRounds")
        Title("Karty odkryte.", "Ten utwór należy do: $ownerName")
        Surface(color = Ink.Surface, shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Rhythm(Modifier.size(48.dp), Ink.Highlight)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trackTitle, style = MaterialTheme.typography.titleLarge)
                    Text(trackArtist, color = Ink.Muted)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wyniki rundy", style = MaterialTheme.typography.titleLarge)
            players.sortedByDescending { it.score }.forEachIndexed { index, player ->
                ResultRow(player, index, showAnswer = true)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (round == totalRounds) "Do podsumowania" else "Kolejna runda za", color = Ink.Muted)
                Text("${remainingSeconds.coerceAtLeast(0)} s", fontWeight = FontWeight.Bold)
            }
            PrimaryAction(if (isReady) "Jesteś gotowy" else if (round == totalRounds) "Gotowy na wyniki" else "Jestem gotowy", onReady, !isReady)
            Text("Gotowi: ${players.count { it.ready }} z ${players.size}. Gdy wszyscy będą gotowi, ruszamy od razu.", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun FinalScreen(players: List<UiPlayer>, onReplay: () -> Unit) {
    val ranking = players.sortedByDescending { it.score }
    val winner = ranking.firstOrNull()
    val winners = ranking.filter { it.score == winner?.score }
    Screen {
        Brand("Koniec gry")
        Title("Dobry słuch.\nDobra ekipa.", "Trzy rundy za nami. Oto końcowy ranking.")
        if (winner != null) {
            Surface(color = Ink.Surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (winners.size > 1) "Wspólne 1. miejsce" else "1. miejsce", color = Ink.Highlight, fontWeight = FontWeight.Bold)
                    Avatar(winner.name, winner.id.hashCode(), 64)
                    Text(winners.joinToString(" i ") { it.name }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("${winner.score} pkt", fontWeight = FontWeight.Black, fontSize = 40.sp, color = Ink.Highlight)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ranking.forEach { player -> ResultRow(player, ranking.indexOfFirst { it.score == player.score }, showAnswer = false) }
        }
        PrimaryAction("Zagrajmy jeszcze raz", onReplay)
    }
}

@Composable
private fun ResultRow(player: UiPlayer, rank: Int, showAnswer: Boolean) {
    val result = when (player.outcome) {
        UiOutcome.CORRECT -> "Trafiona odpowiedź" to Ink.Green
        UiOutcome.WRONG -> "Błędna odpowiedź" to Ink.Red
        UiOutcome.TIMEOUT -> "Brak odpowiedzi" to Ink.Muted
        UiOutcome.NONE -> "" to Ink.Muted
    }
    Surface(color = Ink.Surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${rank + 1}.", color = Ink.Muted, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (showAnswer) {
                    Text(result.first, color = result.second, style = MaterialTheme.typography.bodyMedium)
                    player.answerName?.let { Text("Wybór: $it", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium) }
                }
                if (showAnswer && player.ready) Text("Gotowy", color = Ink.Highlight, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${player.score} pkt", fontWeight = FontWeight.Bold)
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
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = Ink.Text, fontWeight = FontWeight.ExtraBold, fontSize = (size * .45f).sp)
    }
}

@Composable
private fun Rhythm(modifier: Modifier, color: Color) {
    val heights = listOf(.28f, .5f, .78f, .42f, 1f, .64f, .88f, .46f, .72f, .32f, .56f)
    Canvas(modifier) {
        val gap = size.width / (heights.size * 1.8f)
        val width = gap * .8f
        val totalWidth = heights.size * width + (heights.size - 1) * gap
        heights.forEachIndexed { index, height ->
            val barHeight = size.height * height
            drawRoundRect(color, Offset((size.width - totalWidth) / 2f + index * (width + gap), (size.height - barHeight) / 2f), Size(width, barHeight), CornerRadius(width / 2f))
        }
    }
}
