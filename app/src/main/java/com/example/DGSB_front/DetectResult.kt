package com.example.DGSB_front

import com.google.gson.annotations.SerializedName

data class DetectResult(
    var objects: String,
    var text: String,
    var face: String
)