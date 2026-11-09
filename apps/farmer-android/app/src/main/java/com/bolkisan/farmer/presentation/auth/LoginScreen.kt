package com.bolkisan.farmer.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bolkisan.farmer.BuildConfig
import com.bolkisan.farmer.R
import com.bolkisan.farmer.data.repository.BolKisanRepository
import com.bolkisan.farmer.presentation.common.appStrings
import com.bolkisan.farmer.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    repository: BolKisanRepository,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val currentLang by repository.languageFlow.collectAsState()
    val strings = appStrings(currentLang)
    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MintCreamBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_bol_kisan_logo),
                contentDescription = "Bol Kisan Logo",
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "BOL KISAN",
                style = Typography.headlineLarge,
                color = ForestGreenPrimary
            )
            Text(
                text = strings.loginTitle,
                style = Typography.titleLarge,
                color = DarkEarthFg,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            errorMessage?.let {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2))
                ) {
                    Text(
                        text = it,
                        color = HazardRed,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text(strings.mobileNumberLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ForestGreenPrimary,
                    unfocusedBorderColor = SproutBorder
                )
            )

            if (!otpSent) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            loading = true
                            errorMessage = null
                            val res = repository.requestOtp(phoneNumber)
                            loading = false
                            if (res.isSuccess) {
                                otpSent = true
                                otp = res.getOrNull()?.devOtp ?: ""
                            } else {
                                errorMessage = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Text(
                        text = if (loading) strings.sendingOtp else strings.getOtp,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardWhite
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it },
                    label = { Text(strings.otpLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ForestGreenPrimary,
                        unfocusedBorderColor = SproutBorder
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            loading = true
                            errorMessage = null
                            val res = repository.verifyOtp(phoneNumber, otp, "Ramu Farmer")
                            loading = false
                            if (res.isSuccess) {
                                onLoginSuccess()
                            } else {
                                errorMessage = res.exceptionOrNull()?.message
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreenPrimary)
                ) {
                    Text(
                        text = if (loading) strings.verifyingOtp else strings.loginButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CardWhite
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        phoneNumber = "9876543210"
                        otp = "123456"
                        otpSent = true
                    }
                ) {
                    Text(
                        text = strings.demoAccount,
                        color = HarvestGoldAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
