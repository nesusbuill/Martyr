package com.apex.martyr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

// ... (các val màu giữ nguyên) ...

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasShizuku by remember { mutableStateOf(false) }
    var hasLQ by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isForceUpdate by remember { mutableStateOf(false) }
    var shizukuStatusText by remember { mutableStateOf("Đang kiểm tra Shizuku...") }

    val isRunning by ProgressState.isRunning.collectAsState()
    val progress by ProgressState.progress.collectAsState()
    val message by ProgressState.message.collectAsState()

    // 1. LẮNG NGHE SỰ KIỆN CẤP QUYỀN
    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                hasShizuku = true
                shizukuStatusText = "Đã cấp quyền Shizuku"
            } else {
                hasShizuku = false
                shizukuStatusText = "Quyền Shizuku bị từ chối"
            }
        }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    // 2. LẮNG NGHE TRẠNG THÁI BINDER (Sticky để bắt cả trường hợp Binder đến trước khi UI hiển thị)
    DisposableEffect(Unit) {
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            // Binder đã sẵn sàng, kiểm tra lại quyền
            hasShizuku = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            shizukuStatusText = if (hasShizuku) "Đã cấp quyền Shizuku" else "Chưa cấp quyền Shizuku"
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            hasShizuku = false
            shizukuStatusText = "Shizuku đã dừng hoạt động"
        }
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }

    suspend fun checkStatus() {
        withContext(Dispatchers.IO) {
            try {
                // ... (logic check force update và game giữ nguyên) ...
                val config = NetworkManager.fetchConfig()
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                }
                if (currentVersionCode < config.minAppVersion) {
                    isForceUpdate = true
                    return@withContext
                }

                hasLQ = try { context.packageManager.getPackageInfo("com.garena.game.kgvn", 0) != null } catch (_: Exception) { false }
                if (hasLQ) {
                    val info = context.packageManager.getPackageInfo("com.garena.game.kgvn", 0)
                    isLocked = Prefs.isOptimized(context, info.versionName)
                }

                // Bọc try-catch khi kiểm tra Shizuku để tránh crash nếu Binder chết
                try {
                    hasShizuku = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                    shizukuStatusText = if (hasShizuku) "Đã cấp quyền Shizuku" else "Chưa cấp quyền Shizuku"
                } catch (e: Throwable) {
                    hasShizuku = false
                    shizukuStatusText = "Lỗi kết nối Shizuku: ${e.message}"
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { checkStatus() }
    LaunchedEffect(isRunning) { if (!isRunning) checkStatus() }

    fun runOptimize() {
        if (isForceUpdate || isRunning) return
        try {
            if (!hasLQ) {
                Toast.makeText(context, "Chưa cài Liên Quân Mobile!", Toast.LENGTH_SHORT).show()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.garena.game.kgvn")))
                return
            }

            // Kiểm tra kỹ lại quyền trước khi chạy
            if (!hasShizuku) {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    hasShizuku = true
                } else {
                    Toast.makeText(context, "Vui lòng cấp quyền Shizuku...", Toast.LENGTH_SHORT).show()
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Lỗi yêu cầu quyền: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }

            if (isLocked) {
                Toast.makeText(context, "Bản này đã được tối ưu rồi!", Toast.LENGTH_SHORT).show()
                return
            }

            ProgressState.start()
            val i = Intent(context, MartyrService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            ProgressState.reset()
        }
    }

    // ... (phần giao diện UI giữ nguyên, nhưng thay đổi dòng chữ cảnh báo) ...
    
    // Tìm đến đoạn hiển thị cảnh báo và sửa thành:
    if (!hasShizuku || !hasLQ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (!hasShizuku) {
                Text("⚠ $shizukuStatusText - Bấm Optimize để cấp", color = RedColor, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            // ...
        }
    }
    
    // ... (phần còn lại giữ nguyên) ...
}
