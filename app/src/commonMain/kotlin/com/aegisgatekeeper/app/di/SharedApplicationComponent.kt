package com.aegisgatekeeper.app.di

import com.aegisgatekeeper.app.auth.TokenProvider
import com.aegisgatekeeper.app.media.MediaDownloader

interface SharedApplicationComponent {
    val tokenProvider: TokenProvider
    val mediaDownloader: MediaDownloader
}

object GlobalDI {
    lateinit var component: SharedApplicationComponent
}
