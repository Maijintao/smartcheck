package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.smartcheck.app.BuildConfig
import com.smartcheck.app.data.repository.AdminAuthRepository
import com.smartcheck.app.ui.theme.Dimens
import com.smartcheck.app.viewmodel.AdminAuthViewModel
import com.smartcheck.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(
    onNavigateBack: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    viewModel: AdminAuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by remember { mutableStateOf(true) }

    val storedAccount by viewModel.account.collectAsState()
    val loginTitle by settingsViewModel.loginTitle.collectAsState()
    val canteenName by settingsViewModel.canteenName.collectAsState()
    val context = LocalContext.current

    val primaryColor = Color(0xFF2563EB)
    val textMain = Color(0xFF1F2937)
    val textMuted = Color(0xFF6B7280)
    val iconMuted = Color(0xFF9CA3AF)
    val bgInput = Color(0xFFF3F4F6)

    val accountInteraction = remember { MutableInteractionSource() }
    val passwordInteraction = remember { MutableInteractionSource() }
    val accountFocused by accountInteraction.collectIsFocusedAsState()
    val passwordFocused by passwordInteraction.collectIsFocusedAsState()

    val prefs = remember {
        context.getSharedPreferences("admin_auth", android.content.Context.MODE_PRIVATE)
    }

    LaunchedEffect(Unit) {
        viewModel.logout()
        val rememberedUsername = prefs.getString("remembered_username", null)
        val rememberedPassword = prefs.getString("remembered_password", null)
        if (rememberedUsername != null && rememberedPassword != null) {
            account = rememberedUsername
            password = rememberedPassword
            rememberPassword = true
        }
    }
    LaunchedEffect(storedAccount) {
        if (account.isBlank()) {
            account = if (storedAccount.isBlank()) AdminAuthRepository.DEFAULT_ACCOUNT else storedAccount
        }
        if (password.isBlank()) {
            password = AdminAuthRepository.DEFAULT_PASSWORD
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧品牌区域
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .background(Color(0xFF0A1628))
            ) {
                // 背景图片 - 科技感人体扫描图
                AsyncImage(
                    model = "android.resource://${context.packageName}/drawable/login_bg",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterStart
                )

                // 轻微暗色遮罩，让文字更清晰
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A1628).copy(alpha = 0.35f))
                )

                // 装饰圆形
                Box(
                    modifier = Modifier
                        .size(420.dp)
                        .offset(x = (-120).dp, y = (-120).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Box(
                    modifier = Modifier
                        .size(520.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 160.dp, y = 160.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 40.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (canteenName.isBlank()) "某某智能晨检" else canteenName,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "提供精准、快速的健康检测数据分析。\n守护每一次晨检，让管理更智能。",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            lineHeight = 22.sp
                        )
                    }

                    Text(
                        text = "当前版本: V ${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            // 右侧登录表单
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.65f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "欢迎登录",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMain
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "请输入您的管理员账号与密码",
                        fontSize = 14.sp,
                        color = textMuted
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 账号输入框
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(52.dp)
                        .border(
                            width = if (accountFocused) 1.5.dp else 1.dp,
                            color = if (accountFocused) primaryColor else Color(0xFFE5E7EB),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .background(if (accountFocused) Color.White else bgInput, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (accountFocused) primaryColor else iconMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = account,
                            onValueChange = {
                                account = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("请输入账号", fontSize = 15.sp, color = iconMuted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = textMain),
                            singleLine = true,
                            interactionSource = accountInteraction,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = primaryColor,
                                focusedTextColor = textMain,
                                unfocusedTextColor = textMain
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 密码输入框
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(52.dp)
                        .border(
                            width = if (passwordFocused) 1.5.dp else 1.dp,
                            color = if (passwordFocused) primaryColor else Color(0xFFE5E7EB),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .background(if (passwordFocused) Color.White else bgInput, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (passwordFocused) primaryColor else iconMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = password,
                            onValueChange = {
                                password = it
                                error = null
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("请输入密码", fontSize = 15.sp, color = iconMuted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = textMain),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            interactionSource = passwordInteraction,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                cursorColor = primaryColor,
                                focusedTextColor = textMain,
                                unfocusedTextColor = textMain
                            )
                        )
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏" else "显示",
                                tint = iconMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 记住密码 + 忘记密码
                Row(
                    modifier = Modifier.fillMaxWidth(0.65f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it },
                            colors = CheckboxDefaults.colors(checkedColor = primaryColor),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "记住密码",
                            fontSize = 13.sp,
                            color = textMuted
                        )
                    }
                    TextButton(
                        onClick = { },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "忘记密码？",
                            fontSize = 13.sp,
                            color = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 登录按钮
                Button(
                    onClick = {
                        viewModel.login(account, password) { result ->
                            result.fold(
                                onSuccess = {
                                    if (rememberPassword) {
                                        prefs.edit()
                                            .putString("remembered_username", account)
                                            .putString("remembered_password", password)
                                            .apply()
                                    } else {
                                        prefs.edit()
                                            .remove("remembered_username")
                                            .remove("remembered_password")
                                            .apply()
                                    }
                                    onLoginSuccess()
                                },
                                onFailure = { error = it.message ?: "登录失败" }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("登 录", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 激活设备
                TextButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "激活设备",
                        fontSize = 14.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
