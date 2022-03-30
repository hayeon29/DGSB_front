package com.example.teamproject6

import android.Manifest.permission
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat

class MainActivity : AppCompatActivity() {

    lateinit var exitApp: Button
    lateinit var startCamera: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exitApp = findViewById(R.id.exitApp)
        startCamera = findViewById(R.id.startCamera)

        val dialog_exit = android.app.AlertDialog.Builder(this@MainActivity)
        dialog_exit.setView(R.layout.dialog)

        exitApp.setOnClickListener {
            showMessageDialog()
        }

        startCamera.setOnClickListener {
            val intent = Intent(applicationContext, CameraActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showMessageDialog(){
        val customDialog = CustomDialog(finishApp = {finish()})
        customDialog.show(supportFragmentManager, "CustomDialog")
    }

    public override fun onDestroy() {
        super.onDestroy()
        finish()
    }


}