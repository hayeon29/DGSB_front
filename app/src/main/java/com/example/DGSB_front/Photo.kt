package com.example.DGSB_front


import android.graphics.Bitmap
import android.os.Build
import java.time.LocalDateTime

class Photo(photo: String) {
    var photo: String? = photo
    var created: String? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.created = LocalDateTime.now().toString()
        }else{
            this.created = "2000-01-01"
        }
    }
}