package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.domain.BlinkValidators
import com.example.domain.StatsRange
import com.example.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.viewmodel.BlinkDashboardData

@Composable
fun StatisticsScreen(
    data: BlinkDashboardData,
    selectedRange: StatsRange,
    onSelectRange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val aggregate = remember(data.sessions, selectedRange) {
        BlinkValidators.aggregateStatistics(data.sessions, selectedRange)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = strings.statsTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val ranges = listOf(
                    StatsRange.DAILY to strings.daily,
                    StatsRange.WEEKLY to strings.weekly,
                    StatsRange.MONTHLY to strings.monthly
                )
                ranges.forEachIndexed { index, (range, label) ->
                    SegmentedButton(
                        selected = selectedRange == range,
                        onClick = { onSelectRange(range) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ranges.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }

        if (aggregate.totalSessions == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = strings.emptyStatsTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = strings.emptyStatsBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStatTile(
                        title = strings.totalSessions,
                        value = aggregate.totalSessions.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatTile(
                        title = strings.totalFocusTime,
                        value = "${aggregate.totalDurationSeconds / 60}m",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStatTile(
                        title = strings.blinksTriggeredLabel,
                        value = aggregate.totalBlinksTriggered.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatTile(
                        title = strings.restsCompleted,
                        value = "${aggregate.totalEyeRestsCompleted} (${aggregate.totalEyeRestsSkipped} skipped)",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${strings.totalFocusTime} (min)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val barColor = MaterialTheme.colorScheme.primary
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val points = aggregate.dailySeries
                        val maxVal = (points.maxOfOrNull { it.durationMinutes } ?: 1).coerceAtLeast(1)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val count = points.size.coerceAtLeast(1)
                                val slotWidth = size.width / count
                                val barWidth = (slotWidth * 0.56f).coerceAtMost(32.dp.toPx())
                                val chartHeight = size.height - 16.dp.toPx()

                                points.forEachIndexed { idx, pt ->
                                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                                    drawRoundRect(
                                        color = trackColor,
                                        topLeft = Offset(x, 0f),
                                        size = Size(barWidth, chartHeight),
                                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                    )
                                    val ratio = pt.durationMinutes.toFloat() / maxVal.toFloat()
                                    val barH = (chartHeight * ratio).coerceAtLeast(if (pt.durationMinutes > 0) 6.dp.toPx() else 0f)
                                    if (barH > 0f) {
                                        drawRoundRect(
                                            color = barColor,
                                            topLeft = Offset(x, chartHeight - barH),
                                            size = Size(barWidth, barH),
                                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            points.forEach { pt ->
                                Text(
                                    text = pt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = strings.profileUsageTitle,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val maxProfileMin = (aggregate.profileUsage.maxOfOrNull { it.durationMinutes } ?: 1).coerceAtLeast(1)
            items(aggregate.profileUsage) { usage ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(usage.profileName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${usage.durationMinutes}m • ${usage.sessions} sessions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (usage.durationMinutes.toFloat() / maxProfileMin).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(100.dp))
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SummaryStatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
