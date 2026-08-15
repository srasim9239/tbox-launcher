package vad.dashing.tbox.ui.launcher

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import vad.dashing.tbox.R
import kotlin.random.Random

data class LauncherEggRaceCar(
    val id: Int,
    val lane: Int,
    val depth: Float,
    val paintIndex: Int = 0,
    val scored: Boolean = false,
)

/**
 * Hidden endless racer: 8 taps on PRND **D** start it. The 3D car shifts lanes;
 * oncoming cars are drawn on the virtual road like a pocket Tetris racer.
 */
internal object LauncherEggRace {
    const val TAP_TARGET = 8
    private const val TAP_WINDOW_MS = 4_000L
    private const val PLAYER_HIT_MIN = 0.34f
    private const val PLAYER_HIT_MAX = 0.50f
    /** Depth where sprites switch under the 3D body and keep rolling past it. */
    const val PASS_UNDER_DEPTH = 0.46f
    private const val DESPAWN_DEPTH = 0.92f
    private const val HIT_LANE_WIDTH = 0.55f
    private const val START_SPEED_KMH = 22f
    private const val SPEED_PER_PASS_KMH = 2f
    private const val MAX_SPEED_KMH = 52f

    var active by mutableStateOf(false)
        private set
    var playerLane by mutableFloatStateOf(0f)
        private set
    var score by mutableIntStateOf(0)
        private set
    var crashed by mutableStateOf(false)
        private set
    var cars by mutableStateOf<List<LauncherEggRaceCar>>(emptyList())
        private set
    var speedKmh by mutableFloatStateOf(START_SPEED_KMH)
        private set

    private var tapCount = 0
    private var tapWindowStartMs = 0L
    private var nextCarId = 1
    private var spawnCooldown = 2.0f
    private var crashHoldSec = 0f
    private var lastSpawnLane = 0
    private var lastPaintIndex = -1

    fun onDTapped() {
        if (active) return
        val now = SystemClock.elapsedRealtime()
        if (now - tapWindowStartMs > TAP_WINDOW_MS) tapCount = 0
        if (tapCount == 0) tapWindowStartMs = now
        tapCount++
        if (tapCount >= TAP_TARGET) {
            tapCount = 0
            start()
        }
    }

    fun start() {
        active = true
        playerLane = 0f
        score = 0
        crashed = false
        cars = emptyList()
        speedKmh = START_SPEED_KMH
        nextCarId = 1
        spawnCooldown = 2.0f
        crashHoldSec = 0f
        lastSpawnLane = 0
        lastPaintIndex = -1
    }

    fun stop() {
        active = false
        crashed = false
        cars = emptyList()
        playerLane = 0f
        tapCount = 0
    }

    fun setSteer(lane: Float) {
        if (!active) return
        playerLane = lane.coerceIn(-1f, 1f)
    }

    fun tick(dtSec: Float) {
        if (!active) return
        if (crashed) {
            crashHoldSec -= dtSec
            if (crashHoldSec <= 0f) crashed = false
            return
        }
        val depthStep = dtSec * (0.055f + speedKmh * 0.0011f)
        val advanced = ArrayList<LauncherEggRaceCar>(cars.size)
        var hit = false
        for (car in cars) {
            val next = car.depth + depthStep
            if (next >= DESPAWN_DEPTH) continue
            val sameLane = kotlin.math.abs(playerLane - car.lane) < HIT_LANE_WIDTH
            if (!car.scored && sameLane && next in PLAYER_HIT_MIN..PLAYER_HIT_MAX) {
                hit = true
                continue
            }
            var scored = car.scored
            if (!scored && next >= PLAYER_HIT_MAX && !sameLane) {
                score++
                speedKmh = (speedKmh + SPEED_PER_PASS_KMH).coerceAtMost(MAX_SPEED_KMH)
                scored = true
            }
            advanced.add(car.copy(depth = next, scored = scored))
        }
        cars = advanced
        if (hit) {
            crashed = true
            crashHoldSec = 0.7f
            return
        }

        spawnCooldown -= dtSec
        if (spawnCooldown <= 0f && cars.none { it.depth < 0.42f }) {
            spawnWave()
            spawnCooldown = (2.2f - speedKmh * 0.01f).coerceIn(1.5f, 2.2f)
        }
    }

    private fun spawnWave() {
        val blocked = cars.filter { it.depth < 0.48f }.map { it.lane }.toSet()
        val free = listOf(-1, 0, 1).filter { it !in blocked }
        if (free.isEmpty()) return
        val avoidLast = free.filter { it != lastSpawnLane }
        val lane = (if (avoidLast.isNotEmpty()) avoidLast else free).random(Random.Default)
        lastSpawnLane = lane
        val paints = (0 until 6).filter { it != lastPaintIndex }.ifEmpty { listOf(0) }
        val paintIndex = paints.random(Random.Default)
        lastPaintIndex = paintIndex
        cars = cars + LauncherEggRaceCar(
            id = nextCarId++,
            lane = lane,
            depth = 0.02f,
            paintIndex = paintIndex,
        )
    }
}

@Composable
internal fun LauncherEggRaceTicker() {
    val running = LauncherEggRace.active
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var lastMs = 0L
        while (isActive && LauncherEggRace.active) {
            withFrameMillis { now ->
                val dt = if (lastMs == 0L) {
                    0.016f
                } else {
                    ((now - lastMs) / 1000f).coerceIn(0.008f, 0.05f)
                }
                lastMs = now
                LauncherEggRace.tick(dt)
            }
        }
    }
}

internal fun eggRaceCarShiftDp(playerLane: Float) = (playerLane * 128f).dp

@Composable
internal fun LauncherEggRaceCloseBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.action_close),
            color = LauncherColors.LeftTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(LauncherColors.LeftPanelCard)
                .clickable { LauncherEggRace.stop() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.launcher_egg_race_score, LauncherEggRace.score),
            color = if (LauncherEggRace.crashed) {
                LauncherColors.WarningRed
            } else {
                LauncherColors.LeftTextPrimary
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun LauncherEggRaceControls(modifier: Modifier = Modifier) {
    val lane = LauncherEggRace.playerLane
    val steerCd = stringResource(R.string.launcher_egg_race_steer)
    Slider(
        value = lane,
        onValueChange = { LauncherEggRace.setSteer(it) },
        valueRange = -1f..1f,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = steerCd },
        colors = SliderDefaults.colors(
            thumbColor = LauncherColors.AccentCyan,
            activeTrackColor = LauncherColors.AccentCyan,
            inactiveTrackColor = LauncherColors.TextMuted,
        ),
    )
}
