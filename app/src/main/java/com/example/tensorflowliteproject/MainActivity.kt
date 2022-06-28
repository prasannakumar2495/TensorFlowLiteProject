package com.example.tensorflowliteproject

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tensorflowliteproject.databinding.ActivityMainBinding
import com.example.tensorflowliteproject.ml.LiteModelAiyVisionClassifierBirdsV13
import org.tensorflow.lite.support.image.TensorImage
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val GALLERY_REQUEST_CODE = 123
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val captureImage = binding.captureImage
        val loadImage = binding.loadImage
        captureImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            ) {
                takePicturePreview.launch(null)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
        loadImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val intent =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                intent.type = "image/*"
                val mimeTypes = arrayOf("image/jpeg", "image/png", "image/jpg")
                intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                onResult.launch(intent)
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }

        binding.resultTV.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${binding.resultTV.text}"))
            startActivity(intent)
        }

        //to download image when longPress on ImageView
        binding.imageView.setOnLongClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return@setOnLongClickListener true
        }
    }

    //to download the image to device

    private val requestPermissionLauncher =
        registerForActivityResult((ActivityResultContracts.RequestPermission())) { isGranted: Boolean ->
            if (isGranted) {
                AlertDialog.Builder(this).setTitle("Download Image?")
                    .setMessage("Do you want to download this image?")
                    .setPositiveButton("Yes") { _, _ ->
                        val drawable = binding.imageView.drawable as BitmapDrawable
                        val bitmap = drawable.bitmap
                        downloadImage(bitmap)
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                Toast.makeText(this,
                    "Please allow permission to download the image.",
                    Toast.LENGTH_SHORT).show()
            }
        }

    //function that takes a bitmap and store to user device
    private fun downloadImage(mBitmap: Bitmap): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,
                "Birds_Image" + System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (uri != null) {
            contentResolver.insert(uri, contentValues)?.also {
                contentResolver.openOutputStream(it).use { outputStream ->
                    if (!mBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                        throw IOException("Couldn't save the bitmap")
                    } else {
                        Toast.makeText(this, "Image Saved", Toast.LENGTH_SHORT).show()
                    }
                }
                return it
            }
        }
        return null
    }

    //to get image from gallery

    private val onResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.i("TAG", "This is the result: ${it.data} ${it.resultCode}")
            onResultReceived(GALLERY_REQUEST_CODE, it)
        }

    private fun onResultReceived(requestCode: Int, result: ActivityResult?) {
        when (requestCode) {
            GALLERY_REQUEST_CODE -> {
                if (result?.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        Log.i("TAG", "onResultReceived: $uri")
                        val bitmap =
                            BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        binding.imageView.setImageBitmap(bitmap)
                        outputGenerator(bitmap)
                    }
                } else {
                    Log.e("TAG", "onResultReceived: error in selecting image")
                }
            }
        }
    }

    private fun outputGenerator(bitmap: Bitmap) {
        val model = LiteModelAiyVisionClassifierBirdsV13.newInstance(this)

        // converting bitmap into tensor flow image
        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val tfImage = TensorImage.fromBitmap(newBitmap)

        // process the image using trained model and sort it in descending order
        val outputs = model.process(tfImage)
            .probabilityAsCategoryList.apply {
                sortByDescending { it.score }
            }

        //getting result having high probability
        val highProbabilityOutput = outputs[0]

        //setting output text
        binding.resultTV.text = highProbabilityOutput.label
        Log.i("TAG", "outputGenerator: $highProbabilityOutput")

        // Releases model resources if no longer used.
        model.close()
    }

    //request camera permission
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                takePicturePreview.launch(null)
            } else {
                Toast.makeText(this, "Permission Denied!!", Toast.LENGTH_SHORT).show()
            }
        }

    //launch camera and take picture
    private val takePicturePreview =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            if (it != null) {
                binding.imageView.setImageBitmap(it)
                outputGenerator(it)
            }
        }
}