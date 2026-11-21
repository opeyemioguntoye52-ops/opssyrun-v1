package com.example.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.random.Random

class ProceduralTextures {
  val whiteTex: Int
  val sandstoneFloorTex: Int
  val carvedPillarTex: Int
  val timberLogTex: Int
  val nairaCoinTex: Int

  init {
    whiteTex = uploadBitmap(createWhiteBitmap(), repeat = false)
    sandstoneFloorTex = uploadBitmap(createSandstoneFloorBitmap(), repeat = true)
    carvedPillarTex = uploadBitmap(createCarvedPillarBitmap(), repeat = true)
    timberLogTex = uploadBitmap(createTimberLogBitmap(), repeat = true)
    nairaCoinTex = uploadBitmap(createNairaCoinBitmap(), repeat = false)
  }

  private fun uploadBitmap(bmp: Bitmap, repeat: Boolean): Int {
    val ids = IntArray(1)
    GLES20.glGenTextures(1, ids, 0)
    val id = ids[0]
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
    val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
    GLES20.glTexParameteri(
      GLES20.GL_TEXTURE_2D,
      GLES20.GL_TEXTURE_MIN_FILTER,
      GLES20.GL_LINEAR_MIPMAP_LINEAR,
    )
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
    bmp.recycle()
    return id
  }

  private fun createWhiteBitmap(): Bitmap {
    return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.WHITE)
    }
  }

  private fun createSandstoneFloorBitmap(): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(218, 174, 126))

    val rng = Random(42)
    // Subtle weathered stone speckles
    for (i in 0 until 450) {
      paint.color =
        if (rng.nextBoolean()) Color.argb(38, 165, 118, 72) else Color.argb(32, 240, 210, 170)
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      canvas.drawCircle(x, y, 1.5f + rng.nextFloat() * 4f, paint)
    }

    // Tile grid seams + mossy tufts
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    paint.color = Color.rgb(138, 102, 66)
    val step = size / 3f
    for (i in 0..3) {
      val p = i * step
      canvas.drawLine(p, 0f, p, size.toFloat(), paint)
      canvas.drawLine(0f, p, size.toFloat(), p, paint)
    }

    // Moss along seams
    paint.style = Paint.Style.FILL
    for (i in 0 until 26) {
      paint.color =
        if (i % 2 == 0) Color.argb(180, 88, 129, 87) else Color.argb(150, 62, 98, 63)
      val onVert = i % 2 == 0
      val seam = (i % 3) * step
      val pos = rng.nextFloat() * size
      val r = 5f + rng.nextFloat() * 9f
      if (onVert) {
        canvas.drawCircle(seam, pos, r, paint)
      } else {
        canvas.drawCircle(pos, seam, r, paint)
      }
    }
    return bmp
  }

  private fun createCarvedPillarBitmap(): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(206, 162, 118))

    // Horizontal relief bands
    val bands = floatArrayOf(28f, 64f, 128f, 192f, 228f)
    for (y in bands) {
      paint.color = Color.rgb(152, 114, 78)
      canvas.drawRect(0f, y - 4f, size.toFloat(), y + 4f, paint)
      paint.color = Color.rgb(232, 196, 152)
      canvas.drawRect(0f, y - 7f, size.toFloat(), y - 4f, paint)
    }

    // Vertical carved geometric glyphs
    paint.color = Color.argb(110, 115, 84, 58)
    for (x in 16 until size step 32) {
      canvas.drawRect(x.toFloat(), 78f, x + 12f, 114f, paint)
      canvas.drawRect(x.toFloat(), 142f, x + 12f, 178f, paint)
    }

    // Moss patches
    val rng = Random(99)
    paint.color = Color.argb(175, 88, 129, 87)
    for (i in 0 until 20) {
      canvas.drawCircle(rng.nextFloat() * size, rng.nextFloat() * 44f, 6f + rng.nextFloat() * 8f, paint)
    }
    return bmp
  }

  private fun createTimberLogBitmap(): Bitmap {
    val size = 256
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(78, 55, 38))
    val rng = Random(123)
    for (i in 0 until 75) {
      paint.color = if (i % 3 == 0) Color.rgb(52, 36, 24) else Color.rgb(104, 75, 52)
      paint.strokeWidth = 2f + rng.nextFloat() * 4f
      val y = rng.nextFloat() * size
      canvas.drawLine(0f, y, size.toFloat(), y + (rng.nextFloat() - 0.5f) * 14f, paint)
    }
    // Moss cap spots
    paint.color = Color.argb(170, 88, 129, 87)
    for (i in 0 until 12) {
      canvas.drawCircle(rng.nextFloat() * size, rng.nextFloat() * size, 7f + rng.nextFloat() * 10f, paint)
    }
    return bmp
  }

  private fun createNairaCoinBitmap(): Bitmap {
    val size = 128
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawColor(Color.rgb(255, 209, 102))

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 8f
    paint.color = Color.rgb(244, 162, 97)
    canvas.drawCircle(64f, 64f, 52f, paint)

    paint.style = Paint.Style.FILL
    paint.color = Color.rgb(98, 64, 24)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 66f
    paint.isFakeBoldText = true
    canvas.drawText("₦", 64f, 86f, paint)
    return bmp
  }
}
