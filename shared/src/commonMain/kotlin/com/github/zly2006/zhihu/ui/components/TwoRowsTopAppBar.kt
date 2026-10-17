/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.fastSumBy
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhihuTwoRowsTopAppBar(
    title: @Composable (expanded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable (expanded: Boolean) -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    collapsedHeight: Dp = Dp.Unspecified,
    expandedHeight: Dp = Dp.Unspecified,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TwoRowsTopAppBar(
        title = { title(true) },
        titleTextStyle = MaterialTheme.typography.headlineMedium,
        titleBottomPadding = 0.dp,
        smallTitle = { title(false) },
        smallTitleTextStyle = MaterialTheme.typography.titleLarge,
        modifier = modifier,
        subtitle = { subtitle?.invoke(true) },
        subtitleTextStyle = MaterialTheme.typography.labelLarge,
        smallSubtitle = { subtitle?.invoke(false) },
        smallSubtitleTextStyle = MaterialTheme.typography.labelMedium,
        titleHorizontalAlignment = titleHorizontalAlignment,
        navigationIcon = navigationIcon,
        actions = actions,
        collapsedHeight =
            if (collapsedHeight == Dp.Unspecified || collapsedHeight == Dp.Infinity) {
                TopAppBarDefaults.MediumAppBarCollapsedHeight
            } else {
                collapsedHeight
            },
        expandedHeight =
            if (expandedHeight == Dp.Unspecified || expandedHeight == Dp.Infinity) {
                if (subtitle != null) {
                    TopAppBarDefaults.MediumFlexibleAppBarWithSubtitleExpandedHeight
                } else {
                    TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight
                }
            } else {
                expandedHeight
            },
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 顶栏折叠量始终等于正文已滚动的距离：顶栏覆盖在正文上方折叠，不先于正文消费滚动，松手后也不吸附，
 * 正文从第一个像素起跟手。调用方需把正文顶部边距固定为顶栏展开高度，避免折叠时每帧重排正文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberContentLinkedScrollBehavior(scrollState: ScrollState): TopAppBarScrollBehavior {
    val state = rememberTopAppBarState()
    // 动画滚动、恢复阅读位置等不经过嵌套滚动；顶栏测出高度后也要按新的上限重新约束。
    LaunchedEffect(scrollState, state) {
        snapshotFlow { scrollState.value to state.heightOffsetLimit }.collect { (value, _) ->
            state.heightOffset = -value.toFloat()
        }
    }
    return remember(scrollState, state) { ContentLinkedScrollBehavior(state, scrollState) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoRowsTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    titleBottomPadding: Dp,
    smallTitle: @Composable () -> Unit,
    smallTitleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    smallSubtitle: (@Composable () -> Unit)?,
    smallSubtitleTextStyle: TextStyle,
    titleHorizontalAlignment: Alignment.Horizontal,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    collapsedHeight: Dp,
    expandedHeight: Dp,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    require(collapsedHeight.isSpecified && collapsedHeight.isFinite) {
        "The collapsedHeight is expected to be specified and finite"
    }
    require(expandedHeight.isSpecified && expandedHeight.isFinite) {
        "The expandedHeight is expected to be specified and finite"
    }
    require(expandedHeight >= collapsedHeight) {
        "The expandedHeight is expected to be greater or equal to the collapsedHeight"
    }

    val titleBottomPaddingPx = with(LocalDensity.current) { titleBottomPadding.roundToPx() }
    val colorTransitionFraction = { scrollBehavior?.state?.collapsedFraction ?: 0f }
    val appBarContainerColor = {
        lerp(
            colors.containerColor,
            colors.scrolledContainerColor,
            FastOutLinearInEasing.transform(colorTransitionFraction()),
        )
    }
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    val topTitleAlpha = { TopTitleAlphaEasing.transform(colorTransitionFraction()) }
    val bottomTitleAlpha = { 1f - colorTransitionFraction() }
    val hideTopRowSemantics by
        remember(colorTransitionFraction) {
            derivedStateOf { colorTransitionFraction() < 0.5f }
        }
    val hideBottomRowSemantics = !hideTopRowSemantics
    Box(
        modifier =
            modifier
                .drawBehind { drawRect(color = appBarContainerColor()) }
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {},
    ) {
        Column {
            TopAppBarLayout(
                modifier =
                    Modifier
                        .windowInsetsPadding(windowInsets)
                        .clipToBounds(),
                scrolledOffset = { 0f },
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                subtitleContentColor = colors.subtitleContentColor,
                title = smallTitle,
                titleTextStyle = smallTitleTextStyle,
                subtitle = smallSubtitle,
                subtitleTextStyle = smallSubtitleTextStyle,
                titleAlpha = topTitleAlpha,
                titleVerticalArrangement = Arrangement.Center,
                titleHorizontalAlignment = titleHorizontalAlignment,
                titleBottomPadding = 0,
                hideTitleSemantics = hideTopRowSemantics,
                navigationIcon = navigationIcon,
                actions = actionsRow,
                height = collapsedHeight,
                contentPadding = TopAppBarDefaults.ContentPadding,
            )
            TopAppBarLayout(
                modifier =
                    Modifier
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                        .clipToBounds()
                        .adjustHeightOffsetLimit(scrollBehavior),
                scrolledOffset = { scrollBehavior?.state?.heightOffset ?: 0f },
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                subtitleContentColor = colors.subtitleContentColor,
                title = title,
                titleTextStyle = titleTextStyle,
                subtitle = subtitle,
                subtitleTextStyle = subtitleTextStyle,
                titleAlpha = bottomTitleAlpha,
                titleVerticalArrangement = Arrangement.Bottom,
                titleHorizontalAlignment = titleHorizontalAlignment,
                titleBottomPadding = titleBottomPaddingPx,
                hideTitleSemantics = hideBottomRowSemantics,
                navigationIcon = {},
                actions = {},
                height = expandedHeight - collapsedHeight,
                contentPadding = TopAppBarDefaults.ContentPadding,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.adjustHeightOffsetLimit(scrollBehavior: TopAppBarScrollBehavior?) =
    scrollBehavior?.state?.let {
        onSizeChanged { size ->
            val offset = size.height.toFloat() - it.heightOffset
            it.heightOffsetLimit = -offset
        }
    } ?: this

@OptIn(ExperimentalMaterial3Api::class)
private class ContentLinkedScrollBehavior(
    override val state: TopAppBarState,
    private val scrollState: ScrollState,
) : TopAppBarScrollBehavior {
    // 折叠量由正文滚动决定，不响应拖动顶栏本身。
    override val isPinned: Boolean = true
    override val snapAnimationSpec: AnimationSpec<Float>? = null
    override val flingAnimationSpec: DecayAnimationSpec<Float>? = null
    override val nestedScrollConnection =
        object : NestedScrollConnection {
            // 在正文消费滚动的同一帧同步；只靠 snapshotFlow 会晚一帧，顶栏与正文错位。
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                state.heightOffset = -scrollState.value.toFloat()
                return Offset.Zero
            }
        }
}

@Composable
private fun TopAppBarLayout(
    modifier: Modifier,
    scrolledOffset: () -> Float,
    navigationIconContentColor: Color,
    titleContentColor: Color,
    subtitleContentColor: Color,
    actionIconContentColor: Color,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    titleAlpha: () -> Float,
    titleVerticalArrangement: Arrangement.Vertical,
    titleHorizontalAlignment: Alignment.Horizontal,
    titleBottomPadding: Int,
    hideTitleSemantics: Boolean,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    height: Dp,
    contentPadding: PaddingValues,
) {
    Layout(
        {
            Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides navigationIconContentColor,
                    content = navigationIcon,
                )
            }
            if (subtitle != null) {
                Column(
                    modifier =
                        Modifier
                            .layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                },
                            ).graphicsLayer { alpha = titleAlpha() },
                    horizontalAlignment = titleHorizontalAlignment,
                ) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(titleTextStyle, title)
                    }
                    CompositionLocalProvider(LocalContentColor provides subtitleContentColor) {
                        ProvideTextStyle(subtitleTextStyle, subtitle)
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                },
                            ).graphicsLayer { alpha = titleAlpha() },
                ) {
                    CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                        ProvideTextStyle(titleTextStyle, title)
                    }
                }
            }
            Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides actionIconContentColor,
                    content = actions,
                )
            }
        },
        modifier = modifier,
        measurePolicy =
            remember(
                scrolledOffset,
                titleVerticalArrangement,
                titleHorizontalAlignment,
                titleBottomPadding,
                height,
            ) {
                TopAppBarMeasurePolicy(
                    scrolledOffset,
                    titleVerticalArrangement,
                    titleHorizontalAlignment,
                    titleBottomPadding,
                    height,
                    contentPadding,
                )
            },
    )
}

