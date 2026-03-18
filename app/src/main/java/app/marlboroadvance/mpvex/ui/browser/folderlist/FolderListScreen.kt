package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.BrowserPreferences
import app.marlboroadvance.mpvex.preferences.FolderSortType
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.SortOrder
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.MainScreen
import app.marlboroadvance.mpvex.ui.browser.cards.FolderCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.RenameDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.SortDialog
import app.marlboroadvance.mpvex.ui.browser.dialogs.VisibilityToggle
import app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.browser.videolist.VideoListScreen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.media.MediaUtils
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import app.marlboroadvance.mpvex.utils.sort.SortUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject

@Serializable
object FolderListScreen : Screen {
  @OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backstack = LocalBackStack.current
    val browserPreferences = koinInject<BrowserPreferences>()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val viewModel: FolderListViewModel =
      viewModel(
        factory = FolderListViewModel.factory(context.applicationContext as android.app.Application),
      )
    val videoFolders by viewModel.videoFolders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val permissionState = PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() }
    )

    // Update MainScreen permission state to control navigation bar visibility
    LaunchedEffect(permissionState.status) {
      MainScreen.updatePermissionState(!permissionState.status.isGranted)
    }

    val folderSortType by browserPreferences.folderSortType.collectAsState()
    val folderSortOrder by browserPreferences.folderSortOrder.collectAsState()
    val sortedFolders =
      remember(videoFolders, folderSortType, folderSortOrder) {
        SortUtils.sortFolders(videoFolders, folderSortType, folderSortOrder)
      }

    val selectionManager =
      rememberSelectionManager(
        items = sortedFolders,
        getId = { it.bucketId },
        onDeleteItems = { foldersToDelete, _ ->
           viewModel.deleteFolders(foldersToDelete)
        },
        onOperationComplete = { viewModel.refresh() },
      )

    val isRefreshing = remember { mutableStateOf(false) }
    val sortDialogOpen = rememberSaveable { mutableStateOf(false) }
    val deleteDialogOpen = rememberSaveable { mutableStateOf(false) }
    val renameDialogOpen = rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isFabVisible = remember { mutableStateOf(true) }
    val isFabExpanded = remember { mutableStateOf(false) }

    // Floating bottom bar is no longer used for folders as per request
    val showFloatingBottomBar = false

    BackHandler(enabled = selectionManager.isInSelectionMode) {
      selectionManager.clear()
    }

    DisposableEffect(lifecycleOwner) {
      val observer =
        LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.refresh()
          }
        }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = null,
      isFabVisible = isFabVisible,
      expanded = isFabExpanded.value,
      onExpandedChange = { isFabExpanded.value = it },
    )

    Scaffold(
      topBar = {
        BrowserTopBar(
          title = stringResource(app.marlboroadvance.mpvex.R.string.app_name),
          isInSelectionMode = selectionManager.isInSelectionMode,
          selectedCount = selectionManager.selectedCount,
          totalCount = videoFolders.size,
          onBackClick = null,
          onCancelSelection = { selectionManager.clear() },
          onSortClick = { sortDialogOpen.value = true },
          onSearchClick = null,
          onSettingsClick = {
            backstack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
          },
          onDeleteClick = { deleteDialogOpen.value = true },
          onRenameClick = { renameDialogOpen.value = true },
          isSingleSelection = selectionManager.isSingleSelection,
          onInfoClick = null,
          onShareClick = {
            coroutineScope.launch {
              val selectedFolders = selectionManager.getSelectedItems()
              val videosFromFolders = mutableListOf<app.marlboroadvance.mpvex.domain.media.model.Video>()
              // We'd need a way to get videos from folders here if we wanted to support sharing folders
              // For now, keep it simple as the primary request was rename/hide dock.
              selectionManager.clear()
            }
          },
          onPlayClick = {
            coroutineScope.launch {
              selectionManager.clear()
            }
          },
          onSelectAll = { selectionManager.selectAll() },
          onInvertSelection = { selectionManager.invertSelection() },
          onDeselectAll = { selectionManager.clear() },
        )
      },
    ) { padding ->
      Box(modifier = Modifier.fillMaxSize()) {
        when (permissionState.status) {
          is PermissionStatus.Granted -> {
            FolderListContent(
              folders = sortedFolders,
              isLoading = isLoading && videoFolders.isEmpty(),
              isRefreshing = isRefreshing,
              onRefresh = { viewModel.refresh() },
              selectionManager = selectionManager,
              onFolderClick = { folder ->
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(folder)
                } else {
                  backstack.add(VideoListScreen(folder.bucketId, folder.name))
                }
              },
              onFolderLongClick = { folder -> selectionManager.toggle(folder) },
              listState = listState,
              modifier = Modifier.padding(padding),
              showFloatingBottomBar = showFloatingBottomBar,
            )
          }
          is PermissionStatus.Denied -> {
            PermissionDeniedState(
              onRequestPermission = { permissionState.launchPermissionRequest() },
              modifier = Modifier.padding(padding),
            )
          }
        }
      }

      FolderSortDialog(
        isOpen = sortDialogOpen.value,
        onDismiss = { sortDialogOpen.value = false },
        sortType = folderSortType,
        sortOrder = folderSortOrder,
        onSortTypeChange = { browserPreferences.folderSortType.set(it) },
        onSortOrderChange = { browserPreferences.folderSortOrder.set(it) },
      )

      DeleteConfirmationDialog(
        isOpen = deleteDialogOpen.value,
        onDismiss = { deleteDialogOpen.value = false },
        onConfirm = { selectionManager.deleteSelected() },
        itemType = "folder",
        itemCount = selectionManager.selectedCount,
        itemNames = selectionManager.getSelectedItems().map { it.name },
      )

      if (renameDialogOpen.value && selectionManager.isSingleSelection) {
        val folder = selectionManager.getSelectedItems().firstOrNull()
        if (folder != null) {
          RenameDialog(
            isOpen = true,
            onDismiss = { renameDialogOpen.value = false },
            onConfirm = { newName ->
              viewModel.renameFolder(folder, newName)
              selectionManager.clear()
              renameDialogOpen.value = false
            },
            currentName = folder.name,
            itemType = "folder",
          )
        }
      }
    }
  }
}

