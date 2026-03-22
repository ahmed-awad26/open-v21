package com.opencontacts.core.ui.fastscroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Immutable
data class AlphabetFastScrollerStyle(
    val visualWidthDp: Float = 12f,
    val touchWidthDp: Float = 28f,
    val bubbleSizeDp: Float = 52f,
    val itemMinHeightDp: Float = 10f,
    val sidePaddingDp: Float = 4f,
    val bubbleOffsetXDp: Float = -56f,
)

@Composable
fun BoxScope.AlphabetFastScroller(
    index: AlphabetIndex,
    activeLetter: Char,
    onLetterChanged: (Char) -> Unit,
    modifier: Modifier = Modifier,
    style: AlphabetFastScrollerStyle = AlphabetFastScrollerStyle(),
) {
    val sections = AlphabetSections.ALL
    val sidebarHeightPx = remember { mutableFloatStateOf(1f) }
    val dragY = remember { mutableFloatStateOf(0f) }
    val isDragging = remember { mutableStateOf(false) }
    val currentDragLetter = remember { mutableStateOf(activeLetter) }

    Box(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .padding(end = style.sidePaddingDp.dp)
            .width(style.touchWidthDp.dp)
            .fillMaxHeight()
            .wrapContentSize(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(style.visualWidthDp.dp)
                .heightIn(min = (sections.size * style.itemMinHeightDp).dp)
                .fillMaxHeight(0.78f)
                .onSizeChanged { sidebarHeightPx.floatValue = it.height.toFloat() }
                .pointerInput(index) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging.value = true
                            dragY.floatValue = offset.y
                            val letter = AlphabetTouchMapper.mapYToLetter(offset.y, sidebarHeightPx.floatValue, sections)
                            currentDragLetter.value = letter
                            onLetterChanged(letter)
                        },
                        onDrag = { change, _ ->
                            dragY.floatValue = change.position.y
                            val letter = AlphabetTouchMapper.mapYToLetter(change.position.y, sidebarHeightPx.floatValue, sections)
                            if (letter != currentDragLetter.value) {
                                currentDragLetter.value = letter
                                onLetterChanged(letter)
                            }
                        },
                        onDragEnd = { isDragging.value = false },
                        onDragCancel = { isDragging.value = false },
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                sections.forEach { letter ->
                    val enabled = index.hasSection(letter)
                    val isActive = (if (isDragging.value) currentDragLetter.value else activeLetter) == letter
                    val alpha by animateFloatAsState(
                        targetValue = when {
                            isActive -> 1f
                            enabled -> 0.82f
                            else -> 0.28f
                        }, label = "letterAlpha"
                    )
                    Text(
                        text = letter.toString(),
                        modifier = Modifier.padding(vertical = 1.dp).alpha(alpha),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isDragging.value,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = style.bubbleOffsetXDp.dp)
        ) {
            val bubbleY by animateFloatAsState(
                targetValue = dragY.floatValue.coerceIn(0f, sidebarHeightPx.floatValue) - (style.bubbleSizeDp.dp.toPx() / 2f),
                label = "bubbleY"
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, bubbleY.roundToInt()) }
                    .width(style.bubbleSizeDp.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = currentDragLetter.value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
