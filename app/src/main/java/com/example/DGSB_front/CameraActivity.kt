package com.example.DGSB_front


import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import org.opencv.android.*
import org.opencv.core.CvException
import org.opencv.core.Mat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.util.*
import java.io.ByteArrayOutputStream
import java.io.IOException

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient

import okhttp3.logging.HttpLoggingInterceptor





class CameraActivity: AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2, TextToSpeech.OnInitListener {
    private var matInput: Mat? = null //openCV에서 가장 기본이 되는 구조체. Matrix
    private var matResult: Mat? = null
    private val baseUrl: String = "http://15.164.143.254:8000/"
    private var currentPhotoPath: String = ""
    private var headers: MutableMap<String, String> = mutableMapOf()
    private var filePath: String? = ""
    private var tts: TextToSpeech? = null

    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var networkService: NetworkService? = null
    lateinit var cameraBtn: AppCompatButton
    external fun ConvertRGBtoGray(matAddrInput: Long, matAddrResult: Long)
    external fun convertMatToArray(matAddr: Long, array: ByteArray)

    companion object {
        private const val TAG = "opencv"

        //여기서부턴 퍼미션 관련 메소드
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200


        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("opencv_java4")
            System.loadLibrary("native-lib")
        }
    }

    private fun initNetwork(baseURL: String){
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(baseURL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        networkService = retrofit.create(NetworkService::class.java)
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                SUCCESS -> {
                    mOpenCvCameraView!!.enableView()
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }
    //모듈을 로드하는 함수

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        ) //전체화면 만들기
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        ) //액티비티 화면 켜짐 유지
        setContentView(R.layout.activity_camera)

        mOpenCvCameraView = findViewById<View>(R.id.activity_surface_view) as CameraBridgeViewBase
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)
        mOpenCvCameraView!!.setCameraIndex(0) // front-camera(1),  back-camera(0)
        cameraBtn = findViewById<AppCompatButton>(R.id.cameraBtn)
        tts = TextToSpeech(this, this)

        initNetwork(baseUrl)

        cameraBtn.setOnClickListener {
            //onButtonClicked()
            speakOut()
        }
    }


    @SuppressLint("QueryPermissionsNeeded")
    private fun onButtonClicked(){

        var bmp: Bitmap? = null
        try {
            bmp = Bitmap.createBitmap(matResult!!.cols(), matResult!!.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matResult, bmp)
        } catch (e: CvException) {
            Log.d("Exception", e.message!!)
        }
        var uri: Uri? = null
        uri = getImageUri(applicationContext, bmp!!)
        Log.d("Uri: ", uri.toString())
        filePath = getRealPathFromURI(uri!!)
        Log.d("Uri Path: ", filePath.toString())
        uploadPhoto(filePath!!)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && resultCode == RESULT_OK) {
            val test = Uri.fromFile(File(currentPhotoPath))
            saveFile(test)
        }
    }

    private fun getRealPathFromURI(contentURI: Uri): String? {
        val result: String?
        val cursor = contentResolver.query(contentURI, null, null, null, null)
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.path
        } else {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            result = cursor.getString(idx)
            cursor.close()
        }
        return result
    }


    // 파일 저장
    private fun saveFile(image_uri: Uri) {
        val values = ContentValues()
        val fileName = "DGSB_FRONT_" + System.currentTimeMillis() + ".jpg"
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/*")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val contentResolver = contentResolver
        val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        try {
            val pdf = contentResolver.openFileDescriptor(item!!, "w", null)
            if (pdf == null) {
                Log.d("DGSB_FRONT", "null")
            } else {
                val inputData: ByteArray = getBytes(image_uri)!!
                val fos = FileOutputStream(pdf.fileDescriptor)
                fos.write(inputData)
                fos.close()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(item, values, null, null)
                }

                // 갱신
                //galleryAddPic(fileName)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Log.d("DGSB_FRONT", "FileNotFoundException  : " + e.localizedMessage)
        } catch (e: Exception) {
            Log.d("DGSB_FRONT", "FileOutputStream = : " + e.message)
        }
    }

    // Uri to ByteArr
    @Throws(IOException::class)
    fun getBytes(image_uri: Uri?): ByteArray? {
        val iStream = contentResolver.openInputStream(image_uri!!)
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024 // 버퍼 크기
        val buffer = ByteArray(bufferSize) // 버퍼 배열
        var len = 0
        // InputStream에서 읽어올 게 없을 때까지 바이트 배열에 쓴다.
        while (iStream!!.read(buffer).also { len = it } != -1) byteBuffer.write(buffer, 0, len)
        return byteBuffer.toByteArray()
    }

    private fun getImageUri(context: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(context.contentResolver, inImage, "DGSB_FRONT_" + System.currentTimeMillis() + ".jpg", null)
        return Uri.parse(path)
    }

    public override fun onPause() {
        super.onPause()
        if (mOpenCvCameraView != null) mOpenCvCameraView!!.disableView()
    }

    public override fun onResume() {
        super.onResume()
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.")
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback)
        } else {
            Log.d(TAG, "onResume :: OpenCV library found inside package. Using it!")
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if(tts == null){
            tts!!.stop()
            tts!!.shutdown()
        }
        if (mOpenCvCameraView != null) mOpenCvCameraView!!.disableView()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}
    override fun onCameraViewStopped() {}
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {

        matInput = inputFrame.rgba()
        //matInput 객체의 원래 주소를 얻어 그레이스케일한 후 matResult의 nativeObjAddr에 값 부여
        if (matResult == null) matResult = Mat(matInput!!.rows(), matInput!!.cols(), matInput!!.type())
        matResult = matInput!!.clone()
        return matResult!!
        //matResult를 반환한다
    }

    private val cameraViewList: List<CameraBridgeViewBase>
        get() = listOf(mOpenCvCameraView) as List<CameraBridgeViewBase>


    private fun uploadPhoto(image_path: String) {
        val imageFile = File(image_path)
        var body: MultipartBody.Part? = null
        val requestBody = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), imageFile)
        body = MultipartBody.Part.createFormData("photo", imageFile.name, requestBody)
        Log.d("nama file e cuk", imageFile.name)
        val call = networkService!!.uploadFile(body)
        call!!.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if(response.isSuccessful){
                    Log.i("project", "Success")
                    val resultBody = response.body()
                    val resultString: String = resultBody!!.string()
                    Log.i("header_result", response.headers().toString())
                    Log.i("body_result", resultString)
                }else run {
                    val statusCode: Int = response.code()
                    Log.i("project", "StatusCode: " + statusCode)
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.i("project", "FailMessage: " + t.message)
            }
        })
    }

    private fun getAuth(){
        val call = networkService!!.getAuth()
        call.enqueue(object : Callback<ResponseBody?> {
            override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                if(response.isSuccessful){
                    Log.i("project", "Success")
                    Log.i("response", response.headers().toString())
                    val cookies = response.headers()["Set-Cookie"]
                    val cookie = cookies!!.split("=", ";")
                    val token = cookie[1]
                    headers["X-CSRFToken"] = token
                    uploadPhoto(filePath!!)
                }else run {
                    val statusCode: Int = response.code()
                    Log.i("project", "StatusCode: " + statusCode)
                }
            }

            override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                Log.i("project", "FailMessage: " + t.message)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        var havePermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
                havePermission = false
            }
        }
        if (havePermission) {
            onCameraPermissionGranted()
        }
    }

    private fun onCameraPermissionGranted() {
        val cameraViews = cameraViewList
        for (cameraBridgeViewBase in cameraViews) {
            cameraBridgeViewBase.setCameraPermissionGranted()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            onCameraPermissionGranted()
        } else {
            showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.")
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun showDialogForPermission(msg: String) {
        val builder = AlertDialog.Builder(this@CameraActivity)
        builder.setTitle("알림")
        builder.setMessage(msg)
        builder.setCancelable(false)
        builder.setPositiveButton(
            "예"
        ) { _, _ ->
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
        builder.setNegativeButton(
            "아니오"
        ) { _, _ -> finish() }
        builder.create().show()
    }

    override fun onInit(status: Int) {
        if(status == TextToSpeech.SUCCESS){
            val result: Int = tts!!.setLanguage(Locale.KOREA)
            if(result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA){
                Log.e("TTS", "This Language is not supported")
            } else {
                speakOut()
            }
        } else {
            Log.e("TTS", "Initialization Failed")
        }
    }

    private fun speakOut() {
        val text: CharSequence = "안녕하세요."
        tts!!.setPitch(0.6f)
        tts!!.setSpeechRate(0.1f)
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id1")
    }
}