@Composable
private fun FolderListContent(
  folders: List<VideoFolder>,
  isLoading: Boolean,
  isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
  onRefresh: suspend () -> Unit,
  selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<VideoFolder, String>,
  onFolderClick: (VideoFolder) -> Unit,
  onFolderLongClick: (VideoFolder) -> Unit,
  listState: androidx.compose.foundation.lazy.LazyListState,
  modifier: Modifier = Modifier,
  showFloatingBottomBar: Boolean = false,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val tapThumbnailToSelect by gesturePreferences.tapThumbnailToSelect.collectAsState()
  val navigationBarHeight = app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current

  when {
    isLoading && folders.isEmpty() -> {
      Box(
        modifier = modifier
          .fillMaxSize()
          .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(48.dp),
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    folders.isEmpty() && !isLoading -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        EmptyState(
          icon = Icons.Filled.Folder,
          title = "No folders found",
          message = "Folders with videos will appear here",
        )
      }
    }

    else -> {
      val isAtTop by remember {
        derivedStateOf {
          listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
      }

      val hasEnoughItems = folders.size > 20

      val scrollbarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isAtTop || !hasEnoughItems) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollbarAlpha",
      )

      PullRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        listState = listState,
        modifier = modifier.fillMaxSize(),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = navigationBarHeight)
        ) {
          LazyColumnScrollbar(
            state = listState,
            settings = ScrollbarSettings(
              thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f * scrollbarAlpha),
              thumbSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = scrollbarAlpha),
            ),
          ) {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                bottom = if (showFloatingBottomBar) 88.dp else 16.dp
              ),
            ) {
              items(
                items = folders,
                key = { it.bucketId },
              ) { folder ->
                FolderCard(
                  folder = folder,
                  isSelected = selectionManager.isSelected(folder),
                  isRecentlyPlayed = false,
                  onClick = { onFolderClick(folder) },
                  onLongClick = { onFolderLongClick(folder) },
                  onThumbClick = if (tapThumbnailToSelect) {
                    { onFolderLongClick(folder) }
                  } else {
                    { onFolderClick(folder) }
                  },
                  isGridMode = false,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FolderSortDialog(
  isOpen: Boolean,
  onDismiss: () -> Unit,
  sortType: FolderSortType,
  sortOrder: SortOrder,
  onSortTypeChange: (FolderSortType) -> Unit,
  onSortOrderChange: (SortOrder) -> Unit,
) {
  val browserPreferences = koinInject<BrowserPreferences>()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val showTotalVideosChip by browserPreferences.showTotalVideosChip.collectAsState()
  val showTotalDurationChip by browserPreferences.showTotalDurationChip.collectAsState()
  val showTotalSizeChip by browserPreferences.showTotalSizeChip.collectAsState()
  val showDateChip by browserPreferences.showDateChip.collectAsState()
  val showFolderPath by browserPreferences.showFolderPath.collectAsState()
  val unlimitedNameLines by appearancePreferences.unlimitedNameLines.collectAsState()

  SortDialog(
    isOpen = isOpen,
    onDismiss = onDismiss,
    title = "Sort & View Options",
    sortType = sortType.displayName,
    onSortTypeChange = { typeName ->
      FolderSortType.entries.find { it.displayName == typeName }?.let(onSortTypeChange)
    },
    sortOrderAsc = sortOrder.isAscending,
    onSortOrderChange = { isAsc ->
      onSortOrderChange(if (isAsc) SortOrder.Ascending else SortOrder.Descending)
    },
    types =
      listOf(
        FolderSortType.Title.displayName,
        FolderSortType.Date.displayName,
        FolderSortType.Size.displayName,
      ),
    icons =
      listOf(
        Icons.Filled.Title,
        Icons.Filled.CalendarToday,
        Icons.Filled.SwapVert,
      ),
    getLabelForType = { type, _ ->
      when (type) {
        FolderSortType.Title.displayName -> Pair("A-Z", "Z-A")
        FolderSortType.Date.displayName -> Pair("Oldest", "Newest")
        FolderSortType.Size.displayName -> Pair("Smallest", "Largest")
        else -> Pair("Asc", "Desc")
      }
    },
    visibilityToggles =
      listOf(
        VisibilityToggle(
          label = "Full Name",
          checked = unlimitedNameLines,
          onCheckedChange = { appearancePreferences.unlimitedNameLines.set(it) },
        ),
        VisibilityToggle(
          label = "Path",
          checked = showFolderPath,
          onCheckedChange = { browserPreferences.showFolderPath.set(it) },
        ),
        VisibilityToggle(
          label = "Total Videos",
          checked = showTotalVideosChip,
          onCheckedChange = { browserPreferences.showTotalVideosChip.set(it) },
        ),
        VisibilityToggle(
          label = "Total Duration",
          checked = showTotalDurationChip,
          onCheckedChange = { browserPreferences.showTotalDurationChip.set(it) },
        ),
        VisibilityToggle(
          label = "Folder Size",
          checked = showTotalSizeChip,
          onCheckedChange = { browserPreferences.showTotalSizeChip.set(it) },
        ),
        VisibilityToggle(
          label = "Date",
          checked = showDateChip,
          onCheckedChange = { browserPreferences.showDateChip.set(it) },
        ),
      ),
  )
}
