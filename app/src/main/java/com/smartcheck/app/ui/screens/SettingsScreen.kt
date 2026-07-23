package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.utils.DeviceAuth
import com.smartcheck.app.utils.DeviceInfo
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.AdminAuthViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.smartcheck.app.BuildConfig
import com.smartcheck.app.utils.AppUpdateChecker
import com.smartcheck.app.utils.UpdateInfo

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AdminAuthViewModel = hiltViewModel()
) {
    val adminName by viewModel.adminName.collectAsState()
    val account by viewModel.account.collectAsState()
    val canteenName by viewModel.canteenName.collectAsState()
    val loginTitle by viewModel.loginTitle.collectAsState()
    val loginBackground by viewModel.loginBackground.collectAsState()
    val adminAvatar by viewModel.adminAvatar.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val deviceSn by viewModel.deviceSn.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val platformUrl by viewModel.platformUrl.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val heartbeatInterval by viewModel.heartbeatInterval.collectAsState()
    val context = LocalContext.current

    val currentAccount by authViewModel.account.collectAsState()
    val currentRole by authViewModel.currentRole.collectAsState()

    val defaultDeviceId = remember { DeviceAuth.getCurrentDeviceMac() ?: DeviceInfo.getDeviceId(context) }
    val deviceModel = remember { DeviceInfo.getDeviceModel() }
    val appVersion = remember { DeviceInfo.getAppVersion(context) }

    val primaryBlue = Color(0xFF2563EB)
    val primaryLight = Color(0xFFEFF6FF)
    val bgMain = Color(0xFFF8FAFC)
    val bgSidebar = Color.White
    val textMain = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)
    val danger = Color(0xFFEF4444)
    val dangerLight = Color(0xFFFEF2F2)

    var dialogLabel by remember { mutableStateOf("") }
    var dialogValue by remember { mutableStateOf("") }
    var onDialogConfirm by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showUpdateLoading by remember { mutableStateOf(false) }
    var showAvatarMenu by remember { mutableStateOf(false) }

    // 密码修改对话框
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordLoading by remember { mutableStateOf(false) }

    // 检查更新
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf(-1) }   // -1=空闲, 0-100=下载中
    var downloadSpeed by remember { mutableStateOf("") }      // 下载速度
    var updateError by remember { mutableStateOf("") }
    val updateScope = rememberCoroutineScope()

    // 版本历史
    var showHistoryDialog by remember { mutableStateOf(false) }
    var versionHistory by remember { mutableStateOf<List<UpdateInfo>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }

    // 平台连接测试状态
    var connectionTestLoading by remember { mutableStateOf(false) }
    var connectionTestSuccess by remember { mutableStateOf(false) }
    var connectionTestMessage by remember { mutableStateOf("点击测试连接") }

    fun openEdit(label: String, value: String, onConfirm: (String) -> Unit) {
        dialogLabel = label
        dialogValue = value
        onDialogConfirm = onConfirm
        showDialog = true
    }

    fun showPasswordChangeDialog() {
        oldPassword = ""
        newPassword = ""
        confirmPassword = ""
        passwordError = null
        showPasswordDialog = true
    }

    fun confirmPasswordChange() {
        passwordError = null
        if (oldPassword.isBlank()) {
            passwordError = "请输入原密码"
            return
        }
        if (newPassword.isBlank()) {
            passwordError = "请输入新密码"
            return
        }
        if (newPassword.length < 6) {
            passwordError = "新密码长度不能少于6位"
            return
        }
        if (newPassword != confirmPassword) {
            passwordError = "两次输入的密码不一致"
            return
        }
        
        passwordLoading = true
        authViewModel.changePassword(oldPassword, newPassword) { result ->
            passwordLoading = false
            result.fold(
                onSuccess = {
                    showPasswordDialog = false
                    oldPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                },
                onFailure = {
                    passwordError = it.message ?: "修改失败"
                }
            )
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setAdminAvatar(uri.toString())
        }
    }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setLoginBackground(uri.toString())
        }
    }

    val cameraImageFile = remember {
        val fileName = "avatar_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.jpg"
        File(context.cacheDir, fileName)
    }
    val cameraImageUri = remember(cameraImageFile) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cameraImageFile
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.setAdminAvatar(cameraImageUri.toString())
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(bgMain)) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(bgSidebar)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
                Text(
                    text = "设置中心",
                    color = textMain,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(primaryLight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "管理员设置",
                        color = primaryBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(dangerLight)
                    .clickable {
                        authViewModel.logout()
                        CoroutineScope(Dispatchers.Main).launch { onLogout() }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "退出登录",
                    color = danger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(borderColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(bgMain)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp, vertical = 28.dp)
                    .clickable(onClick = onNavigateBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回主页",
                    tint = textMuted,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "返回主页",
                    color = textMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, end = 48.dp, bottom = 48.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                SettingsSectionTitle(title = "个人资料", textMuted = textMuted)
                SettingsCard {
                    SettingsItem(
                        title = "管理员头像",
                        subtitle = "支持 JPG, PNG 格式图片",
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(primaryLight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (adminAvatar.isNotBlank()) {
                                    AsyncImage(
                                        model = adminAvatar,
                                        contentDescription = "头像",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "头像",
                                        tint = primaryBlue,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        },
                        trailing = {
                            PillButton(
                                text = "修改头像",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { showAvatarMenu = true }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "管理员姓名",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (adminName.isBlank()) "赵某某" else adminName,
                                    color = textMuted,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                PillButton(
                                    text = "修改",
                                    kind = PillButtonKind.Primary,
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    borderColor = borderColor,
                                    danger = danger,
                                    dangerLight = dangerLight,
                                    onClick = { openEdit("管理员姓名", adminName) { viewModel.setAdminName(it) } }
                                )
                            }
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "登录账号与密码",
                        subtitle = "当前账号: $currentAccount",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "******", color = textMuted, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                PillButton(
                                    text = "修改密码",
                                    kind = PillButtonKind.Primary,
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    borderColor = borderColor,
                                    danger = danger,
                                    dangerLight = dangerLight,
                                    onClick = { showPasswordChangeDialog() }
                                )
                            }
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor,
                        showDivider = false
                    )
                }

                DropdownMenu(
                    expanded = showAvatarMenu,
                    onDismissRequest = { showAvatarMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("拍照") },
                        onClick = {
                            showAvatarMenu = false
                            cameraLauncher.launch(cameraImageUri)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从相册选择") },
                        onClick = {
                            showAvatarMenu = false
                            avatarPicker.launch("image/*")
                        }
                    )
                }

                SettingsSectionTitle(title = "设备与系统", textMuted = textMuted)
                SettingsCard {
                    SettingsItem(
                        title = "设备识别码 (SN)",
                        subtitle = deviceSn.ifEmpty { defaultDeviceId },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "型号: $deviceModel", color = textMuted, fontSize = 13.sp)
                                Text(text = "v$appVersion", color = textMuted, fontSize = 13.sp)
                            }
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "设备ID (上报用)",
                        subtitle = deviceId.ifBlank { defaultDeviceId },
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { openEdit("设备ID (上报用)", deviceId.ifBlank { defaultDeviceId }) { viewModel.setDeviceId(it) } }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "激活服务器地址",
                        subtitle = DeviceAuth.serverUrl,
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "平台地址",
                        subtitle = platformUrl.ifBlank { "未配置" },
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { openEdit("平台地址", platformUrl) { viewModel.setPlatformUrl(it) } }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "API Key",
                        subtitle = if (apiKey.isBlank()) "未配置" else "已配置",
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { openEdit("API Key", apiKey) { viewModel.setApiKey(it) } }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "心跳间隔",
                        subtitle = "${heartbeatInterval}秒（范围 10-300）",
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = {
                                    openEdit(
                                        label = "心跳间隔（秒）",
                                        value = heartbeatInterval.toString()
                                    ) { raw ->
                                        val parsed = raw.toIntOrNull() ?: 30
                                        viewModel.setHeartbeatInterval(parsed)
                                    }
                                }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "平台连接测试",
                        subtitle = connectionTestMessage,
                        trailing = {
                            PillButton(
                                text = if (connectionTestLoading) "测试中" else "测试连接",
                                kind = if (connectionTestSuccess) PillButtonKind.Primary else PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                enabled = !connectionTestLoading,
                                onClick = {
                                    connectionTestLoading = true
                                    connectionTestMessage = "正在测试连接..."
                                    connectionTestSuccess = false
                                    updateScope.launch {
                                        val result = viewModel.testPlatformConnection()
                                        connectionTestLoading = false
                                        connectionTestSuccess = result.success
                                        connectionTestMessage = result.message
                                    }
                                }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "系统授权状态",
                        subtitle = "当前版本: V${BuildConfig.VERSION_NAME}",
                        trailing = {
                            PillButton(
                                text = "重置授权",
                                kind = PillButtonKind.Danger,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = {
                                    DeviceAuth.clearActivation()
                                    CoroutineScope(Dispatchers.Main).launch { onLogout() }
                                }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "当前登录",
                        subtitle = buildString {
                            append(currentAccount)
                            if (currentRole != null) {
                                append(" · ")
                                append(if (currentRole == "admin") "管理员" else "员工")
                            }
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "食堂名称",
                        subtitle = if (canteenName.isBlank()) "紫马科技" else canteenName,
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { openEdit("食堂名称", canteenName) { viewModel.setCanteenName(it) } }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "语音播报",
                        subtitle = if (voiceEnabled) "已开启" else "已关闭",
                        trailing = {
                            Switch(
                                checked = voiceEnabled,
                                onCheckedChange = { viewModel.setVoiceEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = primaryBlue,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFE2E8F0)
                                )
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor,
                        showDivider = false
                    )
                }

                SettingsSectionTitle(title = "应用更新", textMuted = textMuted)
                SettingsCard {
                    SettingsItem(
                        title = "当前版本",
                        subtitle = "V${BuildConfig.VERSION_NAME}",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PillButton(
                                    text = if (historyLoading) "加载中" else "更新记录",
                                    kind = PillButtonKind.Outline,
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    borderColor = borderColor,
                                    danger = danger,
                                    dangerLight = dangerLight,
                                    onClick = {
                                        if (!historyLoading) {
                                            historyLoading = true
                                            updateScope.launch {
                                                AppUpdateChecker.getVersionHistory(DeviceAuth.serverUrl)
                                                    .fold(
                                                        onSuccess = { history ->
                                                            historyLoading = false
                                                            versionHistory = history
                                                            showHistoryDialog = true
                                                        },
                                                        onFailure = { e ->
                                                            historyLoading = false
                                                            updateError = e.message ?: "获取历史失败"
                                                        }
                                                    )
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                PillButton(
                                    text = if (showUpdateLoading) "检查中" else "获取新版",
                                    kind = PillButtonKind.Primary,
                                    primaryBlue = primaryBlue,
                                    primaryLight = primaryLight,
                                    borderColor = borderColor,
                                    danger = danger,
                                    dangerLight = dangerLight,
                                    enabled = !showUpdateLoading,
                                    onClick = {
                                        if (showUpdateLoading) return@PillButton
                                        showUpdateLoading = true
                                        updateError = ""
                                        updateScope.launch {
                                            AppUpdateChecker.checkUpdate(DeviceAuth.serverUrl)
                                                .fold(
                                                    onSuccess = { info ->
                                                        showUpdateLoading = false
                                                        if (info != null) {
                                                            availableUpdate = info
                                                        } else {
                                                            updateError = "已是最新版本"
                                                        }
                                                    },
                                                    onFailure = { e ->
                                                        showUpdateLoading = false
                                                        updateError = e.message ?: "检查失败"
                                                    }
                                                )
                                        }
                                    }
                                )
                            }
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor,
                        showDivider = false
                    )

                    if (updateError.isNotEmpty()) {
                        val isInfoMessage = updateError == "已是最新版本" ||
                            updateError.startsWith("安装已交给系统")
                        Text(
                            text = updateError,
                            fontSize = 13.sp,
                            color = if (isInfoMessage) primaryBlue else danger,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                        )
                    }
                }

                SettingsSectionTitle(title = "界面与个性化", textMuted = textMuted)
                SettingsCard {
                    SettingsItem(
                        title = "登录页标题",
                        subtitle = if (loginTitle.isBlank()) "欢迎使用智能晨检仪" else loginTitle,
                        trailing = {
                            PillButton(
                                text = "修改",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { openEdit("登录页标题", loginTitle) { viewModel.setLoginTitle(it) } }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor
                    )
                    SettingsItem(
                        title = "登录页背景",
                        subtitle = if (loginBackground.isBlank()) "默认背景" else "已设置",
                        trailing = {
                            PillButton(
                                text = "选择图片",
                                kind = PillButtonKind.Outline,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { backgroundPicker.launch("image/*") }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor,
                        showDivider = false
                    )
                }

                SettingsSectionTitle(title = "维护", textMuted = textMuted)
                SettingsCard {
                    SettingsItem(
                        title = "清理历史记录照片",
                        subtitle = "删除本地照片与历史记录",
                        trailing = {
                            PillButton(
                                text = "立即清理",
                                kind = PillButtonKind.Danger,
                                primaryBlue = primaryBlue,
                                primaryLight = primaryLight,
                                borderColor = borderColor,
                                danger = danger,
                                dangerLight = dangerLight,
                                onClick = { viewModel.clearRecordImages() }
                            )
                        },
                        textMain = textMain,
                        textMuted = textMuted,
                        borderColor = borderColor,
                        showDivider = false
                    )
                }
            }
        }
    }

    // 错误/成功提示 3 秒后自动消失
    LaunchedEffect(updateError) {
        if (updateError.isNotEmpty()) {
            delay(3000)
            updateError = ""
        }
    }

    // 发现新版本对话框
    availableUpdate?.let { info ->
        AlertDialog(
            onDismissRequest = { availableUpdate = null },
            title = { Text(text = "发现新版本 ${info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    Text(text = "当前版本：V${BuildConfig.VERSION_NAME}")
                    if (info.releaseNotes.isNotEmpty()) {
                        Text(text = "更新内容：${info.releaseNotes}", color = Color(0xFF6B7280))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = info.apkUrl
                        Timber.i("SettingsScreen 用户点击'立即更新'，APK URL: $url")
                        availableUpdate = null
                        downloadProgress = 0
                        downloadSpeed = ""
                        updateScope.launch {
                            try {
                                Timber.i("SettingsScreen 开始下载 APK")
                                AppUpdateChecker.downloadAndInstall(context, url) { progress, speed ->
                                    downloadProgress = progress
                                    downloadSpeed = speed
                                }
                                downloadProgress = -1  // 安装已触发，关闭进度对话框
                                downloadSpeed = ""
                                Timber.i("SettingsScreen 下载完成，安装已触发")
                            } catch (e: Exception) {
                                downloadProgress = -1
                                downloadSpeed = ""
                                Timber.e("SettingsScreen 下载失败: ${e.message}")
                                val rawMessage = e.message.orEmpty()
                                updateError = if (
                                    rawMessage.contains("未安装此应用") ||
                                    rawMessage.contains("INSTALL_FAILED", ignoreCase = true)
                                ) {
                                    "安装已交给系统，请在系统安装界面完成安装"
                                } else if (rawMessage.isNotBlank()) {
                                    "下载失败：$rawMessage"
                                } else {
                                    "更新失败，请稍后重试"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) { Text(text = "立即更新", color = Color.White) }
            },
            dismissButton = {
                Button(
                    onClick = { availableUpdate = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB))
                ) { Text(text = "稍后再说", color = Color.Black) }
            }
        )
    }

    // 下载进度对话框
    if (downloadProgress >= 0) {
        AlertDialog(
            onDismissRequest = {},   // 下载中不允许关闭
            title = { Text(text = "正在下载更新...") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    if (downloadProgress in 0..99) {
                        LinearProgressIndicator(
                            progress = downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = primaryBlue
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$downloadProgress%",
                                color = Color(0xFF6B7280),
                                fontSize = Dimens.TextSizeSmall
                            )
                            if (downloadSpeed.isNotEmpty()) {
                                Text(
                                    text = downloadSpeed,
                                    color = primaryBlue,
                                    fontSize = Dimens.TextSizeSmall
                                )
                            }
                        }
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = primaryBlue
                        )
                        Text(text = "下载完成，等待安装...", color = Color(0xFF6B7280))
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "修改$dialogLabel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    OutlinedTextField(
                        value = dialogValue,
                        onValueChange = { dialogValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (dialogLabel == "登录页标题") {
                        Text(
                            text = "预览：${dialogValue.ifBlank { "欢迎使用智能晨检仪" }}",
                            fontSize = Dimens.TextSizeSmall,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDialogConfirm?.invoke(dialogValue)
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text(text = "确定", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB))
                ) {
                    Text(text = "取消", color = Color.Black)
                }
            }
        )
    }

    // 版本历史对话框
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text(text = "版本历史") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.PaddingNormal)
                ) {
                    if (versionHistory.isEmpty()) {
                        Text(text = "暂无版本记录", color = Color(0xFF6B7280))
                    } else {
                        versionHistory.forEach { v ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                                    .padding(Dimens.PaddingNormal),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "V${v.versionName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = Dimens.TextSizeNormal,
                                        color = Color(0xFF111827)
                                    )
                                    if (v.isLatest) {
                                        Text(
                                            text = "当前版本",
                                            fontSize = Dimens.TextSizeSmall,
                                            color = Color.White,
                                            modifier = Modifier
                                                .background(primaryBlue, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (v.createdAt.isNotBlank()) {
                                    Text(
                                        text = v.createdAt,
                                        fontSize = Dimens.TextSizeSmall,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                                if (v.releaseNotes.isNotBlank()) {
                                    Text(
                                        text = v.releaseNotes,
                                        fontSize = Dimens.TextSizeSmall,
                                        color = Color(0xFF374151)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text(text = "关闭", color = primaryBlue)
                }
            }
        )
    }

    // 密码修改对话框
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(text = "修改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)) {
                    OutlinedTextField(
                        value = oldPassword,
                        onValueChange = { oldPassword = it; passwordError = null },
                        label = { Text("原密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; passwordError = null },
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; passwordError = null },
                        label = { Text("确认新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = Dimens.TextSizeSmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { confirmPasswordChange() },
                    enabled = !passwordLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    if (passwordLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    }
                    Text(text = "确定", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showPasswordDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5E7EB))
                ) {
                    Text(text = "取消", color = Color.Black)
                }
            }
        )
    }
}

private enum class PillButtonKind {
    Primary,
    Outline,
    Danger
}

@Composable
private fun SettingsSectionTitle(
    title: String,
    textMuted: Color
) {
    Text(
        text = title,
        fontSize = 15.sp,
        color = textMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 6.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    textMain: Color,
    textMuted: Color,
    borderColor: Color,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Box(modifier = Modifier.padding(end = 14.dp)) {
                        leading()
                    }
                }
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        color = textMain,
                        fontWeight = FontWeight.Medium
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = textMuted
                        )
                    }
                }
            }

            if (trailing != null) {
                Box(modifier = Modifier.padding(start = 16.dp)) {
                    trailing()
                }
            }
        }

        if (showDivider) {
            Divider(color = borderColor)
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    kind: PillButtonKind,
    primaryBlue: Color,
    primaryLight: Color,
    borderColor: Color,
    danger: Color,
    dangerLight: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bg = when (kind) {
        PillButtonKind.Primary -> primaryBlue
        PillButtonKind.Outline -> primaryLight
        PillButtonKind.Danger -> dangerLight
    }
    val fg = when (kind) {
        PillButtonKind.Primary -> Color.White
        PillButtonKind.Outline -> primaryBlue
        PillButtonKind.Danger -> danger
    }
    val stroke = when (kind) {
        PillButtonKind.Outline -> borderColor
        else -> null
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (stroke != null) {
                    Modifier.border(1.dp, stroke, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
