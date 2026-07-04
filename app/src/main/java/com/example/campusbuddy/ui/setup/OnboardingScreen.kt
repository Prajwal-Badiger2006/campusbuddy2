package com.example.campusbuddy.ui.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.campusbuddy.ui.components.AppPrimaryButton
import com.example.campusbuddy.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class OnboardingItem(
    val title: String,
    val description: String,
    val pageColor: Color,
    val gradientEndColor: Color
)

val onboardingItems = listOf(
    OnboardingItem(
        title = "Find Study Partners",
        description = "Connect with students in your college who share your interests and goals. Find the perfect study partner for any subject.",
        pageColor = Color(0xFF003527),
        gradientEndColor = Color(0xFF064E3B)
    ),
    OnboardingItem(
        title = "Form Project Teams",
        description = "Need teammates for a hackathon or project? Post a request and get matched with skilled students ready to collaborate.",
        pageColor = Color(0xFF0F00A3),
        gradientEndColor = Color(0xFF2C2ABC)
    ),
    OnboardingItem(
        title = "Chat & Collaborate",
        description = "Built-in real-time messaging lets you coordinate with your partners, share ideas, and work together seamlessly.",
        pageColor = Color(0xFF006C49),
        gradientEndColor = Color(0xFF00714D)
    ),
    OnboardingItem(
        title = "Build Your Streak",
        description = "Stay active and build your reliability score. The more you engage, the better matches you'll get!",
        pageColor = Color(0xFFBA1A1A),
        gradientEndColor = Color(0xFF93000A)
    )
)

