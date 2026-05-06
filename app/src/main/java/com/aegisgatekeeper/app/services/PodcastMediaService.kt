package com.aegisgatekeeper.app.services

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aegisgatekeeper.app.App

class PodcastMediaService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

                        override fun onCreate() {
        super.onCreate()
        
        val headers = mutableMapOf("Accept" to "*/*")
        if (com.aegisgatekeeper.app.BuildConfig.COBALT_API_KEY.isNotEmpty()) {
            headers["Api-Key"] = com.aegisgatekeeper.app.BuildConfig.COBALT_API_KEY
        }

        val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(30000)

        val cacheDataSourceFactory =
            androidx.media3.datasource.cache.CacheDataSource
                .Factory()
                .setCache(App.downloadCache)
                .setUpstreamDataSourceFactory(dataSourceFactory)
                .setCacheWriteDataSinkFactory(null)

        val audioAttributes =
            androidx.media3.common.AudioAttributes
                .Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()

        val player =
            ExoPlayer
                .Builder(this)
                // For tunneled streams, we bypass the cache to avoid Range request issues.
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(audioAttributes, true)
                .build()

        val intent = Intent(this, com.aegisgatekeeper.app.MainActivity::class.java).apply {
            action = "com.aegisgatekeeper.app.OPEN_NATIVE_PLAYER"
            putExtra("OPEN_ACTIVE_NATIVE_PLAYER", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 1002, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setId("PodcastMediaSession_${java.util.UUID.randomUUID()}")
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady == true) {
            player.pause()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
