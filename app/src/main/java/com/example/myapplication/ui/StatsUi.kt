package com.example.myapplication.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myapplication.data.ListeningStats
import com.example.myapplication.data.StatsPeriod
import com.example.myapplication.player.PlayLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun countOf(count: Long, one: String, few: String, many: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1L && mod100 != 11L -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "${grouped(count)} $word"
}

private fun grouped(count: Long): String = String.format(Locale.forLanguageTag("ru"), "%,d", count)

/** The time of day [hour] is in, as said after "чаще всего". */
private fun partOfDay(hour: Int): String = when (hour) {
    in 0..5 -> "ночью"
    in 6..11 -> "утром"
    in 12..17 -> "днём"
    else -> "вечером"
}

/**
 * "Итоги": how much music, whose and which most, from where and at what time of day, over a
 * week, a month, a year or all of it — and a card of it, the size of a story, to share or keep.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val plays by produceState<List<PlayLog.Play>?>(null) {
        value = withContext(Dispatchers.IO) { PlayLog.read(context) }
    }
    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
    val stats = remember(plays, period) { plays?.let { ListeningStats.of(it, period) } }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Kicker(text = "Итоги", color = PanelColors.accent)
                    Text("Ваша музыка", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StatsPeriod.entries) { choice ->
                        Surface(
                            onClick = { period = choice },
                            shape = CircleShape,
                            color = if (choice == period) PanelColors.accent else PanelColors.container,
                            contentColor = if (choice == period) PanelColors.onAccent else PanelColors.content
                        ) {
                            Text(choice.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        }
                    }
                }
            }
            when {
                stats == null -> item { Spacer(modifier = Modifier.height(240.dp)) }
                stats.isEmpty -> item {
                    StatsPanel {
                        Text("Пока пусто", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = PanelColors.content)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (period == StatsPeriod.ALL) {
                                "Итоги копятся с этой версии: каждый трек, который вы слушаете дольше полуминуты. Скоро здесь будут минуты, любимые артисты и треки."
                            } else {
                                "За это время ничего не прослушано. Посмотрите итоги за больший срок."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PanelColors.content.copy(alpha = 0.75f)
                        )
                    }
                }
                else -> {
                    item { Totals(stats) }
                    item { TopArtists(stats) }
                    item { TopTracks(stats) }
                    item { Sources(stats) }
                    item { Hours(stats) }
                    item { StoryCardSection(stats, period) }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PanelColors.container)
            .padding(20.dp),
        content = content
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Totals(stats: ListeningStats) {
    StatsPanel {
        Text(
            grouped(stats.minutes),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = PanelColors.accent
        )
        Text(
            countOf(stats.minutes, "минута", "минуты", "минут").substringAfter(' ') + " музыки",
            style = MaterialTheme.typography.titleMedium,
            color = PanelColors.content
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnPanelChip(countOf(stats.trackCount.toLong(), "трек", "трека", "треков"))
            OnPanelChip(countOf(stats.artistCount.toLong(), "артист", "артиста", "артистов"))
            OnPanelChip(countOf(stats.playCount.toLong(), "прослушивание", "прослушивания", "прослушиваний"))
        }
    }
}

@Composable
private fun TopArtists(stats: ListeningStats) {
    val top = stats.topArtists.take(5)
    val most = top.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L
    StatsPanel {
        Kicker(text = "Любимые артисты", color = PanelColors.accent)
        Spacer(modifier = Modifier.height(12.dp))
        top.forEachIndexed { i, artist ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = PanelColors.accent,
                    modifier = Modifier.width(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = PanelColors.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${grouped(artist.ms / 60_000)} мин",
                            style = MaterialTheme.typography.labelMedium,
                            color = PanelColors.content.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // Each against the first: how far behind it.
                    val share = artist.ms.toFloat() / most
                    val bar = PanelColors.accent
                    val track = PanelColors.content.copy(alpha = 0.1f)
                    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
                        drawRoundRect(bar, size = Size(size.width * share, size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTracks(stats: ListeningStats) {
    StatsPanel {
        Kicker(text = "На повторе", color = PanelColors.accent)
        Spacer(modifier = Modifier.height(10.dp))
        stats.topTracks.take(5).forEach { track ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                AsyncImage(
                    model = track.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelColors.content.copy(alpha = 0.1f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = PanelColors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = PanelColors.content.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    countOf(track.plays.toLong(), "раз", "раза", "раз"),
                    style = MaterialTheme.typography.labelMedium,
                    color = PanelColors.accent
                )
            }
        }
    }
}

@Composable
private fun Sources(stats: ListeningStats) {
    val total = stats.sources.sumOf { it.second }.coerceAtLeast(1L)
    val shades = listOf(
        PanelColors.accent,
        PanelColors.accent.copy(alpha = 0.6f),
        PanelColors.content.copy(alpha = 0.4f),
        PanelColors.content.copy(alpha = 0.22f),
        PanelColors.content.copy(alpha = 0.12f)
    )
    StatsPanel {
        Kicker(text = "Откуда музыка", color = PanelColors.accent)
        Spacer(modifier = Modifier.height(14.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
            var x = 0f
            stats.sources.forEachIndexed { i, (_, ms) ->
                val w = size.width * ms / total
                drawRect(shades[i.coerceAtMost(shades.lastIndex)], topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        stats.sources.forEachIndexed { i, (source, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(shades[i.coerceAtMost(shades.lastIndex)]))
                Spacer(modifier = Modifier.width(10.dp))
                Text(ListeningStats.sourceName(source), style = MaterialTheme.typography.bodyMedium, color = PanelColors.content, modifier = Modifier.weight(1f))
                Text("${(ms * 100 / total)}%", style = MaterialTheme.typography.labelLarge, color = PanelColors.content.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun Hours(stats: ListeningStats) {
    val peak = stats.byHour.indices.maxByOrNull { stats.byHour[it] } ?: 0
    val most = stats.byHour.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val bar = PanelColors.accent
    val quiet = PanelColors.content.copy(alpha = 0.15f)
    StatsPanel {
        Kicker(text = "Когда слушаете", color = PanelColors.accent)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Чаще всего ${partOfDay(peak)}", style = MaterialTheme.typography.titleMedium, color = PanelColors.content)
        Spacer(modifier = Modifier.height(14.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            val slot = size.width / 24
            val width = slot * 0.62f
            for (hour in 0 until 24) {
                val h = (size.height * stats.byHour[hour] / most).coerceAtLeast(4f)
                drawRoundRect(
                    color = if (stats.byHour[hour] > 0) bar.copy(alpha = 0.35f + 0.65f * stats.byHour[hour] / most) else quiet,
                    topLeft = Offset(hour * slot + (slot - width) / 2, size.height - h),
                    size = Size(width, h),
                    cornerRadius = CornerRadius(width / 2)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18").forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = PanelColors.content.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The card, as the story it is shared as: laid out at 1080 by 1920 pixels whatever the screen,
 * shown scaled down, and taken whole from its layer when shared or saved.
 */
