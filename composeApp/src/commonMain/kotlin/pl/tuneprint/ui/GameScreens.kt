package pl.tuneprint.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlinx.coroutines.delay

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
    val hitCount: Int = 0,
    val totalOwners: Int = 0,
    val wrongSelections: Int = 0,
    val textAnswerMode: Boolean = false,
)

enum class UiOutcome { NONE, CORRECT, PARTIAL, WRONG, TIMEOUT }

data class UiRoom(val code: String, val hostName: String, val roundCount: Int)

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
    scrollable: Boolean = true,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = Ink.Ground, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            val gutter = if (maxWidth > 600.dp) 32.dp else 24.dp
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = gutter, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 24.dp),
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
private fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, reservedLabels: List<String> = emptyList()) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).panelMotion(3),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (reservedLabels.isEmpty()) Text(text, textAlign = TextAlign.Center)
        else StableResultText(text, reservedLabels, LocalContentColor.current,
            LocalTextStyle.current.copy(textAlign = TextAlign.Center))
    }
}

@Composable
fun HomeScreen(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    heading: String = "Gra ze znajomymi",
    canEnter: Boolean = true,
    settings: @Composable () -> Unit = {},
    extraActions: @Composable () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val validNickname = nickname.isNotBlank() && canEnter
    val open: (() -> Unit) -> Unit = { action ->
        if (validNickname) {
            focus.clearFocus()
            keyboard?.hide()
            action()
        }
    }
    Screen(compact = true) {
        Box(Modifier.fillMaxWidth().heightIn(min = 136.dp)
            .clip(RoundedCornerShape(22.dp)).background(Ink.Surface), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width * .92f, size.height * .83f)
                for (i in 1..12) {
                    drawCircle(Ink.Highlight.copy(alpha = if (i % 4 == 0) .25f else .09f),
                        radius = i * 13.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
                }
                drawCircle(Ink.Violet, 12.dp.toPx(), center)
                drawCircle(Ink.Ground, 3.dp.toPx(), center)
            }
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TunePrint", style = MaterialTheme.typography.headlineLarge, color = Ink.Text,
                    modifier = Modifier.semantics { heading() })
                Text(heading, style = MaterialTheme.typography.bodyLarge, color = Ink.Muted)
            }
        }
        OutlinedTextField(
            value = nickname, onValueChange = { onNicknameChange(it.take(20)) },
            label = { Text("Twój nick") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Ink.Highlight, focusedBorderColor = Ink.Highlight, cursorColor = Ink.Highlight),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().panelMotion(1), shape = RoundedCornerShape(16.dp),
        )
        settings()
        PrimaryAction("Utwórz pokój", { open(onCreate) }, validNickname)
        OutlinedButton(
            onClick = { open(onJoin) }, enabled = validNickname,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Text),
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).panelMotion(4),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        ) { Text("Dołącz do pokoju", textAlign = TextAlign.Center) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { extraActions() }
    }
}

@Composable
fun JoinRoomScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    rooms: List<UiRoom>,
    onSelectRoom: (String) -> Unit,
    error: String?,
    onRefresh: (() -> Unit)? = null,
    canJoin: Boolean = true,
) {
    val validCode = canJoin && code.length == 6 && code.all { it in '0'..'9' }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val join = {
        if (validCode) {
            focus.clearFocus()
            keyboard?.hide()
            onJoin()
        }
    }
    Screen(footer = {
        PrimaryAction("Dołącz", join, validCode)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).panelMotion(4)) {
            Text("Wróć", color = Ink.Muted)
        }
    }) {
        Title("Dołącz do pokoju")
        if (onRefresh != null) {
            if (rooms.isEmpty() && canJoin && error == null) Text("Brak otwartych pokoi. Możesz wpisać kod poniżej.", color = Ink.Muted)
            OutlinedButton(onClick = onRefresh, enabled = canJoin) { Text("Odśwież listę") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rooms.forEachIndexed { index, room ->
                OutlinedButton(onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    onSelectRoom(room.code)
                }, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.fillMaxWidth().panelMotion(index + 1)) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(room.hostName, color = Ink.Text, style = MaterialTheme.typography.titleLarge)
                        Text("${room.code} · ${room.roundCount} ${roundNoun(room.roundCount)}", color = Ink.Muted)
                    }
                }
            }
        }
        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChange(it.filter { character -> character in '0'..'9' }.take(6)) },
            label = { Text("Kod pokoju") },
            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Ink.Highlight, focusedBorderColor = Ink.Highlight, cursorColor = Ink.Highlight),
            singleLine = true,
            isError = error != null,
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, letterSpacing = 4.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { join() }),
            modifier = Modifier.fillMaxWidth().panelMotion(4),
            shape = RoundedCornerShape(16.dp),
        )
        StableResultText(error ?: " ", listOf("Nie znaleziono pokoju"), Ink.Red,
            MaterialTheme.typography.bodyMedium, Modifier.fillMaxWidth().panelMotion(4))
    }
}

