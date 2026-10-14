package com.mradio.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Cover art for stations without a logo (notification, lock screen, Android Auto):
 * the station's initials on one of the Mauritian flag colours.
 */
object Artwork {
    private val flag = intArrayOf(
        Color.rgb(0xEA, 0x28, 0x39), // red
        Color.rgb(0x1A, 0x20, 0x6D), // blue
        Color.rgb(0xFF, 0xD5, 0x00), // yellow
        Color.rgb(0x00, 0xA5, 0x51), // green
    )
    private val cache = ConcurrentHashMap<String, ByteArray>()

    fun png(station: Station): ByteArray = cache.getOrPut(station.id) {
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val index = Math.floorMod(station.name.hashCode(), flag.size)
        canvas.drawColor(flag[index])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (index == 2) Color.rgb(0x1B, 0x1F, 0x2A) else Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials(station.name), size / 2f, y, paint)
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
