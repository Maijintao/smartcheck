package com.smartcheck.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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
    val loginBackground by settingsViewModel.loginBackground.collectAsState()
    val canteenName by settingsViewModel.canteenName.collectAsState()
    val context = LocalContext.current

    val primaryColor = Color(0xFF2563EB)
    val primaryHover = Color(0xFF1D4ED8)
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
        // 加载记住的凭据
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
        if (loginBackground.isNotBlank()) {
            AsyncImage(
                model = loginBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.88f))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(420.dp)
                        .offset(x = (-120).dp, y = (-120).dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
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
                                colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                        Text(
                            text = if (canteenName.isBlank()) "某某智能晨检" else canteenName,
                            fontSize = Dimens.TextSizeNormal,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column(modifier = Modifier.offset(y = (-12).dp)) {
                        Text(
                            text = if (loginTitle.isBlank()) "某某智能晨检" else loginTitle,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "提供精准、快速的健康检测数据分析。\n守护每一次晨检，让管理更智能。",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.86f),
                            lineHeight = 22.sp
                        )
                    }

                    Text(
                        text = "当前版本: V ${BuildConfig.VERSION_NAME}",
                        fontSize = Dimens.TextSizeSmall,
                        color = Color.White.copy(alpha = 0.70f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
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
                fontSize = Dimens.TextSizeSmall,
                color = textMuted
            )
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(52.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (accountFocused) primaryColor else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                TextField(
                    value = account,
                    onValueChange = {
                        account = it
                        error = null
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("请输入账号", fontSize = Dimens.TextSizeNormal) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (accountFocused) primaryColor else iconMuted
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = Dimens.TextSizeNormal),
                    singleLine = true,
                    interactionSource = accountInteraction,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = bgInput,
                        disabledContainerColor = bgInput,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = primaryColor,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    )
                )
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingNormal))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(52.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (passwordFocused) primaryColor else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                TextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("请输入密码", fontSize = Dimens.TextSizeNormal) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (passwordFocused) primaryColor else iconMuted
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = Dimens.TextSizeNormal),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏" else "显示",
                                tint = iconMuted
                            )
                        }
                    },
                    isError = error != null,
                    interactionSource = passwordInteraction,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = bgInput,
                        disabledContainerColor = bgInput,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = primaryColor,
                        focusedTextColor = textMain,
                        unfocusedTextColor = textMain
                    )
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
                Text(
                    text = error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = Dimens.TextSizeSmall
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
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
                    .fillMaxWidth(0.7f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("登 录", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberPassword,
                        onCheckedChange = { rememberPassword = it },
                        colors = CheckboxDefaults.colors(checkedColor = primaryColor)
                    )
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text(text = "记住密码", fontSize = Dimens.TextSizeSmall, color = textMuted)
                }
                TextButton(onClick = { }) {
                    Text(text = "忘记密码？", fontSize = Dimens.TextSizeSmall, color = primaryColor)
                }
            }
        }
    }
}
}
