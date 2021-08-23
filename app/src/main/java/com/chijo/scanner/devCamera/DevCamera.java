package com.chijo.scanner.devCamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.chijo.scanner.AppConstants;
import com.chijo.scanner.Line;
import com.chijo.scanner.R;
import com.chijo.scanner.imageAnalysis.ImageHelper;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DevCamera extends AppCompatActivity {

    //perms
    private final int CAMERA_REQUEST_CODE = 1;
    //preview:
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    //camera UI:
    private Button takePictureButton;
    private Button doneButton;
    private ImageView pictureImageView;
    //Camera:
    private Camera camera;
    private CameraControl cameraControl;

    private String documentFolderPath;

    //page tracking:
    private DevCameraOverlay cameraOverlay;
    private final double trackTolerance = 0.2;
    private double trackArea = 0;
    private List<Line> edges = new ArrayList<>();
    private List<Point> corners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_camera);

        documentFolderPath = AppConstants.DEV_SAVE_LOCATION;

        previewView = (PreviewView) findViewById(R.id.dev_camera_preview_view);
        takePictureButton = (Button) findViewById(R.id.dev_button_take_picture);
        doneButton = (Button) findViewById(R.id.dev_button_done_pictures);
        pictureImageView = (ImageView) findViewById(R.id.dev_analysis_output);
        cameraOverlay = (DevCameraOverlay) findViewById(R.id.dev_camera_preview_view_overlay);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        //ask for camera permissions
        cameraPermissionCheck();
        setupCameraProviderListener();
        setupScreenActions();
        setupButtons();
    }

    private void setupCameraProviderListener() {
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    configureCamera(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                    Toast.makeText(com.chijo.scanner.devCamera.DevCamera.this, "ruh roh... that shouldn't have happened", Toast.LENGTH_LONG).show();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void configureCamera(@NonNull ProcessCameraProvider cameraProvider) {
        //bind preview:
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        //configure for image analysis:
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Mat mat = new Mat();
                Bitmap imageBM = ImageHelper.toBitmap(image);
                if(imageBM != null) {
                    Utils.bitmapToMat(imageBM, mat);
                    int rotation = image.getImageInfo().getRotationDegrees();
                    analyzeImage(imageBM, mat, rotation);
                }
                image.close();
            }
        });

        //configure for taking photos:
        imageCapture = new ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(previewView.getDisplay().getRotation())
                .build();

        camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageCapture, imageAnalysis, preview);
        cameraControl = camera.getCameraControl();

    }

    private void analyzeImage(Bitmap originalBitmap, Mat originalMat, int rotation) {
        //colour clustering via kmeans (too slow)
        /*
        Mat processedImage = ImageHelper.kmeansColourClustering(originalMat, 5);
        */

        Mat processedImage = ImageHelper.processImage(originalMat);

        //Core.rotate(processedImage, processedImage, Core.ROTATE_90_CLOCKWISE);
        Utils.matToBitmap(processedImage, originalBitmap);

        pictureImageView.setImageBitmap(originalBitmap);
        pictureImageView.setPivotX(pictureImageView.getWidth() / 2);
        pictureImageView.setPivotY(pictureImageView.getHeight() / 2);
        pictureImageView.setRotation(90);
    }

    private void setupScreenActions() {
        ScaleGestureDetector.SimpleOnScaleGestureListener listener = new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                cameraControl.setZoomRatio(camera.getCameraInfo().getZoomState().getValue().getZoomRatio() * scaleGestureDetector.getScaleFactor());
                return true;
            }
        };
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this, listener);
        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getX();
                    float y = event.getY();
                    MeteringPointFactory factory = previewView.getMeteringPointFactory();
                    MeteringPoint point = factory.createPoint(x, y);
                    FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
                    cameraControl.startFocusAndMetering(action);
                }
                return true;
            }
        });
    }

    private void setupButtons() {
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                done();
            }
        });
    }

    private void takePicture() {
        long fileName = System.currentTimeMillis();
        File file = new File(documentFolderPath + fileName + ".jpg");
        if(!file.exists()) {
            file.getParentFile().mkdirs();
        }
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {

                    }
                    @Override
                    public void onError(ImageCaptureException error) {
                        // insert your code here.
                        Toast.makeText(com.chijo.scanner.devCamera.DevCamera.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void cameraPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                done();
            }
        }
    }

    private void done() {
        finish();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        //prevent orientation from changing
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onBackPressed() {
        done();
    }
}
