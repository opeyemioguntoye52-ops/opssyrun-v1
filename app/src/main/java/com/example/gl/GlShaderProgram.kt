package com.example.gl

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GlMesh(
  vertices: FloatArray, // interleaved: x, y, z, nx, ny, nz, u, v (8 floats/vert)
  val vertexCount: Int,
) {
  private val buffer: FloatBuffer =
    ByteBuffer.allocateDirect(vertices.size * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(vertices)
        position(0)
      }

  fun bindAndDraw(posHandle: Int, normHandle: Int, uvHandle: Int) {
    val stride = 8 * 4
    buffer.position(0)
    GLES20.glEnableVertexAttribArray(posHandle)
    GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)

    buffer.position(3)
    GLES20.glEnableVertexAttribArray(normHandle)
    GLES20.glVertexAttribPointer(normHandle, 3, GLES20.GL_FLOAT, false, stride, buffer)

    buffer.position(6)
    GLES20.glEnableVertexAttribArray(uvHandle)
    GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, stride, buffer)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
  }

  companion object {
    fun createBox(): GlMesh {
      val v = floatArrayOf(
        // Front (+Z)
        -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,
        0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 1f, 1f,
        0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
        -0.5f, -0.5f, 0.5f, 0f, 0f, 1f, 0f, 1f,
        0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 1f, 0f,
        -0.5f, 0.5f, 0.5f, 0f, 0f, 1f, 0f, 0f,
        // Back (-Z)
        0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f,
        -0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 1f, 1f,
        -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
        0.5f, -0.5f, -0.5f, 0f, 0f, -1f, 0f, 1f,
        -0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 1f, 0f,
        0.5f, 0.5f, -0.5f, 0f, 0f, -1f, 0f, 0f,
        // Right (+X)
        0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0f, 1f,
        0.5f, -0.5f, -0.5f, 1f, 0f, 0f, 1f, 1f,
        0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
        0.5f, -0.5f, 0.5f, 1f, 0f, 0f, 0f, 1f,
        0.5f, 0.5f, -0.5f, 1f, 0f, 0f, 1f, 0f,
        0.5f, 0.5f, 0.5f, 1f, 0f, 0f, 0f, 0f,
        // Left (-X)
        -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f,
        -0.5f, -0.5f, 0.5f, -1f, 0f, 0f, 1f, 1f,
        -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, 1f, 0f,
        -0.5f, -0.5f, -0.5f, -1f, 0f, 0f, 0f, 1f,
        -0.5f, 0.5f, 0.5f, -1f, 0f, 0f, 1f, 0f,
        -0.5f, 0.5f, -0.5f, -1f, 0f, 0f, 0f, 0f,
        // Top (+Y)
        -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0f, 1f,
        0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 1f, 1f,
        0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0.5f, 0f, 1f, 0f, 0f, 1f,
        0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 1f, 0f,
        -0.5f, 0.5f, -0.5f, 0f, 1f, 0f, 0f, 0f,
        // Bottom (-Y)
        -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,
        0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 1f, 1f,
        0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 1f, 0f,
        -0.5f, -0.5f, -0.5f, 0f, -1f, 0f, 0f, 1f,
        0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 1f, 0f,
        -0.5f, -0.5f, 0.5f, 0f, -1f, 0f, 0f, 0f,
      )
      return GlMesh(v, 36)
    }

    fun createCylinder(segments: Int = 16): GlMesh {
      val list = ArrayList<Float>(segments * 12 * 8)
      fun push(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
        list.add(x); list.add(y); list.add(z)
        list.add(nx); list.add(ny); list.add(nz)
        list.add(u); list.add(v)
      }
      for (i in 0 until segments) {
        val a0 = (2.0 * PI * i / segments).toFloat()
        val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
        val x0 = cos(a0) * 0.5f
        val z0 = sin(a0) * 0.5f
        val x1 = cos(a1) * 0.5f
        val z1 = sin(a1) * 0.5f
        val nx0 = cos(a0)
        val nz0 = sin(a0)
        val nx1 = cos(a1)
        val nz1 = sin(a1)
        val u0 = i.toFloat() / segments
        val u1 = (i + 1).toFloat() / segments

        // Side quad (two triangles)
        push(x0, -0.5f, z0, nx0, 0f, nz0, u0, 1f)
        push(x1, -0.5f, z1, nx1, 0f, nz1, u1, 1f)
        push(x1, 0.5f, z1, nx1, 0f, nz1, u1, 0f)
        push(x0, -0.5f, z0, nx0, 0f, nz0, u0, 1f)
        push(x1, 0.5f, z1, nx1, 0f, nz1, u1, 0f)
        push(x0, 0.5f, z0, nx0, 0f, nz0, u0, 0f)

        // Top cap
        push(0f, 0.5f, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        push(x1, 0.5f, z1, 0f, 1f, 0f, 0.5f + cos(a1) * 0.48f, 0.5f + sin(a1) * 0.48f)
        push(x0, 0.5f, z0, 0f, 1f, 0f, 0.5f + cos(a0) * 0.48f, 0.5f + sin(a0) * 0.48f)

        // Bottom cap
        push(0f, -0.5f, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
        push(x0, -0.5f, z0, 0f, -1f, 0f, 0.5f + cos(a0) * 0.48f, 0.5f + sin(a0) * 0.48f)
        push(x1, -0.5f, z1, 0f, -1f, 0f, 0.5f + cos(a1) * 0.48f, 0.5f + sin(a1) * 0.48f)
      }
      return GlMesh(list.toFloatArray(), list.size / 8)
    }

    fun createOctahedron(): GlMesh {
      val top = floatArrayOf(0f, 0.5f, 0f)
      val bot = floatArrayOf(0f, -0.5f, 0f)
      val ring = arrayOf(
        floatArrayOf(0f, 0f, 0.5f),
        floatArrayOf(0.5f, 0f, 0f),
        floatArrayOf(0f, 0f, -0.5f),
        floatArrayOf(-0.5f, 0f, 0f),
      )
      val list = ArrayList<Float>(8 * 3 * 8)
      fun tri(a: FloatArray, b: FloatArray, c: FloatArray) {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        nx /= len; ny /= len; nz /= len
        list.add(a[0]); list.add(a[1]); list.add(a[2]); list.add(nx); list.add(ny); list.add(nz); list.add(0.5f); list.add(0f)
        list.add(b[0]); list.add(b[1]); list.add(b[2]); list.add(nx); list.add(ny); list.add(nz); list.add(0f); list.add(1f)
        list.add(c[0]); list.add(c[1]); list.add(c[2]); list.add(nx); list.add(ny); list.add(nz); list.add(1f); list.add(1f)
      }
      for (i in 0 until 4) {
        val n = (i + 1) % 4
        tri(top, ring[i], ring[n])
        tri(bot, ring[n], ring[i])
      }
      return GlMesh(list.toFloatArray(), 24)
    }
  }
}

class GlShaderProgram {
  val programId: Int
  val aPosition: Int
  val aNormal: Int
  val aTexCoord: Int
  private val uMVP: Int
  private val uModel: Int
  private val uTexture: Int
  private val uColor: Int
  private val uEmissive: Int
  private val uTexRepeat: Int
  private val uSunDir: Int
  private val uSunColor: Int
  private val uHemiSky: Int
  private val uHemiGround: Int
  private val uFogColor: Int
  private val uFogDensity: Int
  private val uCameraPos: Int

  val boxMesh = GlMesh.createBox()
  val cylinderMesh = GlMesh.createCylinder(16)
  val coinMesh = GlMesh.createCylinder(14)
  val octaMesh = GlMesh.createOctahedron()

  val projMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val vpMatrix = FloatArray(16)
  val modelMatrix = FloatArray(16)
  val mvpMatrix = FloatArray(16)

  init {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
    programId = GLES20.glCreateProgram()
    GLES20.glAttachShader(programId, vs)
    GLES20.glAttachShader(programId, fs)
    GLES20.glLinkProgram(programId)
    GLES20.glDeleteShader(vs)
    GLES20.glDeleteShader(fs)

    aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
    aNormal = GLES20.glGetAttribLocation(programId, "aNormal")
    aTexCoord = GLES20.glGetAttribLocation(programId, "aTexCoord")

    uMVP = GLES20.glGetUniformLocation(programId, "uMVP")
    uModel = GLES20.glGetUniformLocation(programId, "uModel")
    uTexture = GLES20.glGetUniformLocation(programId, "uTexture")
    uColor = GLES20.glGetUniformLocation(programId, "uColor")
    uEmissive = GLES20.glGetUniformLocation(programId, "uEmissive")
    uTexRepeat = GLES20.glGetUniformLocation(programId, "uTexRepeat")
    uSunDir = GLES20.glGetUniformLocation(programId, "uSunDir")
    uSunColor = GLES20.glGetUniformLocation(programId, "uSunColor")
    uHemiSky = GLES20.glGetUniformLocation(programId, "uHemiSky")
    uHemiGround = GLES20.glGetUniformLocation(programId, "uHemiGround")
    uFogColor = GLES20.glGetUniformLocation(programId, "uFogColor")
    uFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
    uCameraPos = GLES20.glGetUniformLocation(programId, "uCameraPos")
  }

  fun beginFrame(
    camX: Float,
    camY: Float,
    camZ: Float,
    sunColor: FloatArray,
    hemiSky: FloatArray,
    hemiGround: FloatArray,
    fogColor: FloatArray,
    fogDensity: Float,
  ) {
    GLES20.glUseProgram(programId)
    Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
    GLES20.glUniform3f(uSunDir, 0.56f, 0.74f, 0.37f)
    GLES20.glUniform3fv(uSunColor, 1, sunColor, 0)
    GLES20.glUniform3fv(uHemiSky, 1, hemiSky, 0)
    GLES20.glUniform3fv(uHemiGround, 1, hemiGround, 0)
    GLES20.glUniform3fv(uFogColor, 1, fogColor, 0)
    GLES20.glUniform1f(uFogDensity, fogDensity)
    GLES20.glUniform3f(uCameraPos, camX, camY, camZ)
    GLES20.glUniform1i(uTexture, 0)
  }

  fun drawMesh(
    mesh: GlMesh,
    textureId: Int,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    emissiveR: Float = 0f,
    emissiveG: Float = 0f,
    emissiveB: Float = 0f,
    repeatX: Float = 1f,
    repeatY: Float = 1f,
  ) {
    Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
    GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)
    GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
    GLES20.glUniform4f(uColor, r, g, b, a)
    GLES20.glUniform3f(uEmissive, emissiveR, emissiveG, emissiveB)
    GLES20.glUniform2f(uTexRepeat, repeatX, repeatY)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    mesh.bindAndDraw(aPosition, aNormal, aTexCoord)
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    return shader
  }

  companion object {
    private const val VERTEX_SHADER = """
      uniform mat4 uMVP;
      uniform mat4 uModel;
      uniform vec2 uTexRepeat;
      attribute vec3 aPosition;
      attribute vec3 aNormal;
      attribute vec2 aTexCoord;
      varying vec3 vWorldPos;
      varying vec3 vWorldNormal;
      varying vec2 vUv;
      void main() {
        vec4 worldPos = uModel * vec4(aPosition, 1.0);
        vWorldPos = worldPos.xyz;
        vWorldNormal = normalize(mat3(uModel) * aNormal);
        vUv = aTexCoord * uTexRepeat;
        gl_Position = uMVP * vec4(aPosition, 1.0);
      }
    """

    private const val FRAGMENT_SHADER = """
      precision mediump float;
      uniform sampler2D uTexture;
      uniform vec4 uColor;
      uniform vec3 uEmissive;
      uniform vec3 uSunDir;
      uniform vec3 uSunColor;
      uniform vec3 uHemiSky;
      uniform vec3 uHemiGround;
      uniform vec3 uFogColor;
      uniform float uFogDensity;
      uniform vec3 uCameraPos;
      varying vec3 vWorldPos;
      varying vec3 vWorldNormal;
      varying vec2 vUv;
      void main() {
        vec3 n = normalize(vWorldNormal);
        float ndl = max(dot(n, normalize(uSunDir)), 0.0);
        float hemiMix = n.y * 0.5 + 0.5;
        vec3 ambient = mix(uHemiGround, uHemiSky, hemiMix) * 0.62;
        vec3 lit = ambient + uSunColor * ndl * 0.92;
        vec4 tex = texture2D(uTexture, vUv);
        vec3 base = tex.rgb * uColor.rgb;
        vec3 col = base * lit + uEmissive;
        float dist = length(uCameraPos - vWorldPos);
        float fogFactor = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        col = mix(col, uFogColor, fogFactor);
        gl_FragColor = vec4(col, uColor.a * tex.a);
      }
    """
  }
}
