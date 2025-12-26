package com.example.myliveapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView

class MainActivity : AppCompatActivity() {

  private lateinit var openGlView: OpenGlView
  private lateinit var etRtmp: EditText
  private lateinit var btnStart: Button
  private lateinit var btnStop: Button
  private lateinit var tvStatus: TextView

  private lateinit var rtmpCamera: RtmpCamera2
  private val bitrateAdapter = BitrateAdapter()

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      val cameraOk = result[Manifest.permission.CAMERA] == true
      val micOk = result[Manifest.permission.RECORD_AUDIO] == true
      if (cameraOk && micOk) {
        tvStatus.text = "Status: Permissions granted"
        startPreview()
      } else {
        tvStatus.text = "Status: Missing permissions"
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    openGlView = findViewById(R.id.openGlView)
    etRtmp = findViewById(R.id.etRtmp)
    btnStart = findViewById(R.id.btnStart)
    btnStop = findViewById(R.id.btnStop)
    tvStatus = findViewById(R.id.tvStatus)

    rtmpCamera = RtmpCamera2(openGlView, object : ConnectChecker {
      override fun onConnectionStarted(url: String) {
        runOnUiThread { tvStatus.text = "Status: Connecting..." }
      }
      override fun onConnectionSuccess() {
        runOnUiThread { tvStatus.text = "Status: LIVE" }
      }
      override fun onConnectionFailed(reason: String) {
        runOnUiThread {
          tvStatus.text = "Status: Failed - $reason"
          stopStream()
        }
      }
      override fun onNewBitrate(bitrate: Long) {
        bitrateAdapter.adaptBitrate(bitrate)
      }
      override fun onDisconnect() {
        runOnUiThread {
          tvStatus.text = "Status: Disconnected"
          stopStream()
        }
      }
      override fun onAuthError() {
        runOnUiThread {
          tvStatus.text = "Status: Auth error"
          stopStream()
        }
      }
      override fun onAuthSuccess() {
        runOnUiThread { tvStatus.text = "Status: Auth success" }
      }
    })

    btnStart.setOnClickListener { ensurePermissionsThenStart() }
    btnStop.setOnClickListener { stopStream() }
  }

  private fun ensurePermissionsThenStart() {
    val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    if (cameraGranted && micGranted) {
      startPreview()
    } else {
      permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
  }

  private fun startPreview() {
    if (!rtmpCamera.isOnPreview) {
      rtmpCamera.startPreview()
      tvStatus.text = "Status: Preview started"
    }
    startStream()
  }

  private fun startStream() {
    val url = etRtmp.text.toString().trim()
    if (url.isBlank()) {
      tvStatus.text = "Status: Please enter RTMP URL"
      return
    }

    val preparedVideo = rtmpCamera.prepareVideo(1280, 720, 30, 2500 * 1024, 2, 0)
    val preparedAudio = rtmpCamera.prepareAudio(128 * 1024, 44100, true, false, false)

    if (!preparedVideo || !preparedAudio) {
      tvStatus.text = "Status: Prepare encoder failed"
      return
    }

    btnStart.isEnabled = false
    btnStop.isEnabled = true
    rtmpCamera.startStream(url)
  }

  private fun stopStream() {
    if (rtmpCamera.isStreaming) {
      rtmpCamera.stopStream()
    }
    btnStart.isEnabled = true
    btnStop.isEnabled = false
  }

  override fun onDestroy() {
    super.onDestroy()
    if (rtmpCamera.isStreaming) rtmpCamera.stopStream()
    if (rtmpCamera.isOnPreview) rtmpCamera.stopPreview()
  }
}
