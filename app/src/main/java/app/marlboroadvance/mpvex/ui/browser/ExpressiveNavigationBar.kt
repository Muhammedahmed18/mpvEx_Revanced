package app.marlboroadvance.mpvex.ui.browser

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A custom floating navigation bar that implements a liquified glassmorphism design.
 * Features translucency, blur (on Android 12+), and a subtle border for a minimal look.
 */
@Composable
fun ExpressiveNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isAtLeastS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val surfaceColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)

    // Glassmorphism colors - high transparency on modern Android
    val glassAlpha = if (isAtLeastS) 0.75f else 0.95f
    val glassColor = surfaceColor.copy(alpha = glassAlpha)

    Box(
        modifier = modifier
            .height(84.dp) // Increased height for vertical layout
            .fillMaxWidth()
            .clip(RoundedCornerShape(42.dp))
    ) {
        // Blur Layer (Background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isAtLeastS) Modifier.blur(25.dp) else Modifier
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glassColor.copy(alpha = (glassAlpha + 0.05f).coerceAtMost(1f)),
                            glassColor,
                            glassColor.copy(alpha = (glassAlpha - 0.02f).coerceAtLeast(0f))
                        )
                    )
                )
        )

        // Main surface
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            shape = RoundedCornerShape(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }

        // Refined Border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.02f),
                            Color.Black.copy(alpha = 0.05f)
                        )
                    ),
                    shape = RoundedCornerShape(42.dp)
                )
        )
    }
}

/**
 * An item for the [ExpressiveNavigationBar].
 * Displays label below the icon with a minimal separator when selected.
 */
@Composable
fun RowScope.ExpressiveNavigationBarItem(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "content_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, 
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Minimal separator line
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(contentColor.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            fontSize = 11.sp
                        ),
                        color = contentColor,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