@Composable
private fun StoryCardSection(stats: ListeningStats, period: StatsPeriod) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val screen = LocalDensity.current
    val cardWidth = with(screen) { CARD_WIDTH_PX.toDp() }
    val cardHeight = with(screen) { CARD_HEIGHT_PX.toDp() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Kicker(text = "Карточка для сторис", color = PanelColors.accent, modifier = Modifier.fillMaxWidth().padding(start = 4.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val previewWidth = minOf(maxWidth * 0.78f, 320.dp)
            val scale = previewWidth / cardWidth
            Box(
                modifier = Modifier
                    .size(previewWidth, previewWidth * 16f / 9f)
                    .clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(cardWidth, cardHeight)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        }
                ) {
                    // A card's own dp: 400 of them across its 1080 pixels on every phone.
                    CompositionLocalProvider(LocalDensity provides Density(CARD_WIDTH_PX / 400f, fontScale = 1f)) {
                        StoryCard(stats, period)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { scope.launch { shareCard(context, layer) } },
                shape = RoundedCornerShape(20.dp),
                color = PanelColors.accent,
                contentColor = PanelColors.onAccent,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Поделиться", style = MaterialTheme.typography.titleMedium) }
            }
            Surface(
                onClick = { scope.launch { saveCard(context, layer) } },
                shape = RoundedCornerShape(20.dp),
                color = PanelColors.container,
                contentColor = PanelColors.content,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Сохранить", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun StoryCard(stats: ListeningStats, period: StatsPeriod) {
    val colors = MaterialTheme.colorScheme
    val accent = PanelColors.accent
    val glow = colors.tertiary
    val text = colors.onBackground
    Column(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.background)
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.08f), radius = size.width * 0.9f),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.9f, size.height * 0.08f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width * 0.05f, size.height * 0.95f), radius = size.width * 0.8f),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.05f, size.height * 0.95f)
                )
            }
            .padding(horizontal = 30.dp, vertical = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker(text = "YouCloud · итоги", color = accent, modifier = Modifier.weight(1f))
            Kicker(text = period.cardLabel, color = text.copy(alpha = 0.7f))
        }
        Spacer(modifier = Modifier.height(30.dp))
        Text(grouped(stats.minutes), fontSize = 66.sp, lineHeight = 70.sp, fontWeight = FontWeight.ExtraBold, color = text)
        Text(
            countOf(stats.minutes, "минута", "минуты", "минут").substringAfter(' ') + " музыки",
            fontSize = 22.sp,
            color = text.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Kicker(text = "Любимые артисты", color = accent)
        Spacer(modifier = Modifier.height(8.dp))
        stats.topArtists.take(5).forEachIndexed { i, artist ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text("${i + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent, modifier = Modifier.width(28.dp))
                Text(
                    artist.name,
                    fontSize = if (i == 0) 22.sp else 18.sp,
                    fontWeight = if (i == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Kicker(text = "На повторе", color = accent)
        Spacer(modifier = Modifier.height(8.dp))
        stats.topTracks.take(3).forEach { track ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                AsyncImage(
                    model = track.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(text.copy(alpha = 0.1f))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(track.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, fontSize = 13.sp, color = text.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        val total = stats.sources.sumOf { it.second }.coerceAtLeast(1L)
        Text(
            stats.sources.take(3).joinToString("  ·  ") { (source, ms) -> "${ListeningStats.sourceName(source)} ${ms * 100 / total}%" },
            fontSize = 14.sp,
            color = text.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("Слушаю в YouCloud", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent)
    }
}

private const val CARD_WIDTH_PX = 1080
private const val CARD_HEIGHT_PX = 1920

private suspend fun cardBitmap(layer: GraphicsLayer): Bitmap = layer.toImageBitmap().asAndroidBitmap()

/** The card as a picture in the app's cache, for a share sheet to read through its provider. */
private suspend fun shareCard(context: android.content.Context, layer: GraphicsLayer) {
    val bitmap = cardBitmap(layer)
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "youcloud-itogi.png").also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Итоги YouCloud"))
}

/** The card into the gallery, "Изображения/YouCloud": no permission needed for one's own pictures. */
private suspend fun saveCard(context: android.content.Context, layer: GraphicsLayer) {
    val bitmap = cardBitmap(layer)
    val saved = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "YouCloud итоги ${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YouCloud")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no place for it")
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: error("couldn't write")
        }.isSuccess
    }
    Toast.makeText(
        context,
        if (saved) "Карточка сохранена в «Изображения/YouCloud»" else "Не удалось сохранить карточку",
        Toast.LENGTH_SHORT
    ).show()
}
