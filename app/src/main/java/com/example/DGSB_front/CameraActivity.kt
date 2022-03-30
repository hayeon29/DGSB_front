package com.example.teamproject6

import android.Manifest
import android.R.attr.bitmap
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL


class CameraActivity: AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {
    private var matInput: Mat? = null //openCV에서 가장 기본이 되는 구조체. Matrix
    private var matResult: Mat? = null
    private val baseUrl: String = "https://127.0.0.1:8000/"

    private var mOpenCvCameraView: CameraBridgeViewBase? = null
    private var networkService: NetworkService? = null
    external fun ConvertRGBtoGray(matAddrInput: Long, matAddrResult: Long) //RGB를 그레이스케일로 변환시키는 함수


    companion object {
        private const val TAG = "opencv"

        //여기서부턴 퍼미션 관련 메소드
        private const val CAMERA_PERMISSION_REQUEST_CODE = 200

        init {
            System.loadLibrary("opencv_java4")
            System.loadLibrary("native-lib")
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
    //모듈을 로드하는 함수

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

        initNetwork(baseUrl)
    }

    external fun convertMatToArray(matAddr: Mat?, array: ByteArray)

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
        var bitResult: ByteArray? = null
        matInput = inputFrame.rgba()
        bitResult = ByteArray(matInput!!.rows() * matInput!!.cols() * matInput!!.channels())
        matInput!!.get(0, 0, bitResult)
        //matInput 객체의 원래 주소를 얻어 그레이스케일한 후 matResult의 nativeObjAddr에 값 부여
        val file: Photo = Photo(Base64.encodeToString(bitResult, Base64.NO_WRAP))
        onFilePost(file)
        return matInput!!
        //matResult를 반환한다
    }

    protected val cameraViewList: List<CameraBridgeViewBase>
        get() = listOf(mOpenCvCameraView) as List<CameraBridgeViewBase>

    protected fun onCameraPermissionGranted() {
        val cameraViews = cameraViewList ?: return
        for (cameraBridgeViewBase in cameraViews) {
            cameraBridgeViewBase.setCameraPermissionGranted()
        }
    }

    private fun initNetwork(baseURL: String){
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(baseURL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        networkService = retrofit.create(NetworkService::class.java)
    }

    private fun onFilePost(file: Photo){
        val postPhoto: Call<Photo> = networkService!!.post_photo(file)
        postPhoto.enqueue(object: Callback<Photo>{
            override fun onResponse(call: Call<Photo>, response: Response<Photo>){
                if(response.isSuccessful){
                    Log.i("project", "Success")
                }else run {
                    val statusCode: Int = response.code()
                    Log.i("project", "StatusCode: " + statusCode)
                }
            }

            override fun onFailure(call: Call<Photo>, t: Throwable) {
                Log.i("project", "FailMessage: " + t.message)
            }
        })
    }

    private fun onFilePatch(file: Photo){
        val patchPhoto: Call<Photo> = networkService!!.patch_photo(file)!!
        patchPhoto.enqueue(object: Callback<Photo>{
            override fun onResponse(call: Call<Photo>, response: Response<Photo>) {
                if(response.isSuccessful){
                    Log.i("project", "Success")
                }else run {
                    val statusCode: Int = response.code()
                    Log.i("project", "StatusCode: " + statusCode)
                }
            }

            override fun onFailure(call: Call<Photo>, t: Throwable) {
                Log.i("project", "FailMessage: " + t.message)
            }
        })
    }

    private fun onFileUpload(buffer: ByteArray){
        var conn: HttpURLConnection? = null
        var outStream: DataOutputStream? = null
        var inStream: DataInputStream? = null
        val endString: String = "\r\n"
        var bufferSize: Int = buffer.size
        val urlString: String = "http://127.0.0.1:8000/"   // server ip
        try{
            val url: URL = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.requestMethod = "POST"
            conn.setRequestProperty("Connection", "Keep-Alive")
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + " ")
            outStream = DataOutputStream(conn.outputStream)
            outStream.writeBytes(endString)
            //outStream.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + path + "\"" + endString)
            outStream.writeBytes(endString)
            outStream.write(buffer, 0, bufferSize)
            outStream.writeBytes(endString)
            outStream.writeBytes(endString)

            Log.e("Debug", "File is written")
            outStream.flush()
            outStream.close()
        } catch(ex: MalformedURLException){
            Log.e("Debug", "error: " + ex.message, ex)
        } catch(ioe: IOException){
            Log.e("Debug", "error: " + ioe.message, ioe)
        }

        try{
            if (conn != null) {
                inStream = DataInputStream(conn.inputStream)
            }
            var str: String = inStream?.readLine()!!
            while(str.isEmpty()) {
                Log.e("Debug", "server response" + str)
                str = inStream.readLine()!!
            }
            inStream.close()
        } catch (ioex: IOException){
            Log.e("Debug", "error: " + ioex.message, ioex)
        }
    }



    override fun onStart() {
        super.onStart()
        var havePermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
                havePermission = false
            }
        }
        if (havePermission) {
            onCameraPermissionGranted()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
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
        ) { dialog, id ->
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
        builder.setNegativeButton(
            "아니오"
        ) { arg0, arg1 -> finish() }
        builder.create().show()
    }
}