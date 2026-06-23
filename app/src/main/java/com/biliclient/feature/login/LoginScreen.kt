package com.biliclient.feature.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.biliclient.auth.GeetestWebView

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LoginViewModel = hiltViewModel()
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("扫码登录") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("短信登录") })
        }
        when (tab) {
            0 -> QrTab(vm, onLoggedIn)
            1 -> SmsTab(vm, onLoggedIn)
        }
    }
}

@Composable
private fun QrTab(vm: LoginViewModel, onLoggedIn: () -> Unit) {
    val ui by vm.qr.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.startQr() }
    LaunchedEffect(ui.success) { if (ui.success) onLoggedIn() }

    Column(
        Modifier.fillMaxSize().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        when {
            ui.loading -> CircularProgressIndicator()
            ui.error != null -> {
                Text(ui.error!!)
                Button(onClick = { vm.startQr() }, Modifier.padding(top = 16.dp)) { Text("重试") }
            }
            ui.bitmap != null -> {
                Image(
                    bitmap = ui.bitmap!!.asImageBitmap(),
                    contentDescription = "登录二维码",
                    modifier = Modifier.size(240.dp)
                )
                Text(ui.tip, Modifier.padding(top = 16.dp))
                if (ui.savedToGallery) Text("二维码已保存到相册", Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SmsTab(vm: LoginViewModel, onLoggedIn: () -> Unit) {
    val ui by vm.sms.collectAsStateWithLifecycle()
    LaunchedEffect(ui.success) { if (ui.success) onLoggedIn() }

    if (ui.needGeetest) {
        // 极验弹层
        Column(Modifier.fillMaxSize()) {
            Text("请完成人机验证", Modifier.padding(8.dp))
            GeetestWebView(
                gt = ui.gt, challenge = ui.challenge,
                modifier = Modifier.fillMaxWidth().height(420.dp),
                onSuccess = { v, s -> vm.onGeetestSuccess(v, s) },
                onError = { vm.onGeetestError(it) }
            )
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        OutlinedTextField(
            value = ui.phone, onValueChange = vm::onPhoneChange,
            label = { Text("手机号 (+86)") },
            keyboardType = KeyboardType.Phone,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { vm.requestSms() },
            enabled = !ui.sending,
            modifier = Modifier.padding(top = 12.dp)
        ) { Text(if (ui.codeSent) "重新获取验证码" else "获取验证码") }

        if (ui.codeSent) {
            OutlinedTextField(
                value = ui.code, onValueChange = vm::onCodeChange,
                label = { Text("验证码") },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
            Button(
                onClick = { vm.submitSmsLogin() },
                enabled = !ui.sending,
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("登录") }
        }

        ui.message?.let { Text(it, Modifier.padding(top = 16.dp)) }
        if (ui.sending) CircularProgressIndicator(Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
    )
}
