package com.dupre.sandra.dogbreeddetector

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.custom.*
import com.google.firebase.ml.custom.model.FirebaseCloudModelSource
import com.google.firebase.ml.custom.model.FirebaseLocalModelSource
import com.google.firebase.ml.custom.model.FirebaseModelDownloadConditions
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DogDetector(private val context: Context) {

    var view: DogView? = null
    private var interpreter: FirebaseModelInterpreter? = null
    private val labels = mutableListOf<String>()
    private lateinit var dataOptions: FirebaseModelInputOutputOptions

    companion object {
        private const val IMG_SIZE = 224
        private const val ASSET = "asset"
        private const val MODEL_NAME = "dog-breed-detector"
        private const val MEAN = 128
        private const val STD = 128.0f
    }

    init {
        initializeLabels()
        initializeDataOptions()
        initializeInterpreter()
    }

    fun recognizeDog(bitmap: Bitmap) {
        try {
            val inputs = FirebaseModelInputs.Builder().add(fromBitmapToByteBuffer(bitmap)).build()
            interpreter
                ?.run(inputs, dataOptions)
                ?.addOnSuccessListener {
                    val output = it.getOutput<Array<FloatArray>>(0)
                    val label = labels
                        .mapIndexed { index, label ->
                            Pair(label, output[0][index])
                        }
                        .sortedByDescending { it.second }
                        .first()

                    view?.displayDogBreed(label.first, label.second*100)
                }
                ?.addOnFailureListener {
                    view?.displayError()
                }
        } catch (e: FirebaseMLException) {
            view?.displayError()
        }
    }

    private fun initializeLabels() {
        labels.addAll(context.assets.open("labels.txt").bufferedReader().readLines())
    }

    private fun initializeDataOptions() {
        dataOptions = FirebaseModelInputOutputOptions.Builder()
            .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, IMG_SIZE, IMG_SIZE, 3))
            .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, labels.size))
            .build()
    }

    private fun initializeInterpreter() {
        try {
            val localSource = FirebaseLocalModelSource.Builder(ASSET)
                .setAssetFilePath("$MODEL_NAME.tflite")
                .build()

            val conditions = FirebaseModelDownloadConditions.Builder().requireWifi().build()
            val cloudSource = FirebaseCloudModelSource.Builder(MODEL_NAME)
                .enableModelUpdates(true)
                .setInitialDownloadConditions(conditions)
                .setUpdatesDownloadConditions(conditions)
                .build()

            FirebaseModelManager.getInstance().apply {
                registerLocalModelSource(localSource)
                registerCloudModelSource(cloudSource)
            }

            interpreter = FirebaseModelInterpreter.getInstance(
                FirebaseModelOptions.Builder()
                    .setCloudModelName(MODEL_NAME)
                    .setLocalModelName(ASSET)
                    .build()
            )
        } catch (e: FirebaseMLException) {
            view?.displayError()
        }
    }

    private fun fromBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }

        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, false).apply {
            getPixels(pixels, 0, width, 0, 0, width, height)
        }

        pixels.forEach {
            imgData.putFloat(((it shr 16 and 0xFF) - MEAN) / STD)
            imgData.putFloat(((it shr 8 and 0xFF) - MEAN) / STD)
            imgData.putFloat(((it and 0xFF) - MEAN) / STD)
        }

        return imgData
    }
}