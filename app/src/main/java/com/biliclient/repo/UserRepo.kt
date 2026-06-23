package com.biliclient.repo

import com.biliclient.core.WbiSigner
import com.biliclient.data.db.AccountDao
import com.biliclient.data.db.AccountEntity
import com.biliclient.net.BiliApi
import com.biliclient.net.NavData
import javax.inject.Inject
import javax.inject.Singleton

/** 用户/鉴权资料仓库（nav 取 wbi key + 登录态 + 账号资料）。 */
@Singleton
class UserRepo @Inject constructor(
    private val api: BiliApi,
    private val accountDao: AccountDao
) {
    suspend fun nav(): NavData? {
        val d = runCatching { api.nav() }.getOrNull()?.data ?: return null
        if (d.wbiImg.imgUrl.isNotEmpty()) WbiSigner.updateKeysFromUrls(d.wbiImg.imgUrl, d.wbiImg.subUrl)
        if (d.isLogin) saveProfile(d)
        return d
    }

    suspend fun ensureWbiKeys() {
        if (!WbiSigner.hasKeys) nav()
    }

    suspend fun currentUname(): String? = accountDao.get()?.uname
    suspend fun currentUid(): Long? = accountDao.get()?.uid

    private suspend fun saveProfile(d: NavData) {
        val existing = accountDao.get() ?: AccountEntity(uid = d.mid)
        accountDao.upsert(
            existing.copy(
                uid = if (d.mid != 0L) d.mid else existing.uid,
                uname = d.uname,
                face = d.face,
                vipStatus = d.vipStatus
            )
        )
    }
}