private class TopAppBarMeasurePolicy(
    val scrolledOffset: () -> Float,
    val titleVerticalArrangement: Arrangement.Vertical,
    val titleHorizontalAlignment: Alignment.Horizontal,
    val titleBottomPadding: Int,
    val height: Dp,
    val contentPadding: PaddingValues,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0))
        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0))
        val startPaddingPx = contentPadding.calculateStartPadding(layoutDirection)
        val endPaddingPx = contentPadding.calculateEndPadding(layoutDirection)
        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (
                    constraints.maxWidth -
                        navigationIconPlaceable.width -
                        actionIconsPlaceable.width -
                        startPaddingPx.roundToPx() -
                        endPaddingPx.roundToPx()
                ).coerceAtLeast(0)
            }
        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = maxTitleWidth))
        val titleBaseline =
            if (titlePlaceable[LastBaseline] != AlignmentLine.Unspecified) {
                titlePlaceable[LastBaseline]
            } else {
                0
            }
        val scrolledOffsetValue = scrolledOffset()
        val heightOffset = if (scrolledOffsetValue.isNaN()) 0 else scrolledOffsetValue.roundToInt()
        val topPaddingPx = contentPadding.calculateTopPadding().roundToPx()
        val bottomPaddingPx = contentPadding.calculateBottomPadding().roundToPx()
        val maxLayoutHeight =
            max(height.roundToPx(), titlePlaceable.height) + topPaddingPx + bottomPaddingPx
        val layoutHeight =
            if (constraints.maxHeight == Constraints.Infinity) {
                maxLayoutHeight
            } else {
                (maxLayoutHeight + heightOffset).coerceAtLeast(0)
            }

        return placeTopAppBar(
            constraints,
            layoutHeight,
            maxLayoutHeight,
            navigationIconPlaceable,
            titlePlaceable,
            actionIconsPlaceable,
            titleBaseline,
            contentPadding,
        )
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ) = measurables.fastSumBy { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = max(
        height.roundToPx(),
        measurables.fastMaxOfOrNull { it.minIntrinsicHeight(width) } ?: 0,
    )

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ) = measurables.fastSumBy { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = max(
        height.roundToPx(),
        measurables.fastMaxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0,
    )

    private fun MeasureScope.placeTopAppBar(
        constraints: Constraints,
        layoutHeight: Int,
        maxLayoutHeight: Int,
        navigationIconPlaceable: Placeable,
        titlePlaceable: Placeable,
        actionIconsPlaceable: Placeable,
        titleBaseline: Int,
        contentPadding: PaddingValues,
    ): MeasureResult {
        val topPadding = contentPadding.calculateTopPadding().roundToPx()
        val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
        val startPadding = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
        val endPadding = contentPadding.calculateEndPadding(layoutDirection).roundToPx()
        val contentHeight = layoutHeight + topPadding - bottomPadding
        return layout(constraints.maxWidth, layoutHeight) {
            navigationIconPlaceable.placeRelative(
                x = startPadding,
                y = (contentHeight - navigationIconPlaceable.height) / 2,
            )

            val start = max(TopAppBarTitleInset.roundToPx(), navigationIconPlaceable.width)
            val end = actionIconsPlaceable.width
            var titleX =
                titleHorizontalAlignment.align(
                    size = titlePlaceable.width,
                    space = constraints.maxWidth,
                    layoutDirection = LayoutDirection.Ltr,
                )
            if (titleX < start) {
                titleX += startPadding + (start - titleX)
            } else if (titleX + titlePlaceable.width > constraints.maxWidth - end) {
                titleX +=
                    startPadding +
                    ((constraints.maxWidth - end) - (titleX + titlePlaceable.width))
            }

            val titleY =
                when (titleVerticalArrangement) {
                    Arrangement.Center -> (contentHeight - titlePlaceable.height) / 2
                    Arrangement.Bottom ->
                        if (titleBottomPadding == 0) {
                            contentHeight - titlePlaceable.height
                        } else {
                            val paddingFromBottom =
                                titleBottomPadding - (titlePlaceable.height - titleBaseline)
                            val heightWithPadding = paddingFromBottom + titlePlaceable.height
                            val adjustedBottomPadding =
                                if (heightWithPadding > maxLayoutHeight) {
                                    paddingFromBottom - (heightWithPadding - maxLayoutHeight)
                                } else {
                                    paddingFromBottom
                                }

                            contentHeight -
                                titlePlaceable.height -
                                max(0, adjustedBottomPadding)
                        }
                    else -> 0
                }

            titlePlaceable.placeRelative(titleX, titleY)
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width - endPadding,
                y = (contentHeight - actionIconsPlaceable.height) / 2,
            )
        }
    }
}

private val TopTitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)
private val TopAppBarHorizontalPadding = 4.dp
private val TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding
