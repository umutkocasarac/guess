package io.umut.guess.client


import io.reactivex.Observable
import io.umut.guess.BuildConfig
import io.umut.guess.client.model.AuthenticationInterceptor
import io.umut.guess.client.model.GuessDTO
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit


interface FileUploadService {

    @Multipart
    @POST("guess")
    fun checkFile(@Part file: MultipartBody.Part): Observable<GuessDTO>


    companion object {
        fun create(): FileUploadService {
            val okHttpClient = OkHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(AuthenticationInterceptor(BuildConfig.API_USERNAME, BuildConfig.API_PASSWORD))
                .build()

            val retrofit = Retrofit.Builder()
                .addCallAdapterFactory(
                    RxJava2CallAdapterFactory.create()
                )
                .addConverterFactory(
                    GsonConverterFactory.create()
                )
                .client(okHttpClient)
                .baseUrl(BuildConfig.API_URL)
                .build()

            return retrofit.create(FileUploadService::class.java)
        }
    }
}