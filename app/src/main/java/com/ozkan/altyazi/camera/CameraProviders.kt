package com.ozkan.altyazi.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await

/** Suspends until the process-wide camera provider is initialized. */
suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    ProcessCameraProvider.getInstance(this).await()
