package com.example.DGSB_front

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var exitApp: Button
    lateinit var startCamera: Button
    lateinit var faceRegister: Button
    lateinit var getFaceFromPhotoButton: AppCompatButton
    lateinit var getFaceFromGalleryButton: AppCompatButton

    private val baseUrl: String = "http://34.229.31.234:8000/"
    private var networkService: NetworkService? = null

    private fun String.initNetwork() {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(this)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        networkService = retrofit.create(NetworkService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exitApp = findViewById(R.id.exitApp)
        startCamera = findViewById(R.id.startCamera)
        faceRegister = findViewById(R.id.faceRegister)

        baseUrl.initNetwork()

        val dialogExit = android.app.AlertDialog.Builder(this@MainActivity)
        dialogExit.setView(R.layout.dialog)

        val dialogView: View = layoutInflater.inflate(R.layout.gallery_or_picture_dialog, null)

        val faceDialog = Dialog(this@MainActivity)
        faceDialog.setContentView(R.layout.gallery_or_picture_dialog)
        faceDialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        faceDialog.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)


        exitApp.setOnClickListener {
            showMessageDialog()
        }

        faceRegister.setOnClickListener {
            val intent = Intent(applicationContext, FaceCameraActivity::class.java)
            startActivity(intent)
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