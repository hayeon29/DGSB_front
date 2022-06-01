package com.example.DGSB_front

import com.google.gson.annotations.SerializedName

data class DetectResult(
    var objects: String,
    val text: String,
    val face: String
)