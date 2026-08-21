package com.example.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.concurrent.TimeUnit

interface CatboxApiService {
    @Multipart
    @POST("user/api.php")
    suspend fun uploadFile(
        @Part("reqtype") reqtype: RequestBody,
        @Part("userhash") userhash: RequestBody?,
        @Part fileToUpload: MultipartBody.Part
    ): Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://catbox.moe/"
        const val DEFAULT_USERHASH = "9522593a4a22790d1bf20a178"

        fun create(): CatboxApiService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .build()

            return retrofit.create(CatboxApiService::class.java)
        }
    }
}
