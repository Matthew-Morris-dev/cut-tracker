package dev.matthewmorris.cuttracker.ui.progress

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/** Daily readings as dots with the 7-day average as a line over them. X values are epoch days. */
@Composable
fun TrendChart(data: ChartSeries, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction {
            lineSeries {
                series(data.points.map { it.date.toEpochDay() }, data.points.map { it.value })
                series(data.average.map { it.date.toEpochDay() }, data.average.map { it.value })
            }
        }
    }

    val dotColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val lineColour = MaterialTheme.colorScheme.primary
    val dots = LineCartesianLayer.rememberLine(
        fill = remember { LineCartesianLayer.LineFill.single(fill(Color.Transparent)) },
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.point(rememberShapeComponent(fill(dotColour), CorneredShape.Pill), 6.dp),
        ),
    )
    val average = LineCartesianLayer.rememberLine(
        fill = remember(lineColour) { LineCartesianLayer.LineFill.single(fill(lineColour)) },
        stroke = remember { LineCartesianLayer.LineStroke.continuous(thickness = 3.dp) },
    )

    // Weights sit around 70 to 90 kg, so a y axis from zero would flatten the trend to a line.
    val values = data.points.map { it.value } + data.average.map { it.value }
    val minY = floor(values.min() - 0.5)
    val maxY = ceil(values.max() + 0.5)
    val minX = data.from.toEpochDay().toDouble()
    val maxX = data.to.toEpochDay().toDouble()
    val days = (maxX - minX + 1).toLong()
    val labelSpacing = (days / 4).toInt().coerceAtLeast(1)
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM") }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = remember(dots, average) { LineCartesianLayer.LineProvider.series(dots, average) },
                    rangeProvider = remember(minX, maxX, minY, maxY) {
                        CartesianLayerRangeProvider.fixed(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
                    },
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember {
                        CartesianValueFormatter { _, x, _ -> LocalDate.ofEpochDay(x.toLong()).format(dateFormat) }
                    },
                    itemPlacer = remember(labelSpacing) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }) },
                    guideline = null,
                ),
                // Room for the date label on the last day, which would otherwise be cut off.
                layerPadding = { cartesianLayerPadding(unscalableStart = 8.dp, unscalableEnd = 20.dp) },
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(200.dp),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}
