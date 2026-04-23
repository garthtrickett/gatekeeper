        playerStateCallback = { playerState ->
            val isPlaying = playerState == 1
            val updateIntent = Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java).apply {
                action = "com.aegisgatekeeper.app.SERVICE_UPDATE"
                putExtra("EXTRA_IS_PLAYING", isPlaying)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(updateIntent)
            } else {
                context.startService(updateIntent)
            }
        }

        onDispose {
            android.webkit.CookieManager
                .getInstance()
                .flush()
            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(url, currentPosition))

            val stopIntent = Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java)
            context.stopService(stopIntent)
