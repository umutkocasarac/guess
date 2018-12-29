package io.umut.guess.client


import io.reactivex.Observable
import io.umut.guess.BuildConfig
import io.umut.guess.client.model.GuessDTO
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FileUploadService {

    @Multipart
    @POST("guess")
    fun checkFile(@Part file: MultipartBody.Part): Observable<GuessDTO>


    companion object {
        fun create(): FileUploadService {

            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(
                    RxJava2CallAdapterFactory.create()
                )
                .addConverterFactory(
                    GsonConverterFactory.create()
                )
                .baseUrl(BuildConfig.API_URL)
                .build()

            return retrofit.create(FileUploadService::class.java)
        }
    }
}