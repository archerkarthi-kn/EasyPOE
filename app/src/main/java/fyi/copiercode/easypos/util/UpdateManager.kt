package fyi.copiercode.easypos.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import fyi.copiercode.easypos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://app.copiercode.fyi/uploads/easypos/version_info.json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext UpdateResult.Error("API call failed: ${response.code}")

                val body = response.body?.string() ?: return@withContext UpdateResult.Error("Empty response")
                val updateJson = json.parseToJsonElement(body).jsonObject
                
                val latestVersionCode = updateJson["version_code"]?.jsonPrimitive?.int ?: 0
                val latestVersionName = updateJson["version_name"]?.jsonPrimitive?.content ?: ""
                var downloadUrl = updateJson["download_url"]?.jsonPrimitive?.content ?: ""
                
                // Auto-correct URL if /uploads/ is missing for app.copiercode.fyi
                if (downloadUrl.contains("app.copiercode.fyi") && !downloadUrl.contains("/uploads/")) {
                    downloadUrl = downloadUrl.replace("app.copiercode.fyi/", "app.copiercode.fyi/uploads/")
                }
                
                if (latestVersionCode > BuildConfig.VERSION_CODE && downloadUrl.isNotBlank()) {
                    return@withContext UpdateResult.UpdateAvailable(latestVersionName, latestVersionCode, downloadUrl)
                }
                UpdateResult.NoUpdate
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Check failed", e)
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    fun downloadAndInstall(url: String) {
        // Clean up any old update files first
        val oldFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EasyPOS_Update.apk")
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Easy POS Update")
            .setDescription("Downloading latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "EasyPOS_Update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            installApk(context)
                        } else {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            Log.e("UpdateManager", "Download failed with reason: $reason")
                        }
                    }
                    cursor.close()
                    context.unregisterReceiver(this)
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "EasyPOS_Update.apk")
        if (file.exists()) {
            try {
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Log.e("UpdateManager", "Installation failed", e)
            }
        } else {
            Log.e("UpdateManager", "APK file not found at: ${file.absolutePath}")
        }
    }

    sealed class UpdateResult {
        object NoUpdate : UpdateResult()
        data class UpdateAvailable(val version: String, val versionCode: Int, val url: String) : UpdateResult()
        data class Error(val message: String) : UpdateResult()
    }
}
