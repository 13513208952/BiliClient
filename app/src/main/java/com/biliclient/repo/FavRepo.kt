package com.biliclient.repo

import com.biliclient.net.BiliApi
import com.biliclient.net.FavFolder
import com.biliclient.net.FavResourceData
import javax.inject.Inject
import javax.inject.Singleton

/** 收藏夹列表 + 内容（doc 10 §3）。 */
@Singleton
class FavRepo @Inject constructor(
    private val api: BiliApi,
    private val userRepo: UserRepo
) {
    suspend fun folders(): List<FavFolder> {
        val uid = userRepo.currentUid() ?: return emptyList()
        return runCatching { api.favFolders(uid) }.getOrNull()?.data?.list ?: emptyList()
    }

    suspend fun resources(mediaId: Long, pn: Int = 1): FavResourceData? =
        runCatching { api.favResourceList(mediaId, pn) }.getOrNull()?.data
}