@Composable
fun LobbyScreen(
    players: List<UiPlayer>,
    onStart: () -> Unit,
    roomCode: String,
    onLeave: () -> Unit,
    isHost: Boolean,
    roundCount: Int,
    maxRounds: Int,
    onRoundCountChange: (Int) -> Unit,
    capacity: Int,
    onCapacityChange: (Int) -> Unit,
    showOwnerCount: Boolean,
    onShowOwnerCountChange: (Boolean) -> Unit,
    canStart: Boolean = players.size >= 3,
    startLabel: String = "Zaczynamy",
    modeLabel: String? = null,
    onShare: (() -> Unit)? = null,
    ownerHintsAvailable: Boolean = true,
    modeControl: @Composable () -> Unit = {},
) {
    Screen(footer = {
        PrimaryAction(startLabel, onStart, canStart)
        TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).panelMotion(4)) {
            Text("Wyjdź", color = Ink.Muted)
        }
    }) {
        Title("Lobby")
        Surface(color = Ink.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Kod pokoju", color = Ink.Muted, style = MaterialTheme.typography.bodyMedium)
                Text(roomCode.chunked(3).joinToString(" "), color = Ink.Text, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 3.sp, fontFeatureSettings = "tnum"))
                if (onShare != null) TextButton(onClick = onShare) { Text("Udostępnij kod", color = Ink.Highlight) }
            }
        }
        val roundLimit = maxRounds.coerceAtLeast(1)
        var settingsOpen by rememberSaveable(roomCode) { mutableStateOf(false) }
        Text(listOfNotNull(modeLabel, "$roundCount ${roundNoun(roundCount)}", "15 s").joinToString("  ·  "), color = Ink.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (isHost) OutlinedButton(onClick = { settingsOpen = !settingsOpen },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (settingsOpen) "Zwiń ustawienia" else "Ustawienia gry", color = Ink.Text)
        }
        if (isHost && settingsOpen) {
            modeControl()
            Row(Modifier.fillMaxWidth().panelMotion(1), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onRoundCountChange((roundCount - 1).coerceIn(1, roundLimit)) },
                    enabled = roundCount > 1, modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Text), contentPadding = PaddingValues(0.dp)) {
                    Text("−", fontSize = 24.sp)
                }
                StableResultText("$roundCount ${roundNoun(roundCount)}", (1..roundLimit).map { "$it ${roundNoun(it)}" },
                    Ink.Text, MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    Modifier.weight(1f))
                OutlinedButton(onClick = { onRoundCountChange((roundCount + 1).coerceIn(1, roundLimit)) },
                    enabled = roundCount < roundLimit, modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Text), contentPadding = PaddingValues(0.dp)) {
                    Text("+", fontSize = 24.sp)
                }
            }
            Row(Modifier.fillMaxWidth().panelMotion(2), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onCapacityChange((capacity - 1).coerceIn(3, 8)) },
                    enabled = capacity > maxOf(3, players.size), modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Text), contentPadding = PaddingValues(0.dp)) {
                    Text("−", fontSize = 24.sp)
                }
                StableResultText("Limit: $capacity ${if (capacity in 3..4) "osoby" else "osób"}",
                    (3..8).map { "Limit: $it ${if (it in 3..4) "osoby" else "osób"}" },
                    Ink.Text, MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    Modifier.weight(1f))
                OutlinedButton(onClick = { onCapacityChange((capacity + 1).coerceIn(3, 8)) },
                    enabled = capacity < 8, modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Text), contentPadding = PaddingValues(0.dp)) {
                    Text("+", fontSize = 24.sp)
                }
            }
        }
        if (ownerHintsAvailable && isHost && settingsOpen) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).panelMotion(2)
                .toggleable(value = showOwnerCount, role = Role.Switch, onValueChange = onShowOwnerCountChange),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pokaż liczbę playlist", modifier = Modifier.weight(1f))
                Switch(checked = showOwnerCount, onCheckedChange = null)
            }
        } else if (ownerHintsAvailable && showOwnerCount) {
            Text("Liczba playlist: widoczna", color = Ink.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().panelMotion(2))
        }
        Text("Gracze ${players.size}/$capacity", color = Ink.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().panelMotion(2))
        val lobbySlots = List(capacity) { index -> players.getOrNull(index) ?: UiPlayer("waiting-$index", "Czekamy") }
        PlayerGrid(lobbySlots) { player, index ->
            val waiting = index >= players.size
            val cardAlpha by animateFloatAsState(if (waiting) .5f else 1f, tween(180), label = "lobby-slot-$index")
            Surface(color = Ink.Surface, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().panelMotion(index + 2).alpha(cardAlpha)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(if (waiting) "…" else player.name, 36)
                    AnimatedContent(targetState = player.name, modifier = Modifier.weight(1f),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "lobby-name-$index") { name ->
                        Text(name, fontWeight = FontWeight.Bold, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun roundNoun(count: Int): String = when (count) {
    1 -> "runda"
    in 2..4 -> "rundy"
    else -> "rund"
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
    players: List<UiPlayer>, selectedPlayerIds: Set<String>, submitted: Boolean,
    onToggle: (String) -> Unit, onSubmit: () -> Unit,
    audioLevels: List<Float> = emptyList(),
    acceptingAnswers: Boolean = true,
    ownerCountHint: Int? = null,
    question: String = "Czyj to utwór?",
    textAnswer: String? = null,
    onTextChange: (String) -> Unit = {},
) {
    val seconds = ceil(remainingMillis.coerceAtLeast(0) / 1000.0).toInt()
    Screen(compact = true, footer = {
        PrimaryAction(if (submitted) "Zatwierdzono" else "Zatwierdź", onSubmit,
            (textAnswer?.isNotBlank() ?: selectedPlayerIds.isNotEmpty()) && !submitted && acceptingAnswers,
            reservedLabels = listOf("Zatwierdź", "Zatwierdzono"))
    }) {
        RoundNumber(round, totalRounds)
        Title(question, 1)
        if (ownerCountHint != null) {
            StableResultText("Na $ownerCountHint ${if (ownerCountHint == 1) "playliście" else "playlistach"}",
                listOf("Na 1 playliście", "Na 8 playlistach"), Ink.Muted,
                MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                Modifier.fillMaxWidth().panelMotion(1))
        }
        Row(Modifier.fillMaxWidth().panelMotion(1), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$availablePoints pkt", maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Ink.Highlight, modifier = Modifier.weight(1f))
            Text("${seconds.toString().padStart(2, '0')} s", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 72.dp), textAlign = TextAlign.End, maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum"), color = if (seconds <= 5) Ink.Red else Ink.Text)
        }
        LinearProgressIndicator(progress = { (remainingMillis / 15000f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).panelMotion(1),
            color = if (seconds <= 5) Ink.Red else Ink.Highlight, trackColor = Ink.Raised)
        AudioWaveform(audioLevels, Modifier.fillMaxWidth().height(48.dp).panelMotion(2))
        if (textAnswer != null) {
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            OutlinedTextField(value = textAnswer, onValueChange = onTextChange,
                label = { Text("Twoja odpowiedź") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (textAnswer.isNotBlank() && acceptingAnswers && !submitted) {
                        focus.clearFocus()
                        keyboard?.hide()
                        onSubmit()
                    }
                }),
                enabled = !submitted && acceptingAnswers, modifier = Modifier.fillMaxWidth())
        } else OwnerChoices(players, selectedPlayerIds, onToggle, !submitted && acceptingAnswers)
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
private fun OwnerChoices(players: List<UiPlayer>, selectedPlayerIds: Set<String>, onToggle: (String) -> Unit, enabled: Boolean) {
    // Two equal tiles + 12dp gap fill the width; text can grow and wrap.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val nameWidth = with(density) { (((maxWidth - 12.dp) / 2) - 24.dp).roundToPx().coerceAtLeast(1) }
        val nameLines = players.maxOfOrNull { player ->
            textMeasurer.measure(AnnotatedString(player.name), style = nameStyle,
                constraints = Constraints(maxWidth = nameWidth)).lineCount
        } ?: 1
        PlayerGrid(players) { player, index ->
            val chosen = player.id in selectedPlayerIds
            OutlinedButton(
                onClick = { onToggle(player.id) }, enabled = enabled,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (chosen) Ink.Violet else Ink.Surface,
                    contentColor = if (chosen) Color.White else Ink.Text,
                    disabledContainerColor = if (chosen) Ink.Violet else Ink.Surface,
                    disabledContentColor = if (chosen) Color.White else Ink.Muted,
                ),
                border = androidx.compose.foundation.BorderStroke(if (chosen) 2.dp else 1.dp,
                    if (chosen) Ink.Highlight else Ink.Muted.copy(alpha = .6f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp).panelMotion(index + 2).semantics { selected = chosen },
                contentPadding = PaddingValues(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(player.name)
                    Text(player.name, style = nameStyle, minLines = nameLines, maxLines = nameLines, textAlign = TextAlign.Center)
                    Text("Wybrano", style = MaterialTheme.typography.bodyMedium,
                        modifier = if (chosen) Modifier else Modifier.alpha(0f).clearAndSetSemantics { })
                }
            }
        }
    }
}

@Composable
fun ResultsScreen(
    round: Int, totalRounds: Int, trackTitle: String, trackArtist: String, ownerNames: List<String>,
    players: List<UiPlayer>, remainingSeconds: Int, isReady: Boolean, onReady: () -> Unit,
    onReplay: () -> Unit, isFinal: Boolean = false,
    canReplay: Boolean = true,
    onLeave: (() -> Unit)? = null,
) {
    val previousScores = remember(round) { players.associate { it.id to (it.score - it.roundPoints) } }
    var revealScores by remember(round) { mutableStateOf(false) }
    LaunchedEffect(round) {
        delay(400)
        revealScores = true
    }
    val easeInOut = CubicBezierEasing(.42f, 0f, .58f, 1f)
    val ranking = players.sortedByDescending { if (revealScores) it.score else previousScores.getValue(it.id) }
    Screen(scrollable = false, footer = {
        if (isFinal) {
            PrimaryAction(if (canReplay) "Zagraj ponownie" else "Czekamy na gospodarza", onReplay, canReplay)
            if (onLeave != null) TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Opuść pokój", color = Ink.Muted)
            }
        } else {
            Text("Gotowi ${players.count { it.ready }}/${players.size} · ${remainingSeconds.coerceAtLeast(0)} s",
                color = Ink.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().panelMotion(3))
            PrimaryAction(if (isReady) "Gotowy" else "Dalej", onReady, !isReady)
        }
    }) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isFinal) item(key = "round") { RoundNumber(round, totalRounds) }
            item(key = "title") { Title(if (isFinal) "Ranking końcowy" else "Wyniki rundy", 1) }
            item(key = "track") {
                Surface(color = Ink.Surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().panelMotion(1)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(trackTitle, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        if (trackArtist.isNotBlank()) Text(trackArtist, color = Ink.Muted, textAlign = TextAlign.Center)
                        Text("Na liście: ${ownerNames.joinToString(", ")}", color = Ink.Highlight, textAlign = TextAlign.Center)
                    }
                }
            }
            itemsIndexed(ranking, key = { _, player -> player.id }) { index, player ->
                val score by animateIntAsState(
                    targetValue = if (revealScores) player.score else previousScores.getValue(player.id),
                    animationSpec = tween(700, easing = easeInOut), label = "score-${player.id}",
                )
                val targetScore = if (revealScores) player.score else previousScores.getValue(player.id)
                val rank = ranking.indexOfFirst { (if (revealScores) it.score else previousScores.getValue(it.id)) == targetScore }
                ResultRow(player, rank, showAnswer = true, roster = players, displayedScore = score, showReadyStatus = !isFinal,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null, placementSpec = tween(700, easing = easeInOut), fadeOutSpec = null,
                    ).panelMotion(index + 2))
            }
        }
    }
}

