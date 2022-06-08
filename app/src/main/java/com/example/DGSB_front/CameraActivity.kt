package com.example.DGSB_front

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import org.opencv.android.*
import org.opencv.core.CvException
import org.opencv.core.Mat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.TimeUnit

class CameraActivity: AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2, TextToSpeech.OnInitListener {
    private var matInput: Mat? = null //openCV에서 가장 기본이 되는 구조체. Matrix
    private var matResult: Mat? = null
    private var recording: Boolean = false
    private val baseUrl: String = "http://3.39.167.118:8000/"
    private var currentPhotoPath: String = ""
    private var headers: MutableMap<String, String> = mutableMapOf()
    private var filePath: String? = ""
    private var tts: TextToSpeech? = null
    private val sound: MediaActionSound = MediaActionSound()
    private var speechRecognizer: SpeechRecognizer? = null

    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var networkService: NetworkService? = null
    lateinit var cameraBtn: AppCompatButton

    companion object {
        private const val TAG = "opencv"

        //여기서부턴 퍼미션 관련 메소드
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200


        init {
            System.loadLibrary("opencv_java4")
        }
    }

    private fun String.initNetwork() {
        val interceptor = HttpLoggingInterceptor()
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(interceptor)
            .connectTimeout(10, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .build()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(this)
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


        intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")

        baseUrl.initNetwork()

        cameraBtn.setOnClickListener {
            onButtonClicked()
        }


    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun onButtonClicked(){
        sound.play(MediaActionSound.SHUTTER_CLICK)
        var bmp: Bitmap? = null
        try {
            bmp = Bitmap.createBitmap(matResult!!.cols(), matResult!!.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matResult, bmp)
            bmp = rotateBitmap(bmp, 90f)
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
        if(tts != null){
            tts!!.stop()
            tts!!.shutdown()
            tts = null
        }
        if(speechRecognizer != null){
            speechRecognizer!!.destroy()
            speechRecognizer!!.cancel()
            speechRecognizer = null
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
        val call = networkService!!.uploadFile(body)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if(response.isSuccessful){
                    Log.i("project", "Success")
                    val resultBody = response.body()
                    val resultString: String = String(resultBody!!.bytes(), Charset.forName("utf-8"))
                    Log.i("body_result", resultString)

                    val objects = JSONObject(resultString).getString("object")
                    val text = JSONObject(resultString).getString("text")
                    val face = JSONObject(resultString).getString("face")



                    val detectResult: DetectResult = DetectResult(objects, text, face)
                    if (detectResult.objects == "null" || detectResult.objects == "[]"){
                        detectResult.objects = ""
                    } else {
                        detectResult.objects = "물체, " + detectResult.objects
                    }
                    if (detectResult.text == "null" || detectResult.text == "[]"){
                        detectResult.text = ""
                    } else {
                        detectResult.text = "텍스트, " + detectResult.text
                    }
                    if (detectResult.face == "null" || detectResult.face == "[]"){
                        detectResult.face = ""
                    } else {
                        detectResult.face = "얼굴, " + detectResult.face
                    }
                    Log.i("body_result", detectResult.objects + ", " + detectResult.text + ", " + detectResult.face)
                    val detectResultString: String = detectResult.objects + detectResult.text + detectResult.face
                    if(detectResult.objects == "" && detectResult.text == "" && detectResult.face == ""){
                        showToast("발견된 물체가 없습니다.")
                        speakOut("발견된 물체가 없습니다.")
                    } else {
                        showToast(detectResultString + "발견!")
                        speakOut(detectResultString + "발견!")
                    }

                }else run {
                    val statusCode: Int = response.code()
                    Log.i("project", "StatusCode: $statusCode")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Log.i("project", "FailMessage: " + t.message)
            }
        })
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun showToast(msg: String){
        val inflater: View = LayoutInflater.from(this).inflate(R.layout.toast,null)
        val toastMsg= inflater.findViewById<TextView>(R.id.toast_msg)
        toastMsg.text = msg

        var toast: Toast = Toast(this)
        toast.view = inflater
        toast.show()
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
                    Log.i("project", "StatusCode: $statusCode")
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
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET,
                        Manifest.permission.RECORD_AUDIO
                    ),
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
            "앱을 실행하려면 퍼미션을 허가하셔야합니다.".showDialogForPermission()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun String.showDialogForPermission() {
        val builder = AlertDialog.Builder(this@CameraActivity)
        builder.setTitle("알림")
        builder.setMessage(this)
        builder.setCancelable(false)
        builder.setPositiveButton(
            "예"
        ) { _, _ ->
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.RECORD_AUDIO
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
                Log.d("TTS", "success")
            }
        } else {
            Log.e("TTS", "Initialization Failed")
        }
    }

    private fun speakOut(result: String) {
        val text: CharSequence = result
        tts!!.setPitch(1.0f)
        tts!!.setSpeechRate(1.0f)
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "id1")
    }
}