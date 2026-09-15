package com.imusic.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// --- 1. GitHub API Interface ---
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

// --- 2. Data Models ---
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val assets: List<GitHubAsset>
)
data class GitHubAsset(
    val browser_download_url: String,
    val name: String
)

// --- 3. Update Logic ---
class UpdateViewModel : ViewModel() {
    val updateAvailable = mutableStateOf(false)
    val updateUrl = mutableStateOf("")
    val newVersionName = mutableStateOf("")

    fun checkForUpdates(context: Context, githubOwner: String, githubRepo: String) {
        viewModelScope.launch {
            try {
                // Get the current version of the app installed on the phone
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersion = packageInfo.versionName ?: "1.0.0"

                // Fetch the latest release from your GitHub repository
                val api = Retrofit.Builder()
                    .baseUrl("https://api.github.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GitHubApi::class.java)

                val latestRelease = api.getLatestRelease(githubOwner, githubRepo)
                val latestVersion = latestRelease.tag_name.replace("v", "") // removes 'v' from 'v1.0.1'

                // Compare versions
                if (latestVersion != currentVersion.replace("v", "")) {
                    newVersionName.value = latestRelease.tag_name

                    // Find the APK file in the release assets, or fallback to the release page
                    val apkAsset = latestRelease.assets.firstOrNull { it.name.endsWith(".apk") }
                    updateUrl.value = apkAsset?.browser_download_url ?: latestRelease.html_url

                    updateAvailable.value = true
                }
            } catch (e: Exception) {
                // Fails silently if no internet or repo not found
                e.printStackTrace()
            }
        }
    }
}