package com.example.DGSB_front

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

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