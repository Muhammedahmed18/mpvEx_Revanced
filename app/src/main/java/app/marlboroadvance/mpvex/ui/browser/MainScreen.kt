package app.marlboroadvance.mpvex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager
import kotlinx.serialization.Serializable

@Serializable
object MainScreen : Screen {
  // Use a companion object to store state more persistently
  private var persistentSelectedTab: Int = 0
  
  // Shared state using Compose states for instant reactivity
  private val isInSelectionModeState = mutableStateOf(false)
  private val shouldHideNavigationBarState = mutableStateOf(false)
  private val isPermissionDeniedState = mutableStateOf(false)
  private val sharedVideoSelectionManagerState: MutableState<Any?> = mutableStateOf(null)
  
  // Check if the selection contains only videos and update navigation bar visibility accordingly
  private var onlyVideosSelected: Boolean = false
  
  /**
   * Update selection state and navigation bar visibility
   * This method should be called whenever selection changes
   */
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    isInSelectionModeState.value = isInSelectionMode
    onlyVideosSelected = isOnlyVideosSelected
    sharedVideoSelectionManagerState.value = selectionManager
    
    // Only hide navigation bar when videos are selected AND in selection mode
    shouldHideNavigationBarState.value = isInSelectionMode && isOnlyVideosSelected
  }
  
  /**
   * Update permission state to control FAB visibility
   */
  fun updatePermissionState(isDenied: Boolean) {
    isPermissionDeniedState.value = isDenied
  }

  /**
   * Get current permission denied state
   */
  fun getPermissionDeniedState(): Boolean = isPermissionDeniedState.value

  /**
   * Update bottom navigation bar visibility based on floating bottom bar state
   */
  fun updateBottomBarVisibility(shouldShow: Boolean) {
    // Hide bottom navigation when floating bottom bar is visible
    shouldHideNavigationBarState.value = !shouldShow
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember {
      mutableIntStateOf(persistentSelectedTab)
    }

    val context = LocalContext.current
    val density = LocalDensity.current

    // Observe shared states directly
    val isInSelectionMode by isInSelectionModeState
    val hideNavigationBar by shouldHideNavigationBarState
    val permissionDenied by isPermissionDeniedState
    
    // Update persistent state whenever tab changes
    LaunchedEffect(selectedTab) {
      persistentSelectedTab = selectedTab
    }

    // Scaffold with bottom navigation bar
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) { _ ->
      Box(modifier = Modifier.fillMaxSize()) {
        // Space for navigation bar - 0 padding when permission is denied or bar is hidden
        val fabBottomPadding = if (permissionDenied || hideNavigationBar) 0.dp else 120.dp

        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            // Material 3 Expressive slide-in-fade animation (like Google Phone app)
            val slideDistance = with(density) { 48.dp.roundToPx() }
            val animationDuration = 250
            
            if (targetState > initialState) {
              // Moving forward: slide in from right with fade
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { -slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            } else {
              // Moving backward: slide in from left with fade
              (slideInHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                initialOffsetX = { -slideDistance }
              ) + fadeIn(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                )
              )) togetherWith (slideOutHorizontally(
                animationSpec = tween(
                  durationMillis = animationDuration,
                  easing = FastOutSlowInEasing
                ),
                targetOffsetX = { slideDistance }
              ) + fadeOut(
                animationSpec = tween(
                  durationMillis = animationDuration / 2,
                  easing = FastOutSlowInEasing
                )
              ))
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          CompositionLocalProvider(
            LocalNavigationBarHeight provides fabBottomPadding
          ) {
            when (targetTab) {
              0 -> FolderListScreen.Content()
              1 -> RecentlyPlayedScreen.Content()
              2 -> PlaylistScreen.Content()
              3 -> NetworkStreamingScreen.Content()
            }
          }
        }

        // Custom floating navigation bar
        // Only render the navbar container if permission is NOT denied
        if (!permissionDenied) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
          ) {
            AnimatedVisibility(
              visible = !hideNavigationBar,
              enter = slideInVertically(
                animationSpec = tween(durationMillis = 300),
                initialOffsetY = { it }
              ) + fadeIn(),
              exit = slideOutVertically(
                animationSpec = tween(durationMillis = 300),
                targetOffsetY = { it }
              ) + fadeOut()
            ) {
              ExpressiveNavigationBar {
                ExpressiveNavigationBarItem(
                  selectedIcon = Icons.Filled.Home,
                  unselectedIcon = Icons.Outlined.Home,
                  label = "Home",
                  selected = selectedTab == 0,
                  onClick = { selectedTab = 0 }
                )
                ExpressiveNavigationBarItem(
                  selectedIcon = Icons.Filled.History,
                  unselectedIcon = Icons.Outlined.History,
                  label = "Recents",
                  selected = selectedTab == 1,
                  onClick = { selectedTab = 1 }
                )
                ExpressiveNavigationBarItem(
                  selectedIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                  unselectedIcon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                  label = "Playlists",
                  selected = selectedTab == 2,
                  onClick = { selectedTab = 2 }
                )
                ExpressiveNavigationBarItem(
                  selectedIcon = Icons.Filled.Language,
                  unselectedIcon = Icons.Outlined.Language,
                  label = "Network",
                  selected = selectedTab == 3,
                  onClick = { selectedTab = 3 }
                )
              }
            }
          }
        }
      }
    }
  }
}

// CompositionLocal for navigation bar height
val LocalNavigationBarHeight = compositionLocalOf { 0.dp }
