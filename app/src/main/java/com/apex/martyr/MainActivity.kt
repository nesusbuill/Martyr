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

val BgDark    = Color(0xFF141414)
val CardBg    = Color(0xFF1C1C1E)
val PurpleBtn = Color(0xFFD0BCFF)
val TextWhite = Color(0xFFEEEEEE)
val TextGray  = Color(0xFF888888)
val RedColor  = Color(0xFFFF5252)

class MainActivity : ComponentActivity() {
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (_: Exception) {}

        try {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(background = BgDark)) {
                    App()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasShizuku by remember { mutableStateOf(false) }
    var hasLQ by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isForceUpdate by remember { mutableStateOf(false) }
    
    val isRunning by ProgressState.isRunning.collectAsState()
    val progress by ProgressState.progress.collectAsState()
    val message by ProgressState.message.collectAsState()

    // LẮNG NGHE CẤP QUYỀN VÀ TRẠNG THÁI BINDER
    DisposableEffect(Unit) {
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                hasShizuku = true
                Toast.makeText(context, "Đã cấp quyền Shizuku!", Toast.LENGTH_SHORT).show()
            }
        }
        val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            hasShizuku = try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
        }
        val binderDeadListener = Shizuku.OnBinderDeadListener {
            hasShizuku = false
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        onDispose {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }

    suspend fun checkStatus() {
        withContext(Dispatchers.IO) {
            try {
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
                
                try {
                    hasShizuku = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } catch (_: Throwable) {
                    hasShizuku = false
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.garena.game.kgvn")))
                return
            }
            
            // KIỂM TRA SHIZUKU CỰC KỲ CHẶT CHẼ
            if (!hasShizuku) {
                var isPingOk = false
                try {
                    isPingOk = Shizuku.pingBinder()
                } catch (e: Throwable) {}

                if (!isPingOk) {
                    // Nếu Shizuku bị tắt ngầm, tự động mở app Shizuku lên
                    Toast.makeText(context, "Shizuku chưa chạy! Đang mở app Shizuku...", Toast.LENGTH_LONG).show()
                    try {
                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Vui lòng cài đặt app Shizuku!", Toast.LENGTH_SHORT).show()
                    }
                    return
                } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    // Nếu Shizuku chạy nhưng chưa cấp quyền
                    Toast.makeText(context, "Vui lòng cấp quyền Shizuku...", Toast.LENGTH_SHORT).show()
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (e: Exception) {}
                    return
                } else {
                    hasShizuku = true
                }
            }
            
            if (isLocked) return

            ProgressState.start()
            val i = Intent(context, MartyrService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (e: Exception) { 
            ProgressState.reset() 
        }
    }

    if (isForceUpdate) {
        Column(
            Modifier.fillMaxSize().background(BgDark).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("⚠️ BẢN CẬP NHẬT BẮT BUỘC", color = RedColor, fontWeight = FontWeight.Black, fontSize = 24.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                "Phiên bản App bạn đang dùng đã quá cũ và không còn được hỗ trợ.\n\nVui lòng tải bản mới nhất để tiếp tục sử dụng!",
                color = TextWhite, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 24.sp
            )
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp))
                    .background(PurpleBtn)
                    .clickable {
                        // =========================================================
                        // ⚠️ SẾP SỬA LINK TẢI APP MỚI TẠI ĐÂY (Thay link bên dưới)
                        // =========================================================
                        val updateUrl = "https://github.com/nesusbuill/Martyr/releases" 
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))) } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("TẢI BẢN CẬP NHẬT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(BgDark).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text("<", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBg).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Game Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Liên Quân Mobile", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("com.garena.game.kgvn", color = TextGray, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            val lines = listOf(
                "• Fix Lag Siêu Mượt (Còn 1,3GB)",
                "• Chơi được đấu thường, rank, đỉnh cao, 3v3",
                "• Nền sảnh, tổ đội",
                "• Sử dụng map gốc",
                "• Có cấu hình cao",
                "• Không avt",
                "• Có emote",
                "• Có skin (Ko mod)",
                "• Có âm thanh chiêu thức",
                "• Xoá chi tiết tướng và quái",
                "• Không các hiệu ứng đổ(ảo choàng băng...)"
            )
            
            lines.forEach { line ->
                Text(text = line, color = TextWhite, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 2.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text("✖ Mỗi bản update chỉ optimize 1 lần.", color = RedColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))

        if (isRunning) {
            Text("$message ($progress%)", color = PurpleBtn, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), textAlign = TextAlign.Center)
        }

        if (!hasShizuku || !hasLQ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                if (!hasShizuku) {
                    Text("⚠ Chưa cấp quyền Shizuku - Bấm Optimize để cấp", color = RedColor, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                if (!hasLQ) {
                    Text("⚠ Chưa cài Liên Quân Mobile", color = RedColor, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = when {
                            isLocked -> listOf(Color(0xFF444444), Color(0xFF222222))
                            isRunning -> listOf(Color(0xFF888888), Color(0xFF555555))
                            else -> listOf(PurpleBtn, PurpleBtn)
                        }
                    )
                )
                .clickable(enabled = !isRunning && !isLocked) { runOptimize() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isLocked -> "ĐÃ TỐI ƯU"
                        isRunning -> "$progress%"
                        else -> "Optimize"
                    },
                    color = if (isLocked) TextGray else Color.White, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 20.sp
                )
                if (!isRunning && !isLocked) {
                    Spacer(Modifier.width(8.dp))
                    Text("▶", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