@Composable
private fun ResultRow(player: UiPlayer, rank: Int, showAnswer: Boolean, roster: List<UiPlayer>, modifier: Modifier = Modifier, displayedScore: Int = player.score, showReadyStatus: Boolean = true) {
    val result = when (player.outcome) {
        UiOutcome.CORRECT -> hitSummary(player) to Ink.Green
        UiOutcome.PARTIAL -> hitSummary(player) to Ink.Highlight
        UiOutcome.WRONG -> hitSummary(player) to Ink.Red
        UiOutcome.TIMEOUT -> "Brak odpowiedzi" to Ink.Muted
        UiOutcome.NONE -> "" to Ink.Muted
    }
    Surface(color = if (rank == 0 && !showReadyStatus) Ink.Raised else Ink.Surface,
        shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${rank + 1}.", color = Ink.Muted, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StableResultText(player.name, roster.map { it.name },
                    Ink.Text, MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp))
                if (showAnswer) {
                    StableResultText(result.first, roster.map { hitSummary(it) } + "Brak odpowiedzi",
                        result.second, MaterialTheme.typography.bodyMedium)
                    StableResultText(player.answerName ?: " ", roster.mapNotNull { it.answerName }.ifEmpty { listOf(" ") },
                        Ink.Muted, MaterialTheme.typography.bodyMedium)
                    if (showReadyStatus) Text("Gotowy", color = Ink.Highlight, style = MaterialTheme.typography.bodyMedium,
                        modifier = if (player.ready) Modifier else Modifier.alpha(0f).clearAndSetSemantics { })
                }
            }
            Column(Modifier.width(72.dp * LocalDensity.current.fontScale), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$displayedScore pkt", fontWeight = FontWeight.Bold, style = TextStyle(fontFeatureSettings = "tnum"))
                if (showAnswer) Text("+${player.roundPoints}", color = if (player.roundPoints > 0) Ink.Green else Ink.Muted)
            }
        }
    }
}

private fun hitSummary(player: UiPlayer): String {
    if (player.textAnswerMode) return if (player.outcome == UiOutcome.CORRECT) "Poprawna odpowiedź" else "Błędna odpowiedź"
    val hits = "${player.hitCount}/${player.totalOwners} trafień"
    val errors = when (player.wrongSelections) {
        1 -> "błąd"
        in 2..4 -> "błędy"
        else -> "błędów"
    }
    return if (player.wrongSelections > 0) "$hits · ${player.wrongSelections} $errors" else hits
}

/** Reserve the longest roster/status value, keeping rows stable as readiness changes. */
@Composable
private fun StableResultText(text: String, samples: List<String>, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        samples.forEach { sample ->
            Text(sample, style = style, modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics { })
        }
        Text(text, color = color, style = style, modifier = Modifier.fillMaxWidth())
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
private fun Avatar(name: String, size: Int = 44) {
    Box(Modifier.size(size.dp).background(Ink.Raised, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
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
