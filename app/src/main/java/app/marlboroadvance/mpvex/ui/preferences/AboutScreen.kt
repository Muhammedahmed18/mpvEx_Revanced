package app.marlboroadvance.mpvex.ui.preferences

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity.Companion.collectDeviceInfo
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import `is`.xyz.mpv.Utils
import kotlinx.serialization.Serializable

@Serializable
object AboutScreen : Screen {
  @Suppress("DEPRECATION")
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val clipboardManager = LocalClipboardManager.current
    val packageManager: PackageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName?.substringBefore('-') ?: packageInfo.versionName ?: BuildConfig.VERSION_NAME
    val buildType = BuildConfig.BUILD_TYPE

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(id = R.string.pref_about_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            ) 
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowBack, 
                contentDescription = null,
              )
            }
          },
        )
      },
    ) { paddingValues ->
      Column(
        modifier =
          Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Hero Section
        Spacer(Modifier.height(16.dp))
        
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
          text = "mpvExtended",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        
        Text(
          text = "v$versionName $buildType",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.secondary,
          fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.weight(1f))

        // Device Information Header
        Text(
            text = "Device Information",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                InfoListItem(
                    label = "App Version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})",
                    isFirst = true
                )
                InfoListItem(
                    label = "Android Version",
                    value = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                InfoListItem(
                    label = "Model",
                    value = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
                InfoListItem(
                    label = "MPV Version",
                    value = Utils.VERSIONS.mpv
                )
                InfoListItem(
                    label = "FFmpeg Version",
                    value = Utils.VERSIONS.ffmpeg,
                    isLast = true
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Action Section (Copy to Clipboard)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                    clipboardManager.setText(AnnotatedString(collectDeviceInfo()))
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            ListItem(
                headlineContent = { 
                    Text(
                        "Copy Debug Info",
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                supportingContent = { Text("Used for reporting issues") },
                leadingContent = { 
                    Icon(
                        imageVector = Icons.Default.ContentCopy, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    ) 
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    supportingColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            )
        }

        Spacer(Modifier.height(16.dp))
      }
    }
  }

  @Composable
  private fun InfoListItem(
    label: String,
    value: String,
    isFirst: Boolean = false,
    isLast: Boolean = false
  ) {
    Column {
        if (!isFirst) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
        ListItem(
            headlineContent = { 
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                ) 
            },
            supportingContent = { 
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ) 
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.height(56.dp) // Force compact height
        )
    }
  }
}
