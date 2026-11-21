package com.example.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class RunnerGLSurfaceView(
  context: Context,
  val renderer: RunnerGameRenderer,
) : GLSurfaceView(context) {

  private var touchStartX = 0f
  private var touchStartY = 0f
  private var touchHandled = false
  private var touchStartMs = 0L

  init {
    setEGLContextClientVersion(2)
    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
    setRenderer(renderer)
    renderMode = RENDERMODE_CONTINUOUSLY
    isFocusable = true
    isFocusableInTouchMode = true
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        touchStartX = event.x
        touchStartY = event.y
        touchHandled = false
        touchStartMs = System.currentTimeMillis()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (!touchHandled) {
          val dx = event.x - touchStartX
          val dy = event.y - touchStartY
          val threshold = 42f
          if (abs(dx) >= threshold || abs(dy) >= threshold) {
            touchHandled = true
            val action =
              if (abs(dx) > abs(dy)) {
                if (dx < 0f) RunnerAction.LEFT else RunnerAction.RIGHT
              } else {
                if (dy < 0f) RunnerAction.JUMP else RunnerAction.SLIDE
              }
            queueEvent { renderer.handleAction(action) }
          }
        }
        return true
      }
      MotionEvent.ACTION_UP -> {
        if (!touchHandled) {
          val dx = event.x - touchStartX
          val dy = event.y - touchStartY
          val elapsed = System.currentTimeMillis() - touchStartMs
          if (elapsed < 220L && hypot(dx.toDouble(), dy.toDouble()) > 24.0) {
            val action =
              if (abs(dx) > abs(dy)) {
                if (dx < 0f) RunnerAction.LEFT else RunnerAction.RIGHT
              } else {
                if (dy < 0f) RunnerAction.JUMP else RunnerAction.SLIDE
              }
            queueEvent { renderer.handleAction(action) }
          }
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
        queueEvent { renderer.handleAction(RunnerAction.LEFT) }
        return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
        queueEvent { renderer.handleAction(RunnerAction.RIGHT) }
        return true
      }
      KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_SPACE -> {
        queueEvent { renderer.handleAction(RunnerAction.JUMP) }
        return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
        queueEvent { renderer.handleAction(RunnerAction.SLIDE) }
        return true
      }
      KeyEvent.KEYCODE_P -> {
        queueEvent { renderer.togglePause() }
        return true
      }
      KeyEvent.KEYCODE_M -> {
        queueEvent { renderer.toggleMute() }
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }
}