private val backgroundGradients = listOf(
    listOf(Color(0xFFE8F5E9), Color(0xFFF1F8E9)),
    listOf(Color(0xFFE8EAF6), Color(0xFFF3E5F5)),
    listOf(Color(0xFFE0F2F1), Color(0xFFF1F8E9)),
    listOf(Color(0xFFFFF3E0), Color(0xFFFFEBEE))
)

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingItems.size })
    val scope = rememberCoroutineScope()

    // Interpolated background colors based on pager position
    val pageOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val targetBgIndex = pageOffset.coerceIn(0f, (onboardingItems.size - 1).toFloat())
    val bgIndex = targetBgIndex.toInt().coerceAtMost(onboardingItems.size - 2)
    val bgFraction = targetBgIndex - bgIndex

    val currentBgColors = backgroundGradients[bgIndex.coerceAtMost(backgroundGradients.size - 2)]
    val nextBgColors = backgroundGradients[(bgIndex + 1).coerceAtMost(backgroundGradients.size - 1)]

    val startColor = remember(bgIndex, bgFraction) {
        lerp(currentBgColors[0], nextBgColors[0], bgFraction.coerceIn(0f, 1f))
    }
    val endColor = remember(bgIndex, bgFraction) {
        lerp(currentBgColors[1], nextBgColors[1], bgFraction.coerceIn(0f, 1f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(startColor, endColor)
                )
            )
    ) {
        // Skip button at top-right corner
        if (pagerState.currentPage < onboardingItems.size - 1) {
            TextButton(
                onClick = onNavigateToHome,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Main content: centered pager + indicators
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // HorizontalPager with dynamic transitions
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1
            ) { page ->
                val item = onboardingItems[page]
                val pageOffsetFraction = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scaleFactor = 1f - (pageOffsetFraction.absoluteValue * 0.12f).coerceIn(0f, 0.12f)
                val alpha = 1f - (pageOffsetFraction.absoluteValue * 0.3f).coerceIn(0f, 0.3f)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                            this.alpha = alpha
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Custom Canvas illustration
                    OnboardingIllustration(
                        pageIndex = page,
                        color = item.pageColor,
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Worm-style animated indicator
            WormIndicator(
                pageCount = onboardingItems.size,
                currentPage = pagerState.currentPage,
                pageOffset = pagerState.currentPageOffsetFraction
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Bottom buttons: Next / Get Started
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            AppPrimaryButton(
                text = if (pagerState.currentPage == onboardingItems.size - 1) "Get Started"
                else "Next",
                onClick = {
                    if (pagerState.currentPage < onboardingItems.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onNavigateToHome()
                    }
                }
            )
        }
    }
}

// ==========================================
// WORM INDICATOR
// ==========================================
@Composable
private fun WormIndicator(
    pageCount: Int,
    currentPage: Int,
    pageOffset: Float
) {
    val density = LocalDensity.current
    val dotWidth = 32.dp
    val dotHeight = 8.dp
    val spacing = 8.dp
    val indicatorWidth = dotWidth * pageCount + spacing * (pageCount - 1)

    val stepPx = with(density) { (dotWidth + spacing).toPx() }

    val animatedOffset by animateFloatAsState(
        targetValue = currentPage * stepPx + pageOffset * stepPx,
        animationSpec = tween(durationMillis = 300),
        label = "wormOffset"
    )

    Box(
        modifier = Modifier
            .width(indicatorWidth + 4.dp)
            .height(dotHeight + 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val dotWidthPx = dotWidth.toPx()
            val dotHeightPx = dotHeight.toPx()
            val spacingPx = spacing.toPx()

            // Draw background dots
            for (i in 0 until pageCount) {
                val x = i * (dotWidthPx + spacingPx)
                drawRoundRect(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    topLeft = Offset(x, 0f),
                    size = Size(dotWidthPx, dotHeightPx),
                    cornerRadius = CornerRadius(dotHeightPx / 2)
                )
            }

            // Draw animated worm (stretching pill)
            val wormWidth = dotWidthPx + (pageOffset.absoluteValue * (dotWidthPx + spacingPx * 2)).coerceIn(0f, dotWidthPx + spacingPx * 2)
            drawRoundRect(
                color = Primary,
                topLeft = Offset(animatedOffset, 0f),
                size = Size(wormWidth.coerceAtLeast(dotWidthPx), dotHeightPx),
                cornerRadius = CornerRadius(dotHeightPx / 2)
            )
        }
    }
}

// ==========================================
// CUSTOM CANVAS ILLUSTRATIONS
// ==========================================
@Composable
private fun OnboardingIllustration(
    pageIndex: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) / 2.5f

        when (pageIndex) {
            0 -> drawStudyPartners(center, radius, color)
            1 -> drawProjectTeams(center, radius, color)
            2 -> drawChatBubbles(center, radius, color)
            3 -> drawStreakFlame(center, radius, color)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStudyPartners(
    center: Offset,
    radius: Float,
    color: Color
) {
    val dotRadius = radius * 0.25f
    val positions = listOf(
        Offset(center.x, center.y - radius * 0.8f),
        Offset(center.x - radius * 0.7f, center.y + radius * 0.5f),
        Offset(center.x + radius * 0.7f, center.y + radius * 0.5f)
    )

    // Draw connecting lines
    val linePath = Path().apply {
        moveTo(positions[0].x, positions[0].y)
        lineTo(positions[1].x, positions[1].y)
        lineTo(positions[2].x, positions[2].y)
        close()
    }
    drawPath(
        path = linePath,
        color = color.copy(alpha = 0.15f),
        style = Stroke(width = 3f)
    )

    // Draw connecting lines as arcs
    for (i in positions.indices) {
        for (j in i + 1 until positions.size) {
            drawLine(
                color = color.copy(alpha = 0.2f),
                start = positions[i],
                end = positions[j],
                strokeWidth = 2f
            )
        }
    }

    // Draw person 1 (top)
    drawCircle(color = color.copy(alpha = 0.9f), radius = dotRadius, center = positions[0])
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = dotRadius * 0.35f, center = positions[0])

    // Draw person 2 (bottom-left)
    drawCircle(color = color.copy(alpha = 0.7f), radius = dotRadius * 0.85f, center = positions[1])
    drawCircle(color = Color.White.copy(alpha = 0.7f), radius = dotRadius * 0.3f, center = positions[1])

    // Draw person 3 (bottom-right)
    drawCircle(color = color.copy(alpha = 0.5f), radius = dotRadius * 0.7f, center = positions[2])
    drawCircle(color = Color.White.copy(alpha = 0.5f), radius = dotRadius * 0.25f, center = positions[2])

    // Plus icon in center
    drawCircle(color = color.copy(alpha = 0.2f), radius = dotRadius * 0.6f, center = center)
    drawLine(
        color = color.copy(alpha = 0.4f),
        start = Offset(center.x - dotRadius * 0.25f, center.y),
        end = Offset(center.x + dotRadius * 0.25f, center.y),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = 0.4f),
        start = Offset(center.x, center.y - dotRadius * 0.25f),
        end = Offset(center.x, center.y + dotRadius * 0.25f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProjectTeams(
    center: Offset,
    radius: Float,
    color: Color
) {
    val boxWidth = radius * 1.2f
    val boxHeight = radius * 0.9f
    val boxTopLeft = Offset(center.x - boxWidth / 2, center.y - boxHeight / 2)

    // Monitor/Window frame
    drawRoundRect(
        color = color.copy(alpha = 0.15f),
        topLeft = boxTopLeft,
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(radius * 0.1f)
    )

    // Window title bar
    drawRect(
        color = color.copy(alpha = 0.3f),
        topLeft = boxTopLeft,
        size = Size(boxWidth, boxHeight * 0.15f)
    )

    // Window buttons
    val btnRadius = boxHeight * 0.04f
    drawCircle(Color(0xFFFF5F56), radius = btnRadius, center = Offset(boxTopLeft.x + boxWidth * 0.08f, boxTopLeft.y + boxHeight * 0.075f))
    drawCircle(Color(0xFFFFBD2E), radius = btnRadius, center = Offset(boxTopLeft.x + boxWidth * 0.16f, boxTopLeft.y + boxHeight * 0.075f))
    drawCircle(Color(0xFF27C93F), radius = btnRadius, center = Offset(boxTopLeft.x + boxWidth * 0.24f, boxTopLeft.y + boxHeight * 0.075f))

    // Code lines
    val codeStartX = boxTopLeft.x + boxWidth * 0.12f
    val codeStartY = boxTopLeft.y + boxHeight * 0.25f
    val lineHeight = boxHeight * 0.12f

    // Line 1
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(codeStartX, codeStartY),
        end = Offset(codeStartX + boxWidth * 0.6f, codeStartY),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    // Line 2 (indented)
    drawLine(
        color = color.copy(alpha = 0.35f),
        start = Offset(codeStartX + boxWidth * 0.08f, codeStartY + lineHeight),
        end = Offset(codeStartX + boxWidth * 0.7f, codeStartY + lineHeight),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    // Line 3 (indented more)
    drawLine(
        color = color.copy(alpha = 0.25f),
        start = Offset(codeStartX + boxWidth * 0.16f, codeStartY + lineHeight * 2),
        end = Offset(codeStartX + boxWidth * 0.5f, codeStartY + lineHeight * 2),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    // Line 4 (indented less)
    drawLine(
        color = color.copy(alpha = 0.4f),
        start = Offset(codeStartX, codeStartY + lineHeight * 3),
        end = Offset(codeStartX + boxWidth * 0.55f, codeStartY + lineHeight * 3),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Two small people icons at bottom
    val personSize = boxHeight * 0.2f
    val personY = boxTopLeft.y + boxHeight + personSize * 1.2f

    // Person 1
    drawCircle(color.copy(alpha = 0.6f), radius = personSize * 0.35f, center = Offset(center.x - personSize * 0.8f, personY - personSize * 0.2f))
    drawCircle(color.copy(alpha = 0.3f), radius = personSize * 0.55f, center = Offset(center.x - personSize * 0.8f, personY + personSize * 0.1f))

    // Person 2
    drawCircle(color.copy(alpha = 0.4f), radius = personSize * 0.3f, center = Offset(center.x + personSize * 0.8f, personY - personSize * 0.15f))
    drawCircle(color.copy(alpha = 0.2f), radius = personSize * 0.5f, center = Offset(center.x + personSize * 0.8f, personY + personSize * 0.15f))

    // Connecting dashed line between them
    for (i in 0 until 3) {
        val t = i.toFloat() / 3f
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 2f,
            center = Offset(
                center.x - personSize * 0.8f + (personSize * 1.6f) * t,
                personY
            )
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChatBubbles(
    center: Offset,
    radius: Float,
    color: Color
) {
    val bubbleWidth = radius * 0.9f
    val bubbleHeight = radius * 0.55f
    val cornerRadius = CornerRadius(radius * 0.15f)

    // First bubble (left, slightly smaller)
    val bubble1TopLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.2f)
    drawRoundRect(
        color = color.copy(alpha = 0.2f),
        topLeft = bubble1TopLeft,
        size = Size(bubbleWidth * 0.85f, bubbleHeight * 0.85f),
        cornerRadius = cornerRadius
    )
    // Tail
    drawPath(
        path = Path().apply {
            moveTo(bubble1TopLeft.x + bubbleWidth * 0.7f, bubble1TopLeft.y + bubbleHeight * 0.85f)
            lineTo(bubble1TopLeft.x + bubbleWidth * 0.5f, bubble1TopLeft.y + bubbleHeight * 1.15f)
            lineTo(bubble1TopLeft.x + bubbleWidth * 0.85f, bubble1TopLeft.y + bubbleHeight * 0.85f)
            close()
        },
        color = color.copy(alpha = 0.2f)
    )

    // Ellipsis in first bubble
    for (i in 0 until 3) {
        drawCircle(
            color = color.copy(alpha = 0.4f),
            radius = 3f,
            center = Offset(
                bubble1TopLeft.x + bubbleWidth * 0.2f + i * bubbleWidth * 0.18f,
                bubble1TopLeft.y + bubbleHeight * 0.45f
            )
        )
    }

    // Second bubble (right, larger)
    val bubble2TopLeft = Offset(center.x + radius * 0.1f, center.y - radius * 0.5f)
    drawRoundRect(
        color = color.copy(alpha = 0.5f),
        topLeft = bubble2TopLeft,
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = cornerRadius
    )
    // Tail
    drawPath(
        path = Path().apply {
            moveTo(bubble2TopLeft.x + bubbleWidth * 0.3f, bubble2TopLeft.y + bubbleHeight * 0.85f)
            lineTo(bubble2TopLeft.x + bubbleWidth * 0.5f, bubble2TopLeft.y + bubbleHeight * 1.15f)
            lineTo(bubble2TopLeft.x + bubbleWidth * 0.15f, bubble2TopLeft.y + bubbleHeight * 0.85f)
            close()
        },
        color = color.copy(alpha = 0.5f)
    )

    // Lines in second bubble
    drawLine(
        color = color.copy(alpha = 0.7f),
        start = Offset(bubble2TopLeft.x + bubbleWidth * 0.12f, bubble2TopLeft.y + bubbleHeight * 0.3f),
        end = Offset(bubble2TopLeft.x + bubbleWidth * 0.8f, bubble2TopLeft.y + bubbleHeight * 0.3f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = 0.5f),
        start = Offset(bubble2TopLeft.x + bubbleWidth * 0.12f, bubble2TopLeft.y + bubbleHeight * 0.5f),
        end = Offset(bubble2TopLeft.x + bubbleWidth * 0.6f, bubble2TopLeft.y + bubbleHeight * 0.5f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color.copy(alpha = 0.35f),
        start = Offset(bubble2TopLeft.x + bubbleWidth * 0.12f, bubble2TopLeft.y + bubbleHeight * 0.7f),
        end = Offset(bubble2TopLeft.x + bubbleWidth * 0.45f, bubble2TopLeft.y + bubbleHeight * 0.7f),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Small decorative dots scattered around
    val dotPositions = listOf(
        Offset(center.x + radius * 0.5f, center.y - radius * 0.9f),
        Offset(center.x - radius * 0.3f, center.y - radius * 0.8f),
        Offset(center.x + radius, center.y + radius * 0.1f),
        Offset(center.x - radius * 0.9f, center.y + radius * 0.3f)
    )
    dotPositions.forEach { pos ->
        drawCircle(color.copy(alpha = 0.15f), radius = 4f, center = pos)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStreakFlame(
    center: Offset,
    radius: Float,
    color: Color
) {
    // Draw a stylized flame using paths
    val flamePath = Path().apply {
        // Start at bottom
        moveTo(center.x - radius * 0.35f, center.y + radius * 0.7f)
        // Left curve up
        cubicTo(
            center.x - radius * 0.5f, center.y + radius * 0.2f,
            center.x - radius * 0.2f, center.y - radius * 0.3f,
            center.x, center.y - radius * 0.6f
        )
        // Right curve down
        cubicTo(
            center.x + radius * 0.2f, center.y - radius * 0.3f,
            center.x + radius * 0.5f, center.y + radius * 0.2f,
            center.x + radius * 0.35f, center.y + radius * 0.7f
        )
        // Bottom curve
        cubicTo(
            center.x + radius * 0.25f, center.y + radius * 0.8f,
            center.x - radius * 0.25f, center.y + radius * 0.8f,
            center.x - radius * 0.35f, center.y + radius * 0.7f
        )
        close()
    }

    // Outer flame (larger, more transparent)
    drawPath(
        path = flamePath,
        color = color.copy(alpha = 0.3f)
    )

    // Inner flame (smaller, brighter)
    val innerFlame = Path().apply {
        moveTo(center.x - radius * 0.2f, center.y + radius * 0.4f)
        cubicTo(
            center.x - radius * 0.3f, center.y + radius * 0.1f,
            center.x - radius * 0.1f, center.y - radius * 0.15f,
            center.x, center.y - radius * 0.35f
        )
        cubicTo(
            center.x + radius * 0.1f, center.y - radius * 0.15f,
            center.x + radius * 0.3f, center.y + radius * 0.1f,
            center.x + radius * 0.2f, center.y + radius * 0.4f
        )
        cubicTo(
            center.x + radius * 0.15f, center.y + radius * 0.5f,
            center.x - radius * 0.15f, center.y + radius * 0.5f,
            center.x - radius * 0.2f, center.y + radius * 0.4f
        )
        close()
    }

    drawPath(
        path = innerFlame,
        color = color.copy(alpha = 0.6f)
    )

    // Highlight/glow at center
    drawCircle(
        color = Color.White.copy(alpha = 0.4f),
        radius = radius * 0.12f,
        center = Offset(center.x, center.y - radius * 0.15f)
    )

    // Base/hearth
    drawRoundRect(
        color = color.copy(alpha = 0.2f),
        topLeft = Offset(center.x - radius * 0.4f, center.y + radius * 0.55f),
        size = Size(radius * 0.8f, radius * 0.2f),
        cornerRadius = CornerRadius(radius * 0.1f)
    )

    // Fire particles (small dots)
    val particles = listOf(
        Offset(center.x - radius * 0.3f, center.y - radius * 0.1f),
        Offset(center.x + radius * 0.25f, center.y + radius * 0.05f),
        Offset(center.x - radius * 0.1f, center.y - radius * 0.45f),
        Offset(center.x + radius * 0.35f, center.y - radius * 0.2f)
    )
    particles.forEach { pos ->
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 3f,
            center = pos
        )
    }

    // Streak count circle
    drawCircle(
        color = Color.White,
        radius = radius * 0.18f,
        center = Offset(center.x + radius * 0.55f, center.y + radius * 0.4f)
    )
    drawCircle(
        color = color.copy(alpha = 0.8f),
        radius = radius * 0.16f,
        center = Offset(center.x + radius * 0.55f, center.y + radius * 0.4f)
    )
}


