package com.chijo.scanner.devCamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import com.chijo.scanner.pageviewer.PageAnimator;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

    //dev pictures
    private Bitmap analyzedOutput = null;

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

        //Mat processedImage = ImageHelper.processImage(originalMat);
        Mat processedImage = ImageHelper.getImageOutline(originalMat);
        Mat contourOnly = ImageHelper.findContours(processedImage, originalMat);

        //approximate lines
        List<Line> lines = ImageHelper.approximateEdges(contourOnly);
        //Mat finalEdges = ImageHelper.definitiveEdges(approximateEdges);

        //only keep the 4 longest lines
        Collections.sort(lines, new Line.LengthComparator());
        while(lines.size() > 4) {
            lines.remove(lines.size() - 1);
        }

        if (lines.size() == 4) {
            Imgproc.line(contourOnly, new Point(lines.get(0).p1.x, lines.get(0).p1.y), new Point(lines.get(0).p2.x, lines.get(0).p2.y), new Scalar(255, 0, 0), 4, Imgproc.LINE_AA, 0);
            Imgproc.line(contourOnly, new Point(lines.get(1).p1.x, lines.get(1).p1.y), new Point(lines.get(1).p2.x, lines.get(1).p2.y), new Scalar(255, 0, 0), 4, Imgproc.LINE_AA, 0);
            Imgproc.line(contourOnly, new Point(lines.get(2).p1.x, lines.get(2).p1.y), new Point(lines.get(2).p2.x, lines.get(2).p2.y), new Scalar(255, 0, 0), 4, Imgproc.LINE_AA, 0);
            Imgproc.line(contourOnly, new Point(lines.get(3).p1.x, lines.get(3).p1.y), new Point(lines.get(3).p2.x, lines.get(3).p2.y), new Scalar(255, 0, 0), 4, Imgproc.LINE_AA, 0);
        }


        takePictureButton.setText(Integer.toString(lines.size()));


        //process lines for intersections
        corners.clear();
        for (int i = 0; i < lines.size() - 1; i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                Point intersection = lines.get(i).getIntersection(lines.get(j));
                if (intersection == null) continue;
                if (intersection.x > originalMat.width() || intersection.y > originalMat.height()) continue;
                if (intersection.x < 0 || intersection.y < 0) continue;
                corners.add(intersection);
                //Imgproc.drawMarker(contourOnly, intersection, new Scalar(255, 0, 0), 0, 5, 5);
            }
        }

        // order the corners for "top left", "top right", "bottom left" and "bottom right"
        List<Point> orderedCorners = orderCorners(lines);
        if (orderedCorners != null && orderedCorners.size() == 4) {
            Imgproc.drawMarker(contourOnly, orderedCorners.get(0), new Scalar(255, 0, 0), 0, 8, 5);
            Imgproc.drawMarker(contourOnly, orderedCorners.get(1), new Scalar(0, 255, 0), 0, 8, 5);
            Imgproc.drawMarker(contourOnly, orderedCorners.get(2), new Scalar(0, 0, 255), 0, 8, 5);
            Imgproc.drawMarker(contourOnly, orderedCorners.get(3), new Scalar(0, 0, 0), 0, 8, 5);
        } else if (orderedCorners == null) {
            //last resort, order based on closeness to corners of image
        }

        Utils.matToBitmap(contourOnly, originalBitmap);
        analyzedOutput = originalBitmap;

        pictureImageView.setImageBitmap(originalBitmap);
        pictureImageView.setPivotX(pictureImageView.getWidth() / 2);
        pictureImageView.setPivotY(pictureImageView.getHeight() / 2);
        pictureImageView.setRotation(90);
        //pictureImageView.setVisibility(lines.size() == 4 ? View.VISIBLE : View.GONE);
    }

    private Point currentPoint = null;

    private List<Point> orderCorners(List<Line> lines) {
        if (corners.size() != 4 || lines.size() != 4) return null;
        ArrayList<Point> out = new ArrayList<>();

        int closestCornerIdx = -1;
        double minDist = Integer.MAX_VALUE;

        for (int i = 0; i < corners.size(); i++) {
            double dist = distanceBetween(new Point(0, 0), corners.get(i));
            closestCornerIdx = dist < minDist ? i : closestCornerIdx;
            minDist = Math.min(dist, minDist);
        }

        if (closestCornerIdx < 0) return null;

        //get tl corner
        Point tl = corners.get(closestCornerIdx);
        out.add(tl);
        corners.remove(tl);
        //get lines connected to tl corner
        int closestIdx1 = -1;
        int closestIdx2 = -1;
        double closestDist1 = Integer.MAX_VALUE;
        double closestDist2 = Integer.MAX_VALUE;

        for (int i = 0; i < lines.size(); i++) {
            double d = lines.get(i).distanceTo(tl);
            if (closestIdx1 == -1) {
                closestIdx1 = i;
                closestDist1 = d;
            } else if (closestIdx2 == -1) {
                closestIdx2 = i;
                closestDist2 = d;
            } else {
                if (d < closestDist1 && d < closestDist2) {
                    if (closestDist1 < closestDist2) {
                        closestIdx2 = i;
                        closestDist2 = d;
                    } else {
                        closestIdx1 = i;
                        closestDist1 = d;
                    }
                } else if (d < closestDist1) {
                    closestIdx1 = i;
                    closestDist1 = d;
                } else if (d < closestDist2) {
                    closestIdx2 = i;
                    closestDist2 = d;
                }
            }
        }
        if (closestIdx1 < 0 || closestIdx2 < 0) return null;
        //get rest of corners
        int furthestIdx = -1;
        double furthestDist = 0;
        for (int i = 0; i < corners.size(); i++) {
            double dist = Math.min(lines.get(closestIdx1).distanceTo(corners.get(i)), lines.get(closestIdx2).distanceTo(corners.get(i)));
            furthestIdx = dist > furthestDist ? i : furthestIdx;
            furthestDist = Math.max(furthestDist, dist);
        }
        if (furthestIdx < 0) return null;

        Point br = corners.get(furthestIdx);
        corners.remove(br);

        Line diag = new Line(tl, br);
        Line l1 = new Line(tl, corners.get(0));
        Line l2 = new Line(tl, corners.get(1));

        double a1 = Math.toDegrees(Math.atan((l1.slope() - diag.slope())/(1 + (l1.slope()) * (diag.slope()))));
        double a2 = Math.toDegrees(Math.atan((l2.slope() - diag.slope())/(1 + (l2.slope()) * (diag.slope()))));

        if ((a1 > 0 && a2 > 0) || (a1 < 0 && a2 < 0)) {
            return null;
        } else {
            Point tr = a1 > 0 ? corners.get(0) : corners.get(1);
            Point bl = a2 < 0 ? corners.get(1) : corners.get(0);
            out.add(tr);
            out.add(br);
            out.add(bl);
        }


        return out;
    }

    private double distanceBetween (Point p1, Point p2) {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }

    private class ClosestPoint implements Comparator<Point> {
        public int compare(Point a, Point b) {
            //increasing order
            return (int)(distanceBetween(a, currentPoint) - distanceBetween(b, currentPoint));
        }
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
                takeDevPicture();
            }
        });
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                done();
            }
        });
    }

    private void takeDevPicture() {
        long fileName = System.currentTimeMillis();
        File file = new File(documentFolderPath + fileName + ".jpg");
        //create copy of current analyzed output on screen
        Bitmap saveBM = analyzedOutput.copy(analyzedOutput.getConfig(), true);
        //rotate 90 degrees clockwise
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(saveBM, saveBM.getWidth(), saveBM.getHeight(), true);
        Bitmap rotatedBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, true);

        try (FileOutputStream out = new FileOutputStream(documentFolderPath + fileName + ".jpg")) {
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
