package pl.tuneprint

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.tuneprint.net.NetPlayer
import pl.tuneprint.net.NetResult
import pl.tuneprint.ui.UiPlayer

@Composable
internal fun FinalPodium(players: List<UiPlayer>, last: NetResult?, roster: List<NetPlayer>,
                        host: Boolean, replay: () -> Unit, leave: () -> Unit) {
    val count = minOf(players.size, 3)
    val animate = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    var revealed by rememberSaveable { mutableIntStateOf(if (animate) 0 else count) }
    LaunchedEffect(count) {
        while (revealed < count) {
            delay(550)
            revealed++
        }
    }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xff37234d), Color(0xff171221))))
        .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Podium", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Row(Modifier.fillMaxWidth().heightIn(min = 210.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rank in listOf(2, 1, 3).filter { it <= count }) {
                val player = players[rank - 1]
                val metal = when (rank) { 1 -> Color(0xffffd477); 2 -> Color(0xffd2daed); else -> Color(0xffe9aa83) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(visible = revealed > count - rank, enter = fadeIn() + slideInVertically { it / 2 }) {
                        Text("${player.name}\n${player.score} pkt", textAlign = TextAlign.Center, color = Color.White,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height((152 - 30 * rank).dp)
                        .background(metal, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentAlignment = Alignment.Center) {
                        Text(rank.toString(), color = Color(0xff21172d), style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
        }
        players.drop(3).forEachIndexed { index, player ->
            Text("${index + 4}. ${player.name} · ${player.score} pkt", color = Color.White)
        }
        last?.let {
            Text("Ostatni utwór\n${it.title} · ${it.artist}\n${roster.filter { p -> p.id in it.ownerIds }.joinToString { p -> p.name }}",
                textAlign = TextAlign.Center, color = Color.White)
        }
        if (host) Button(onClick = replay) { Text("Zagraj ponownie") }
        OutlinedButton(onClick = leave) { Text("Opuść pokój") }
    }
}
