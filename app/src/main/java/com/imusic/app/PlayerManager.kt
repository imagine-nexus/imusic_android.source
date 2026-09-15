package com.imusic.app

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val streamUrl: String
)

class PlayerViewModel : ViewModel() {
    private var mediaController: MediaController? = null

    private val _currentTrack = mutableStateOf<MusicTrack?>(null)
    val currentTrack: State<MusicTrack?> = _currentTrack

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _currentPosition = mutableStateOf(0L)
    val currentPosition: State<Long> = _currentPosition

    private val _duration = mutableStateOf(0L)
    val duration: State<Long> = _duration

    private var appContext: Context? = null

    fun initPlayer(context: Context) {
        if (mediaController == null) {
            appContext = context.applicationContext

            val sessionToken = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, PlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(context.applicationContext, sessionToken).buildAsync()

            controllerFuture.addListener({
                mediaController = controllerFuture.get()

                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                        }
                    }
                })
            }, ContextCompat.getMainExecutor(context.applicationContext))

            viewModelScope.launch {
                while (isActive) {
                    mediaController?.let { controller ->
                        if (controller.isPlaying) {
                            _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
                            _duration.value = controller.duration.coerceAtLeast(0L)
                        }
                    }
                    delay(500)
                }
            }
        }
    }

    // --- THE REAL INNERTUBE EXTRACTOR ---
    // This directly queries YouTube's internal API, exactly how the real YT Music app does it.
    // No middleman proxies to crash!
    private suspend fun extractDirectAudioUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // The official endpoint YouTube Music uses
                val url = URL("https://www.youtube.com/youtubei/v1/player")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")

                // Spoof as the official Android YouTube app to get unfiltered audio streams
                val payload = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID",
                            "clientVersion": "20.10.38",
                            "androidSdkVersion": 30,
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId"
                }
                """.trimIndent()

                connection.doOutput = true
                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(payload)
                writer.flush()
                writer.close()

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)

                    // Navigate to the adaptive streaming formats
                    val streamingData = json.optJSONObject("streamingData") ?: return@withContext null
                    val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: return@withContext null

                    var bestUrl: String? = null
                    var bestBitrate = 0

                    // Find the highest quality audio-only stream (usually WebM or M4A)
                    for (i in 0 until adaptiveFormats.length()) {
                        val stream = adaptiveFormats.getJSONObject(i)
                        val mimeType = stream.optString("mimeType", "")

                        if (mimeType.startsWith("audio/")) {
                            val bitrate = stream.optInt("bitrate", 0)
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = stream.getString("url")
                            }
                        }
                    }

                    if (bestUrl != null) {
                        Log.d("iMusic", "Direct InnerTube Audio Extracted!")
                        return@withContext bestUrl
                    }
                }
            } catch (e: Exception) {
                Log.e("iMusic", "InnerTube API failed: ${e.message}")
            }
            null
        }
    }

    fun playTrack(track: MusicTrack) {
        _currentTrack.value = track

        viewModelScope.launch {
            try {
                // Fetch the real YouTube audio directly from Google's servers
                var realStreamUrl = extractDirectAudioUrl(track.id)

                if (realStreamUrl == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Unable to extract audio from YouTube.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val playableTrack = track.copy(streamUrl = realStreamUrl)
                _currentTrack.value = playableTrack

                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(playableTrack.title)
                    .setArtist(playableTrack.artist)
                    .setArtworkUri(Uri.parse(playableTrack.albumArtUrl))
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(playableTrack.streamUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                mediaController?.let { controller ->
                    controller.stop()
                    controller.clearMediaItems()
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipForward() {
        mediaController?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
    }

    fun skipBackward() {
        mediaController?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
    }

    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }
}