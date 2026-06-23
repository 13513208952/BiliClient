package com.biliclient.feature.login

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biliclient.auth.AuthRepo
import com.biliclient.net.QrStatus
import com.biliclient.repo.UserRepo
import com.biliclient.util.Gallery
import com.biliclient.util.QrRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authRepo: AuthRepo,
    private val userRepo: UserRepo
) : ViewModel() {

    // ---------- 扫码 ----------
    data class QrUi(
        val loading: Boolean = true,
        val bitmap: Bitmap? = null,
        val savedToGallery: Boolean = false,
        val tip: String = "正在生成二维码…",
        val success: Boolean = false,
        val error: String? = null
    )
    private val _qr = MutableStateFlow(QrUi())
    val qr = _qr.asStateFlow()
    private var pollJob: Job? = null

    fun startQr() {
        pollJob?.cancel()
        _qr.value = QrUi()
        pollJob = viewModelScope.launch {
            try {
                val gen = withContext(Dispatchers.IO) { authRepo.qrGenerate() }
                val data = gen.data
                if (gen.code != 0 || data == null || data.url.isEmpty()) {
                    _qr.value = QrUi(loading = false, error = "生成二维码失败: ${gen.message}")
                    return@launch
                }
                val bmp = withContext(Dispatchers.Default) { QrRenderer.render(data.url) }
                val savedUri = withContext(Dispatchers.IO) {
                    Gallery.saveImage(appContext, bmp, "bili_qr_${System.currentTimeMillis()}")
                }
                _qr.value = QrUi(loading = false, bitmap = bmp, savedToGallery = savedUri != null, tip = "请用 哔哩哔哩 APP 扫码登录")
                while (isActive) {
                    delay(1500)
                    val poll = withContext(Dispatchers.IO) { authRepo.qrPoll(data.qrcodeKey) }
                    when (poll.data?.code) {
                        QrStatus.SUCCESS -> {
                            withContext(Dispatchers.IO) { userRepo.nav() }
                            _qr.value = _qr.value.copy(success = true, tip = "登录成功")
                            return@launch
                        }
                        QrStatus.SCANNED_WAIT -> _qr.value = _qr.value.copy(tip = "已扫码，请在手机确认")
                        QrStatus.EXPIRED -> { startQr(); return@launch }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _qr.value = QrUi(loading = false, error = e.message ?: "扫码异常")
            }
        }
    }

    // ---------- 短信 + 极验 ----------
    data class SmsUi(
        val phone: String = "", val code: String = "",
        val needGeetest: Boolean = false,
        val gt: String = "", val challenge: String = "", val token: String = "",
        val codeSent: Boolean = false, val sending: Boolean = false,
        val message: String? = null, val success: Boolean = false
    )
    private val _sms = MutableStateFlow(SmsUi())
    val sms = _sms.asStateFlow()
    private var captchaKey: String = ""

    fun onPhoneChange(v: String) { _sms.value = _sms.value.copy(phone = v.filter { it.isDigit() }) }
    fun onCodeChange(v: String) { _sms.value = _sms.value.copy(code = v.filter { it.isDigit() }) }

    fun requestSms() {
        val phone = _sms.value.phone
        if (phone.length < 11) { _sms.value = _sms.value.copy(message = "请输入 11 位手机号"); return }
        _sms.value = _sms.value.copy(sending = true, message = null)
        viewModelScope.launch {
            try {
                val cap = withContext(Dispatchers.IO) { authRepo.captcha() }
                val d = cap.data
                if (cap.code != 0 || d == null || d.geetest.gt.isEmpty()) {
                    _sms.value = _sms.value.copy(sending = false, message = "获取验证失败: ${cap.message}")
                    return@launch
                }
                _sms.value = _sms.value.copy(sending = false, needGeetest = true, gt = d.geetest.gt, challenge = d.geetest.challenge, token = d.token)
            } catch (e: Exception) {
                _sms.value = _sms.value.copy(sending = false, message = e.message ?: "异常")
            }
        }
    }

    fun onGeetestSuccess(validate: String, seccode: String) {
        val s = _sms.value
        _sms.value = s.copy(needGeetest = false, sending = true, message = "正在发送验证码…")
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    authRepo.smsSend(cid = "1", tel = s.phone, token = s.token, challenge = s.challenge, validate = validate, seccode = seccode)
                }
                if (resp.code == 0 && resp.data != null) {
                    captchaKey = resp.data.captchaKey
                    _sms.value = _sms.value.copy(sending = false, codeSent = true, message = "验证码已发送")
                } else {
                    _sms.value = _sms.value.copy(sending = false, message = "发送失败: ${resp.message}")
                }
            } catch (e: Exception) {
                _sms.value = _sms.value.copy(sending = false, message = e.message ?: "异常")
            }
        }
    }

    fun onGeetestError(msg: String) { _sms.value = _sms.value.copy(needGeetest = false, message = "验证失败: $msg") }

    fun submitSmsLogin() {
        val s = _sms.value
        if (s.code.isEmpty()) { _sms.value = s.copy(message = "请输入验证码"); return }
        _sms.value = s.copy(sending = true, message = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { authRepo.smsLogin("1", s.phone, s.code, captchaKey) }
                if (result.success) {
                    withContext(Dispatchers.IO) { userRepo.nav() }
                    _sms.value = _sms.value.copy(sending = false, success = true, message = "登录成功")
                } else {
                    _sms.value = _sms.value.copy(sending = false, message = "登录失败: ${result.message}")
                }
            } catch (e: Exception) {
                _sms.value = _sms.value.copy(sending = false, message = e.message ?: "异常")
            }
        }
    }

    override fun onCleared() { pollJob?.cancel(); super.onCleared() }
}
