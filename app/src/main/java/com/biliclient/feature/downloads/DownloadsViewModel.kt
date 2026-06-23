package com.biliclient.feature.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import com.biliclient.data.db.DownloadEntity
import com.biliclient.download.DownloadController
import com.biliclient.download.DownloadRepo
import com.biliclient.download.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DownloadRepo,
    val controller: DownloadController
) : ViewModel() {

    val tasks: Flow<List<DownloadEntity>> = repo.all()
    val progress = controller.progress

    fun pause(id: Long) = DownloadService.control(context, DownloadService.ACTION_PAUSE, id)
    fun resume(id: Long) = DownloadService.control(context, DownloadService.ACTION_RESUME, id)
    fun cancel(id: Long) = DownloadService.control(context, DownloadService.ACTION_CANCEL, id)
    fun retry(id: Long) = DownloadService.enqueue(context, id)
}
