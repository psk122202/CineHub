package com.cinehub.tv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Открытие стриминговых приложений и трейлеров на телевизоре. */
object External {

    /**
     * Название сервиса из TMDB → пакеты Android TV-приложений.
     * Если нужного сервиса нет — добавьте строку сюда.
     */
    private val providerPackages: List<Pair<String, List<String>>> = listOf(
        "netflix" to listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "amazon" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        "prime video" to listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
        "disney" to listOf("com.disney.disneyplus"),
        "apple tv" to listOf("com.apple.atve.androidtv.appletv"),
        "max" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "hbo" to listOf("com.wbd.stream", "com.hbo.hbonow"),
        "megogo" to listOf("com.megogo.application"),
        "sweet" to listOf("tv.sweet.tvplayer", "tv.sweet.player"),
        "kyivstar" to listOf("ua.kyivstar.tv"),
        "youtube" to listOf("com.google.android.youtube.tv", "com.google.android.youtube"),
        "google play" to listOf("com.google.android.videos"),
        "paramount" to listOf("com.cbs.ott"),
        "mubi" to listOf("com.mubi"),
        "skyshowtime" to listOf("com.skyshowtime.skyshowtime.google"),
        "okko" to listOf("ru.more.play"),
        "kinopoisk" to listOf("ru.kinopoisk.tv"),
        "ivi" to listOf("ru.ivi.client"),
    )

    private fun launchIntent(ctx: Context, pkg: String): Intent? {
        val pm = ctx.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    fun openProvider(ctx: Context, providerName: String) {
        val name = providerName.lowercase()
        val packages = providerPackages.filter { name.contains(it.first) }.flatMap { it.second }.distinct()
        for (pkg in packages) {
            val intent = launchIntent(ctx, pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(intent) }.onSuccess {
                Toast.makeText(ctx, "Найдите фильм в поиске $providerName", Toast.LENGTH_LONG).show()
                return
            }
        }
        if (packages.isNotEmpty()) {
            val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${packages.first()}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(store)
                return
            } catch (_: ActivityNotFoundException) {}
        }
        Toast.makeText(ctx, "Приложение «$providerName» не установлено", Toast.LENGTH_LONG).show()
    }

    fun openYoutube(ctx: Context, videoKey: String) {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoKey")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoKey")),
        )
        for (i in attempts) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {}
        }
        Toast.makeText(ctx, "Не найдено приложение YouTube", Toast.LENGTH_LONG).show()
    }
}
