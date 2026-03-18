package app.marlboroadvance.mpvex.ui.browser.folderlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.VideoFolder
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.preferences.AppearancePreferences
import app.marlboroadvance.mpvex.preferences.FoldersPreferences
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.media.MetadataRetrieval
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

data class FolderWithNewCount(
  val folder: VideoFolder,
  val newVideoCount: Int = 0,
)

class FolderListViewModel(
  application: Application,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val foldersPreferences: FoldersPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()
  private val browserPreferences: app.marlboroadvance.mpvex.preferences.BrowserPreferences by inject()
  private val playbackStateRepository: PlaybackStateRepository by inject()

  // Derived from the master video list in MediaFileRepository
  private val _videoFolders = MutableStateFlow<List<VideoFolder>>(emptyList())
  val videoFolders: StateFlow<List<VideoFolder>> = _videoFolders.asStateFlow()

  private val _foldersWithNewCount = MutableStateFlow<List<FolderWithNewCount>>(emptyList())
  val foldersWithNewCount: StateFlow<List<FolderWithNewCount>> = _foldersWithNewCount.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _hasCompletedInitialLoad = MutableStateFlow(false)
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  private val _scanStatus = MutableStateFlow<String?>(null)
  val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

  private val _isEnriching = MutableStateFlow(false)
  val isEnriching: StateFlow<Boolean> = _isEnriching.asStateFlow()

  companion object {
    private const val TAG = "FolderListViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FolderListViewModel(application) as T
      }
  }

  init {
    // 1. Observe the Master Video List from Repository and derive folders
    viewModelScope.launch {
      combine(
        MediaFileRepository.allVideos,
        foldersPreferences.blacklistedFolders.changes()
      ) { allVideos, blacklist ->
        if (allVideos.isEmpty() && !_hasCompletedInitialLoad.value) {
           return@combine emptyList<VideoFolder>()
        }

        // Group videos by folder (bucketId)
        val derivedFolders = allVideos
          .groupBy { it.bucketId }
          .map { (bucketId, videos) ->
            val firstVideo = videos.first()
            VideoFolder(
              bucketId = bucketId,
              name = firstVideo.bucketDisplayName,
              path = bucketId,
              videoCount = videos.size,
              totalSize = videos.sumOf { it.size },
              totalDuration = videos.sumOf { it.duration },
              lastModified = videos.maxOfOrNull { it.dateModified } ?: 0L
            )
          }
          .filter { it.path !in blacklist && it.videoCount > 0 }
          .sortedBy { it.name.lowercase() }
        
        derivedFolders
      }.collectLatest { filteredFolders ->
        handleFolderUpdates(filteredFolders)
      }
    }

    // Trigger initial load if empty
    viewModelScope.launch {
        if (MediaFileRepository.allVideos.value.isEmpty()) {
            _isLoading.value = true
            MediaFileRepository.refreshAllVideos(getApplication())
            _hasCompletedInitialLoad.value = true
            _isLoading.value = false
        } else {
            _hasCompletedInitialLoad.value = true
        }
    }
  }

  private fun handleFolderUpdates(filteredFolders: List<VideoFolder>) {
    _videoFolders.value = filteredFolders
    
    // Recalculate 'new' counts and handle metadata enrichment if needed
    calculateNewVideoCounts(filteredFolders)
  }

  private fun calculateNewVideoCounts(folders: List<VideoFolder>) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val showLabel = appearancePreferences.showUnplayedOldVideoLabel.get()
        if (!showLabel) {
          _foldersWithNewCount.value = folders.map { FolderWithNewCount(it, 0) }
          return@launch
        }

        val thresholdDays = appearancePreferences.unplayedOldVideoDays.get()
        val thresholdMillis = thresholdDays * 24 * 60 * 60 * 1000L
        val currentTime = System.currentTimeMillis()

        // Use the repository's master list for counting to ensure consistency
        val allVideos = MediaFileRepository.allVideos.value

        val foldersWithCounts = folders.map { folder ->
          val videosInFolder = allVideos.filter { it.bucketId == folder.bucketId }
          val newCount = videosInFolder.count { video ->
            val videoAge = currentTime - (video.dateModified * 1000)
            val isRecent = videoAge <= thresholdMillis
            val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)
            val isUnplayed = playbackState == null
            isRecent && isUnplayed
          }
          FolderWithNewCount(folder, newCount)
        }

        _foldersWithNewCount.value = foldersWithCounts
      } catch (e: Exception) {
        Log.e(TAG, "Error calculating new video counts", e)
      }
    }
  }

  override fun refresh() {
    Log.d(TAG, "Refreshing master video list")
    _isLoading.value = true
    viewModelScope.launch(Dispatchers.IO) {
      MediaFileRepository.clearCache()
      MediaFileRepository.refreshAllVideos(getApplication())
      _isLoading.value = false
    }
  }

  fun recalculateNewVideoCounts() {
    calculateNewVideoCounts(_videoFolders.value)
  }

  /**
   * Delete folders
   */
  fun deleteFolders(folders: List<VideoFolder>): Pair<Int, Int> {
    var successCount = 0
    var failureCount = 0
    
    viewModelScope.launch(Dispatchers.IO) {
      val result = StorageOps.deleteFolders(getApplication(), folders.map { it.path })
      successCount = result.first
      failureCount = result.second
      
      if (successCount > 0) {
        MediaFileRepository.refreshAllVideos(getApplication())
      }
    }
    
    return Pair(successCount, failureCount)
  }

  /**
   * Rename a folder
   */
  fun renameFolder(folder: VideoFolder, newName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      StorageOps.renameFolder(getApplication(), folder.path, newName)
        .onSuccess {
          MediaFileRepository.refreshAllVideos(getApplication())
        }
    }
  }
}
