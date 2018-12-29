package io.umut.guess

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.TextView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.umut.guess.BuildConfig.APPLICATION_ID
import io.umut.guess.client.FileUploadService
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    val fileUploadServe by lazy {
        FileUploadService.create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun openCamera(view: View) {
        Log.v(TAG, "openCamera")
        dispatchTakePictureIntent()
    }

    var mCurrentPhotoPath: String = ""

    @Throws(IOException::class)
    private fun createImageFile(): File {
        Log.v(TAG, "createImageFile")

        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            Log.v(TAG, absolutePath)
            mCurrentPhotoPath = absolutePath
        }
    }

    val REQUEST_TAKE_PHOTO = 1

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(TAG, ex.message)
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        APPLICATION_ID,
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_TAKE_PHOTO) {
            if (resultCode == Activity.RESULT_OK) {
                val file = File(mCurrentPhotoPath);
                val requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file)
                val body = MultipartBody.Part.createFormData("file", file.getName(), requestFile)
                val showResultTextView = findViewById<TextView>(R.id.textView)
                var disposable : Disposable?= null
                disposable = fileUploadServe.checkFile(body).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe {  showResultTextView.text = "Guessing, Please wait" }
                    .subscribe({ r ->
                        Log.i(TAG, "Image uploaded2 " + r.toString())
                        Log.i(TAG, "Image uploaded3 " + r.guesses.joinToString())
                        // Show with https://www.google.com/search?q=asd
                        showResultTextView.text = r.guesses.joinToString()
                    },{e->
                        Log.i(TAG, "Image upload failed " + e.message)
                        showResultTextView.text = "Image upload failed " + e.message
                        disposable?.dispose();
                    }, {
                        Log.i(TAG, "Image uploaded completed")
                        disposable?.dispose();
                    })
            }
        }
    }
}
