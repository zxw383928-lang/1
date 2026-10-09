package io.github.garminaicoach

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import io.github.garminaicoach.data.healthconnect.readPermission
import io.github.garminaicoach.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: CoachViewModel = viewModel(factory = CoachViewModel.factory((application as CoachApplication).repository))
            val state = model.state.collectAsStateWithLifecycle().value
            val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
                // Read actual grants; denied / partial results are normal, never force another prompt.
                model.refresh()
            }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) model.checkAccess()
                    if (event == Lifecycle.Event.ON_STOP) model.hideData()
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            CoachTheme {
                Box(Modifier.safeDrawingPadding()) {
                    CoachScreen(state,
                        onRefresh = model::refresh,
                        onPermissions = { metrics ->
                            try { launcher.launch(metrics.map { it.readPermission() }.toSet()) }
                            catch (_: Exception) { model.showMessage("系统授权界面无法打开，请进入 Health Connect 设置管理权限") }
                        },
                        onSettings = { openSettings(model) },
                        onInstall = {
                            val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                            if (!tryStart(store)) {
                                if (!tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))) {
                                    model.showMessage("没有可打开商店的应用，请在系统设置中更新 Health Connect")
                                }
                            }
                        },
                        onPrivacy = { startActivity(Intent(this@MainActivity, PermissionsRationaleActivity::class.java)) },
                        onClear = model::clearLocalData,
                    )
                }
            }
        }
    }
    private fun openSettings(model: CoachViewModel) {
        if (!tryStart(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))) {
            model.showMessage("未找到 Health Connect 设置入口；请在系统设置中搜索 Health Connect")
            tryStart(Intent(Settings.ACTION_SETTINGS))
        }
    }
    private fun tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: Exception) { false }
}
