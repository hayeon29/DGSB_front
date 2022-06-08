package com.example.DGSB_front

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.concurrent.TimeUnit

class FaceRegister: AppCompatActivity() {
    lateinit var edtName: EditText
    lateinit var cameraBtn: AppCompatButton
    lateinit var sendBtn: AppCompatButton
    lateinit var returnBtn: AppCompatButton
    private lateinit var getResultPhoto: ActivityResultLauncher<Intent>

    private var photoList: ArrayList<String>? = null
    private var photoSelected: Boolean = false
    private val size: Int = 10
    private val baseUrl: String = "http://3.39.167.118:8000/"
    private var networkService: NetworkService? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_register)

        getResultPhoto = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {result ->
            if(result.resultCode == RESULT_OK){
                photoList = result.data?.getSerializableExtra("photoList") as ArrayList<String>?
                photoSelected = true
                if(photoList!!.isNotEmpty()){
                    showToast("사진 찍기가 완료되었습니다.")
                } else {
                    showToast("사진 찍기가 실패했습니다.")
                }
            }
        }

        edtName = findViewById<EditText>(R.id.edtName)
        cameraBtn = findViewById<AppCompatButton>(R.id.cameraBtn)
        sendBtn = findViewById<AppCompatButton>(R.id.sendBtn)
        returnBtn = findViewById<AppCompatButton>(R.id.returnBtn)

        baseUrl.initNetwork()

        cameraBtn.setOnClickListener {
            val intent = Intent(this, FaceCameraActivity::class.java)
            intent.putExtra("size", size)
            startActivity(intent)
            getResultPhoto.launch(intent)
        }

        sendBtn.setOnClickListener {
            if(edtName.text.toString().isNotEmpty()){
                uploadPhoto(photoList!!, size)
            } else {
                showToast("이름을 적어주세요.")
            }
        }

        returnBtn.setOnClickListener {
            finish()
        }
    }

    private fun String.initNetwork() {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(10, TimeUnit.MINUTES)
            .build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(this)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        networkService = retrofit.create(NetworkService::class.java)
    }

    private fun uploadPhoto(image_path_array: ArrayList<String>, size: Int) {
        val bodies: Array<MultipartBody.Part?> = arrayOfNulls<MultipartBody.Part>(10)
        var requestBody: RequestBody
        var imageFile: File
        for (i:Int in 0 until size){
            imageFile = File(image_path_array[i])
            requestBody = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), imageFile)
            bodies[i] = MultipartBody.Part.createFormData("faceList", imageFile.name, requestBody)
        }

        val call = networkService!!.registerFace("name", bodies)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if(response.isSuccessful){
                    Log.i("Face", "Success")
                    val resultBody = response.body()
                    val resultString: String = resultBody!!.string()
                    Log.i("body_result", resultString)
                }else run {
                    val statusCode: Int = response.code()
                    Log.i("Face", "StatusCode: $statusCode")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.i("Face", "FailMessage: " + t.message)
            }
        })
    }

    private fun showToast(msg: String){
        val inflater: View = LayoutInflater.from(this).inflate(R.layout.toast,null)
        val toastMsg= inflater.findViewById<TextView>(R.id.toast_msg)
        toastMsg.text = msg

        var toast: Toast = Toast(this)
        toast.view = inflater
        toast.show()
    }
}