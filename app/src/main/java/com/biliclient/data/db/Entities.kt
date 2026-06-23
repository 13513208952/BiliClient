package com.biliclient.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 账号 + 登录 cookie（doc 11 §2.1）。登录 cookie 放 Room，便于查 csrf。 */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val uid: Long,            // DedeUserID
    val uname: String = "",
    val face: String = "",
    val sessdata: String = "",            // SESSDATA
    val biliJct: String = "",             // bili_jct = csrf
    val dedeUserIdCkMd5: String = "",     // DedeUserID__ckMd5
    val sid: String = "",
    val vipStatus: Int = 0,
    val loginTime: Long = 0
)

/** 下载任务（doc 11 §2.2）。P3 启用，先建表。 */
@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val epId: Long? = null,
    val seasonTitle: String? = null,
    val title: String = "",
    val cover: String = "",
    val qualityId: Int = 0,
    val codecId: Int = 0,
    val audioQualityId: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: Int = 0,                  // 0 排队 1 下载中 2 暂停 3 合流中 4 完成 5 失败
    val errorType: String? = null,
    val savedUri: String? = null,
    val parentId: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/** 搜索历史（doc 11 §2.3）。 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val keyword: String,
    val ts: Long = 0
)
