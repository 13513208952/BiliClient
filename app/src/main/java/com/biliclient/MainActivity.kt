package com.biliclient

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.data.store.UiMode
import com.biliclient.feature.root.PhoneNavHost
import com.biliclient.feature.root.RootViewModel
import com.biliclient.feature.tv.TvNavHost
import com.biliclient.player.PlayerManager
import com.biliclient.ui.theme.BiliClientTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { playerManager.pause() }
        })
        setContent {
            val rootVm: RootViewModel = hiltViewModel()
            val mode by rootVm.uiMode.collectAsStateWithLifecycle()
            val tvUiScale by rootVm.tvUiScale.collectAsStateWithLifecycle()
            val activity = this@MainActivity

            // TV 模式强制横屏；手机模式恢复自由旋转
            LaunchedEffect(mode) {
                activity.requestedOrientation =
                    if (mode == UiMode.TV) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

            BiliClientTheme {
                androidx.compose.runtime.key(mode) {   // 切换时完整重建子 NavController + 所有子 VM
                    when (mode) {
                        UiMode.PHONE -> PhoneNavHost()
                        UiMode.TV    -> TvNavHost(uiScale = tvUiScale)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
