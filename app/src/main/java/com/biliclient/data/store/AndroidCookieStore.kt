package com.biliclient.data.store

import com.biliclient.data.db.AccountDao
import com.biliclient.data.db.AccountEntity
import com.biliclient.net.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * cookie 持久化（doc 11）：风控 cookie -> DataStore(BiliStore)；登录 cookie -> Room(account)。
 */
class AndroidCookieStore(
    private val store: BiliStore,
    private val accountDao: AccountDao,
    private val scope: CoroutineScope
) : CookieStore {

    private val riskKeys = setOf("buvid3", "buvid4", "buvid_fp", "b_nut", "b_lsid", "_uuid", "bili_ticket")
    private val loginKeys = setOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")

    override fun load(): Map<String, String> = runBlocking {
        val risk = store.loadRiskCookies()
        val acc = accountDao.get()
        risk + loginCookiesOf(acc)
    }

    override fun persist(all: Map<String, String>) {
        scope.launch {
            store.saveRiskCookies(all.filterKeys { it in riskKeys })
            val sess = all["SESSDATA"]
            if (sess.isNullOrEmpty()) {
                accountDao.clear()
            } else {
                val existing = accountDao.get()
                accountDao.upsert(
                    AccountEntity(
                        uid = all["DedeUserID"]?.toLongOrNull() ?: existing?.uid ?: 0L,
                        uname = existing?.uname.orEmpty(),
                        face = existing?.face.orEmpty(),
                        sessdata = sess,
                        biliJct = all["bili_jct"].orEmpty(),
                        dedeUserIdCkMd5 = all["DedeUserID__ckMd5"].orEmpty(),
                        sid = all["sid"].orEmpty(),
                        vipStatus = existing?.vipStatus ?: 0,
                        loginTime = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun loginCookiesOf(acc: AccountEntity?): Map<String, String> {
        if (acc == null || acc.sessdata.isEmpty()) return emptyMap()
        return buildMap {
            put("SESSDATA", acc.sessdata)
            put("bili_jct", acc.biliJct)
            put("DedeUserID", acc.uid.toString())
            put("DedeUserID__ckMd5", acc.dedeUserIdCkMd5)
            if (acc.sid.isNotEmpty()) put("sid", acc.sid)
        }
    }
}
