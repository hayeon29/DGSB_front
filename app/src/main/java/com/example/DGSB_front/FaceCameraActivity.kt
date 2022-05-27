package com.example.DGSB_front

import android.Manifest
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.opencv.android.*
import org.opencv.core.CvException
import org.opencv.core.Mat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections

import org.opencv.android.CameraBridgeViewBase
import java.util.concurrent.TimeUnit


class FaceCameraActivity: AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private var matInput: Mat? = null
    private var matResult: Mat? = null
    private var filePath: String? = ""

    private var filePathList: ArrayList<String> = ArrayList<String>()
    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    lateinit var faceCameraBtn: AppCompatButton

    companion object {
        private const val TAG = "opencv"

        //여기서부턴 퍼미션 관련 메소드
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200


        init {
            System.loadLibrary("opencv_java4")
        }
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
        setContentView(R.layout.activity_face_camera)

        mOpenCvCameraView = findViewById<View>(R.id.activity_face_surface_view) as CameraBridgeViewBase
        mOpenCvCameraView!!.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView!!.setCvCameraViewListener(this)
        mOpenCvCameraView!!.setCameraIndex(1) // front-camera(1),  back-camera(0)
        faceCameraBtn = findViewById<AppCompatButton>(R.id.faceCameraBtn)

        val getIntent: Intent = Intent()
        val size: Int = getIntent.getIntExtra("size", 10)

        faceCameraBtn.setOnClickListener {
            size.getMultiplePhoto() //size = 10
            val intent = Intent(this, FaceRegister::class.java)
            intent.putExtra("photoList", filePathList)
            Log.d("photoList", filePathList.toString())
            setResult(RESULT_OK, intent)
            if(!isFinishing){
                finish()
            }
        }
    }
    private fun Int.getMultiplePhoto() {
        for(i:Int in 0 until this){
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
            filePathList!!.add(filePath!!)
        }
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


    private fun getImageUri(context: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(context.contentResolver, inImage, "DGSB_FRONT_" + System.currentTimeMillis() + ".jpg", null)
        return Uri.parse(path)
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

    override fun onStart() {
        super.onStart()
        var havePermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
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
        val builder = AlertDialog.Builder(this@FaceCameraActivity)
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
                ),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
        builder.setNegativeButton(
            "아니오"
        ) { _, _ -> finish() }
        builder.create().show()
    }
}
