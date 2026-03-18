package app.marlboroadvance.mpvex.ui.browser.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.pullrefresh.PullRefreshBox
import app.marlboroadvance.mpvex.ui.browser.cards.PlaylistCard
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import app.marlboroadvance.mpvex.ui.browser.dialogs.DeleteConfirmationDialog
import app.marlboroadvance.mpvex.ui.browser.selection.rememberSelectionManager
import app.marlboroadvance.mpvex.ui.browser.sheets.PlaylistActionSheet
import app.marlboroadvance.mpvex.ui.browser.states.EmptyState
import app.marlboroadvance.mpvex.ui.browser.states.PermissionDeniedState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.koinInject

@Serializable
object PlaylistScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalPermissionsApi::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val repository = koinInject<PlaylistRepository>()
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()

    val viewModel: PlaylistViewModel = viewModel(
      factory = PlaylistViewModel.factory(context.applicationContext as android.app.Application),
    )

    val playlistsWithCount by viewModel.playlistsWithCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedInitialLoad by viewModel.hasCompletedInitialLoad.collectAsState()

    val permissionState = PermissionUtils.handleStoragePermission(
      onPermissionGranted = { viewModel.refresh() }
    )

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val filteredPlaylists = if (isSearching && searchQuery.isNotBlank()) {
      playlistsWithCount.filter { playlistWithCount ->
        playlistWithCount.playlist.name.contains(searchQuery, ignoreCase = true)
      }
    } else {
      playlistsWithCount
    }

    LaunchedEffect(isSearching) {
      if (isSearching) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    val selectionManager = rememberSelectionManager(
      items = filteredPlaylists,
      getId = { it.playlist.id },
      onDeleteItems = { itemsToDelete, _ ->
        itemsToDelete.forEach { item ->
          viewModel.deletePlaylist(item.playlist)
        }
        Pair(itemsToDelete.size, 0)
      },
      onOperationComplete = { viewModel.refresh() },
    )

    val listState = remember { LazyListState() }
    val isRefreshing = remember { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPlaylistActionSheet by remember { mutableStateOf(false) }

    val isFabVisible = remember { mutableStateOf(true) }

    BackHandler(enabled = selectionManager.isInSelectionMode || isSearching) {
      when {
        isSearching -> {
          isSearching = false
          searchQuery = ""
        }

        selectionManager.isInSelectionMode -> selectionManager.clear()
      }
    }

    app.marlboroadvance.mpvex.ui.browser.fab.FabScrollHelper.trackScrollForFabVisibility(
      listState = listState,
      gridState = null,
      isFabVisible = isFabVisible,
      expanded = false,
      onExpandedChange = {},
    )

    Scaffold(
      topBar = {
        if (isSearching) {
          SearchBar(
            inputField = {
              SearchBarDefaults.InputField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                expanded = false,
                onExpandedChange = { },
                placeholder = { Text("Search playlists...") },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                  )
                },
                trailingIcon = {
                  IconButton(
                    onClick = {
                      isSearching = false
                      searchQuery = ""
                    },
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Cancel",
                    )
                  }
                },
                modifier = Modifier.focusRequester(focusRequester),
              )
            },
            expanded = false,
            onExpandedChange = { },
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
          ) {
          }
        } else {
          BrowserTopBar(
            title = "Playlists",
            isInSelectionMode = selectionManager.isInSelectionMode,
            selectedCount = selectionManager.selectedCount,
            totalCount = playlistsWithCount.size,
            onBackClick = null,
            onCancelSelection = { selectionManager.clear() },
            isSingleSelection = selectionManager.isSingleSelection,
            onSearchClick = { isSearching = true },
            onSettingsClick = {
              backStack.add(app.marlboroadvance.mpvex.ui.preferences.PreferencesScreen)
            },
            onRenameClick = if (selectionManager.isSingleSelection) {
              { showRenameDialog = true }
            } else null,
            onDeleteClick = { showDeleteDialog = true },
            onSelectAll = { selectionManager.selectAll() },
            onInvertSelection = { selectionManager.invertSelection() },
            onDeselectAll = { selectionManager.clear() },
          )
        }
      },
      floatingActionButton = {
        val navigationBarHeight =
          app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current
        if (!selectionManager.isInSelectionMode && isFabVisible.value && permissionState.status is PermissionStatus.Granted) {
          ExtendedFloatingActionButton(
            onClick = { showPlaylistActionSheet = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Create Playlist") },
            modifier = Modifier.padding(bottom = navigationBarHeight)
          )
        }
      }
    ) { paddingValues ->
      when (permissionState.status) {
        is PermissionStatus.Granted -> {
          if (isSearching && filteredPlaylists.isEmpty() && searchQuery.isNotBlank()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
              contentAlignment = Alignment.Center,
            ) {
              EmptyState(
                icon = Icons.Filled.Search,
                title = "No playlists found",
                message = "Try a different search term",
              )
            }
          } else if (playlistsWithCount.isEmpty() && hasCompletedInitialLoad) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
              ) {
                EmptyState(
                  icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                  title = "No playlists yet",
                  message = "Create a playlist or add one from an m3u URL",
                )
              }
            }
          } else {
            PlaylistListContent(
              playlistsWithCount = filteredPlaylists,
              listState = listState,
              isRefreshing = isRefreshing,
              onRefresh = { viewModel.refresh() },
              selectionManager = selectionManager,
              onPlaylistClick = { playlistWithCount ->
                if (selectionManager.isInSelectionMode) {
                  selectionManager.toggle(playlistWithCount)
                } else {
                  backStack.add(PlaylistDetailScreen(playlistWithCount.playlist.id))
                }
              },
              onPlaylistLongClick = { playlistWithCount ->
                selectionManager.toggle(playlistWithCount)
              },
              modifier = Modifier.padding(paddingValues),
            )
          }
        }
        is PermissionStatus.Denied -> {
          PermissionDeniedState(
            onRequestPermission = { permissionState.launchPermissionRequest() },
            modifier = Modifier.padding(paddingValues),
          )
        }
      }

      PlaylistActionSheet(
        isOpen = showPlaylistActionSheet,
        onDismiss = { showPlaylistActionSheet = false },
        repository = repository,
        context = context,
      )

      if (showRenameDialog && selectionManager.isSingleSelection) {
        val selectedPlaylist = selectionManager.getSelectedItems().firstOrNull()
        if (selectedPlaylist != null) {
          var playlistName by remember { mutableStateOf(selectedPlaylist.playlist.name) }
          androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
              androidx.compose.material3.OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
              )
            },
            confirmButton = {
              androidx.compose.material3.TextButton(
                onClick = {
                  if (playlistName.isNotBlank()) {
                    scope.launch {
                      repository.updatePlaylist(
                        selectedPlaylist.playlist.copy(
                          name = playlistName.trim()
                        )
                      )
                      showRenameDialog = false
                      selectionManager.clear()
                    }
                  }
                },
                enabled = playlistName.isNotBlank(),
              ) {
                Text("Rename")
              }
            },
            dismissButton = {
              androidx.compose.material3.TextButton(
                onClick = { showRenameDialog = false },
              ) {
                Text("Cancel")
              }
            },
          )
        }
      }

      if (showDeleteDialog) {
        DeleteConfirmationDialog(
          isOpen = true,
          onDismiss = { showDeleteDialog = false },
          onConfirm = {
            selectionManager.deleteSelected()
            showDeleteDialog = false
          },
          itemCount = selectionManager.selectedCount,
          itemType = "playlist",
          itemNames = selectionManager.getSelectedItems().map { it.playlist.name },
        )
      }
    }
  }

  @Composable
  private fun PlaylistListContent(
    playlistsWithCount: List<PlaylistWithCount>,
    listState: LazyListState,
    isRefreshing: androidx.compose.runtime.MutableState<Boolean>,
    onRefresh: suspend () -> Unit,
    selectionManager: app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager<PlaylistWithCount, Int>,
    onPlaylistClick: (PlaylistWithCount) -> Unit,
    onPlaylistLongClick: (PlaylistWithCount) -> Unit,
    modifier: Modifier = Modifier,
  ) {
    val isAtTop by remember {
      derivedStateOf {
        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
      }
    }

    val hasEnoughItems = playlistsWithCount.size > 20

    val scrollbarAlpha by animateFloatAsState(
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
      val navigationBarHeight =
        app.marlboroadvance.mpvex.ui.browser.LocalNavigationBarHeight.current
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
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
          ) {
            items(playlistsWithCount, key = { it.playlist.id }) { playlistWithCount ->
              PlaylistCard(
                playlist = playlistWithCount.playlist,
                itemCount = playlistWithCount.itemCount,
                isSelected = selectionManager.isSelected(playlistWithCount),
                onClick = { onPlaylistClick(playlistWithCount) },
                onLongClick = { onPlaylistLongClick(playlistWithCount) },
                onThumbClick = { onPlaylistClick(playlistWithCount) },
                isGridMode = false,
              )
            }
          }
        }
      }
    }
  }
}
