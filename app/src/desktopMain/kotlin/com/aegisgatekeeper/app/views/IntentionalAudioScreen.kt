        playerStateCallback = { playerState ->
            val isPlaying = playerState == 1
            val updateIntent = Intent("com.aegisgatekeeper.app.SERVICE_UPDATE").apply {
                setPackage(context.packageName)
                putExtra("EXTRA_IS_PLAYING", isPlaying)
            }
            context.sendBroadcast(updateIntent)
        }

        onDispose {
            android.webkit.CookieManager
                .getInstance()
                .flush()
            GatekeeperStateManager.dispatch(GatekeeperAction.SaveMediaPosition(url, currentPosition))

            val stopIntent = Intent(context, com.aegisgatekeeper.app.services.WebViewMediaService::class.java)
            context.stopService(stopIntent)
