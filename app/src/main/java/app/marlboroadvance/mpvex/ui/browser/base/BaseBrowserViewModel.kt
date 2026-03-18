package app.marlboroadvance.mpvex.ui.browser.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.domain.media.model.Video
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.permission.PermissionUtils.StorageOps
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base ViewModel for browser screens with shared functionality
 */
abstract class BaseBrowserViewModel(
  application: Application,
) : AndroidViewModel(application),
  KoinComponent {
  protected val metadataCache: VideoMetadataCacheRepository by inject()
  protected val thumbnailRepository: ThumbnailRepository by inject()

  /**
   * Observable recently played file path for highlighting
   * Automatically filters out non-existent files
   */
  val recentlyPlayedFilePath: StateFlow<String?> =
    RecentlyPlayedOps
      .observeLastPlayedPath()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  /**
   * Refresh the data (to be implemented by subclasses)
   */
  abstract fun refresh()

  /**
   * Delete videos from storage using reactive repository
   * Automatically updates global state, removes from recently played history, and invalidates cache
   *
   * @return Pair of (deletedCount, failedCount)
   */
  open suspend fun deleteVideos(videos: List<Video>): Pair<Int, Int> {
    var deletedCount = 0
    var failureCount = 0

    videos.forEach { video ->
      val success = MediaFileRepository.deleteVideo(getApplication(), video)
      if (success) {
        deletedCount++
        // Invalidate cache for deleted video
        metadataCache.invalidateVideo(video.path)
        thumbnailRepository.invalidateVideo(video)
      } else {
        failureCount++
      }
    }

    return Pair(deletedCount, failureCount)
  }

  /**
   * Rename a video
   * Automatically updates recently played history and invalidates old cache entry
   *
   * @param video Video to rename
   * @param newDisplayName New display name (including extension)
   * @return Result indicating success or failure
   */
  open suspend fun renameVideo(
    video: Video,
    newDisplayName: String,
  ): Result<Unit> {
    val oldPath = video.path
    val result = StorageOps.renameVideo(getApplication(), video, newDisplayName)

    // Invalidate cache for old path (new path will be re-cached on next access)
    result.onSuccess {
      metadataCache.invalidateVideo(oldPath)
      thumbnailRepository.invalidateVideo(video)
    }

    return result
  }
}
