package com.example.objectdetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;

  public static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private PreviewView previewView;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    previewView = findViewById(R.id.previewView);

    if (hasPermission()) {
      // Start CameraX
      startCamera();
    } else {
      requestPermission();
    }
  }

  @SuppressLint("UnsafeOptInUsageError")
  private void startCamera() {
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

    cameraProviderFuture.addListener(() -> {
      // Camera provider is now guaranteed to be available
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

        // Set up the view finder use case to display camera preview
        Preview preview = new Preview.Builder().build();

        // Choose the camera by requiring a lens facing
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Image Analysis
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(DESIRED_PREVIEW_SIZE)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
          // Define rotation Degrees of the imageProxy
          int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
          Log.v("ImageAnalysis_degrees", String.valueOf(rotationDegrees));

          @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
          if (mediaImage != null) {
            InputImage image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            //Pass image to an ML Kit Vision API
            //...

            LocalModel localModel =
                    new LocalModel.Builder()
                            .setAssetFilePath("mobilenet.tflite")
                            .build();

            CustomObjectDetectorOptions customObjectDetectorOptions =
                    new CustomObjectDetectorOptions.Builder(localModel)
                            .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                            .enableClassification()
                            .setClassificationConfidenceThreshold(0.5f)
                            .setMaxPerObjectLabelCount(3)
                            .build();

            ObjectDetector objectDetector =
                    ObjectDetection.getClient(customObjectDetectorOptions);

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                      Log.d("TAG", "onSuccess" + detectedObjects.size());

                      // The list of detected objects contains one item if multiple
                      // object detection wasn't enabled.
                      for (DetectedObject detectedObject : detectedObjects) {
                        Rect boundingBox = detectedObject.getBoundingBox();
                        Integer trackingId = detectedObject.getTrackingId();
                        for (DetectedObject.Label label : detectedObject.getLabels()) {
                          String text = label.getText();

                          Log.v("DETECTIONS", text);
                                    /*if (PredefinedCategory.FOOD.equals(text)) {
            ...
                                    }
                                    int index = label.getIndex();
                                    if (PredefinedCategory.FOOD_INDEX == index) {
            ...
                                    }*/
                          float confidence = label.getConfidence();
                          Log.v("DETECTIONS_conf", String.valueOf(confidence));
                        }
                      }
                    })
                    .addOnFailureListener(e -> Log.e("TAG", e.getLocalizedMessage()))
                    .addOnCompleteListener(result -> imageProxy.close());
          }

        });

        // Connect the preview use case to the previewView
        preview.setSurfaceProvider(
                previewView.getSurfaceProvider());

        // Attach use cases to the camera with the same lifecycle owner
        if (cameraProvider != null) {
          Camera camera = cameraProvider.bindToLifecycle(
                  this,
                  cameraSelector,
                  imageAnalysis,
                  preview);
        }

      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }


    }, ContextCompat.getMainExecutor(this));
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
                .show();
      }
      requestPermissions(new String[]{PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  @Override
  public void onRequestPermissionsResult(
          final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        // Start CameraX
        startCamera();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }
}