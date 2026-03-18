package app.marlboroadvance.mpvex.ui.browser.videolist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.playbackstate.repository.PlaybackStateRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.ui.browser.base.BaseBrowserViewModel
import app.marlboroadvance.mpvex.utils.media.MetadataRetrieval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import androidx.compose.runtime.Immutable

@Immutable
data class VideoWithPlaybackInfo(
  val video: Video,
  val timeRemaining: Long? = null, // in seconds
  val progressPercentage: Float? = null, // 0.0 to 1.0
  val isOldAndUnplayed: Boolean = false, // true if video is older than threshold and never played
  val isWatched: Boolean = false, // true if video has any playback history
)

class VideoListViewModel(
  application: Application,
  private val bucketId: String,
) : BaseBrowserViewModel(application),
  KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val appearancePreferences: app.marlboroadvance.mpvex.preferences.AppearancePreferences by inject()
  private val browserPreferences: app.marlboroadvance.mpvex.preferences.BrowserPreferences by inject()
  private val recentlyPlayedRepository: app.marlboroadvance.mpvex.domain.recentlyplayed.repository.RecentlyPlayedRepository by inject()

  private val _videos = MutableStateFlow<List<Video>>(emptyList())
  val videos: StateFlow<List<Video>> = _videos.asStateFlow()

  private val _videosWithPlaybackInfo = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val videosWithPlaybackInfo: StateFlow<List<VideoWithPlaybackInfo>> = _videosWithPlaybackInfo.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  val lastPlayedInFolderPath: StateFlow<String?> =
    recentlyPlayedRepository
      .observeRecentlyPlayed(limit = 100)
      .map { recentlyPlayedList ->
        val folderPath = _videos.value.firstOrNull()?.path?.let { File(it).parent }
        if (folderPath != null) {
          recentlyPlayedList.firstOrNull { entity ->
            try {
              File(entity.filePath).parent == folderPath
            } catch (_: Exception) {
              false
            }
          }?.filePath
        } else {
          null
        }
      }
      .distinctUntilChanged()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  private val tag = "VideoListViewModel"

  init {
    // Observe the master video list from repository and filter by bucketId
    viewModelScope.launch {
      MediaFileRepository.allVideos.map { allVideos ->
        allVideos.filter { it.bucketId == bucketId }
      }.collectLatest { filteredVideos ->
        handleVideoUpdates(filteredVideos)
      }
    }

    // Observe subtitle indicator preference changes
    viewModelScope.launch {
      browserPreferences.showSubtitleIndicator.changes().collectLatest { enabled ->
        if (enabled) {
          Log.d(tag, "Subtitle indicator enabled, enriching current videos")
          val currentVideos = _videos.value
          if (currentVideos.isNotEmpty()) {
            val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
              context = getApplication(),
              videos = currentVideos,
              browserPreferences = browserPreferences,
              metadataCache = metadataCache
            )
            if (enrichedVideos != currentVideos) {
              _videos.value = enrichedVideos
              loadPlaybackInfo(enrichedVideos)
            }
          }
        }
      }
    }
  }

  private suspend fun handleVideoUpdates(videoList: List<Video>) {
    // First, update with the raw list from repository
    _videos.value = videoList
    loadPlaybackInfo(videoList)

    // Then, enrich videos with detailed metadata (subtitles, etc.) using MediaInfo
    if (videoList.isNotEmpty() && MetadataRetrieval.isVideoMetadataNeeded(browserPreferences)) {
        val enrichedVideos = MetadataRetrieval.enrichVideosIfNeeded(
            context = getApplication(),
            videos = videoList,
            browserPreferences = browserPreferences,
            metadataCache = metadataCache
        )
        
        if (enrichedVideos != videoList) {
            _videos.value = enrichedVideos
            loadPlaybackInfo(enrichedVideos)
        }
    }
  }

  override fun refresh() {
    Log.d(tag, "Hard refreshing video list for bucket: $bucketId")
    _isLoading.value = true
    viewModelScope.launch(Dispatchers.IO) {
      MediaFileRepository.clearCache()
      MediaFileRepository.refreshAllVideos(getApplication())
      _isLoading.value = false
    }
  }

  /**
   * Overriding deleteVideos to use the new reactive deletion in MediaFileRepository
   */
  override suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    var deletedCount = 0
    var failedCount = 0
    
    videos.forEach { video ->
      val success = MediaFileRepository.deleteVideo(getApplication(), video)
      if (success) deletedCount++ else failedCount++
    }
    
    return Pair(deletedCount, failedCount)
  }

  private suspend fun loadPlaybackInfo(videos: List<Video>) {
    val watchedThreshold = browserPreferences.watchedThreshold.get()
    
    val videosWithInfo = videos.map { video ->
      val playbackState = playbackStateRepository.getVideoDataByTitle(video.displayName)

      val progress = if (playbackState != null && video.duration > 0) {
        val durationSeconds = video.duration / 1000
        val timeRemaining = playbackState.timeRemaining.toLong()
        val watched = durationSeconds - timeRemaining
        val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        if (progressValue in 0.01f..0.99f) progressValue else null
      } else null

      val isOldAndUnplayed = playbackState == null

      val isWatched = if (playbackState != null && video.duration > 0) {
         val durationSeconds = video.duration / 1000
         val timeRemaining = playbackState.timeRemaining.toLong()
         val watched = durationSeconds - timeRemaining
         val progressValue = (watched.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
         val calculatedWatched = progressValue >= (watchedThreshold / 100f)
         playbackState.hasBeenWatched || calculatedWatched
      } else false

      VideoWithPlaybackInfo(
        video = video,
        timeRemaining = playbackState?.timeRemaining?.toLong(),
        progressPercentage = progress,
        isOldAndUnplayed = isOldAndUnplayed,
        isWatched = isWatched,
      )
    }
    _videosWithPlaybackInfo.value = videosWithInfo
  }

  companion object {
    fun factory(
      application: Application,
      bucketId: String,
    ) = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = VideoListViewModel(application, bucketId) as T
    }
  }
}
