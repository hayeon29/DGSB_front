package com.example.teamproject6

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST

interface NetworkService {

    @Headers("content-type: multipart/form-data;")
    @POST(".")
    public fun post_photo(@Body photo: Photo): Call<Photo>

    @PATCH(".")
    public fun patch_photo(@Body photo: Photo): Call<Photo>
    
}