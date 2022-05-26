package com.example.DGSB_front

import okhttp3.MultipartBody;
import okhttp3.ResponseBody
import retrofit2.Call;
import retrofit2.http.*


interface NetworkService {

    @Multipart
    @POST("api/handle-detection/")
    fun uploadFile(@Part file: MultipartBody.Part): Call<ResponseBody>

    @Multipart
    @POST("api/register-face/")
    fun registerFace(@Part("name") name: String, @Part file: Array<MultipartBody.Part?>): Call<ResponseBody>

    @GET("admin/")
    fun getAuth(): Call<ResponseBody>
}