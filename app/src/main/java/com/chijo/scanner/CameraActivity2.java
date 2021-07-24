package com.chijo.scanner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.ExifInterface;
import android.media.Image;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.AttributeSet;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraActivity2 extends AppCompatActivity {

    //mode
    private int operationMode = 0; //0: new document, 1: adding to existing document
    //perms
    private final int CAMERA_REQUEST_CODE = 1;
    //result
    private Intent returnResult = new Intent();
    private int resultCode = 0;
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

    private ArrayList<String> pageList = new ArrayList<>();
    private String documentFolderPath;
    private String docName;
    private int picturesTaken = 0;
    private int priorPictures = 0;

    //page tracking:
    private CameraOverlay cameraOverlay;
    private final double trackTolerance = 0.2;
    private double trackArea = 0;
    private List<Line> edges = new ArrayList<>();
    private List<Point> corners = new ArrayList<>();

    private boolean stopAuto = false;
    private boolean timerStarted = false;
    CountDownTimer countDownTimer = new CountDownTimer(4000, 2000) {
        @Override
        public void onTick(long l) {}
        @Override
        public void onFinish() {
            takePicture();
            timerStarted = false;
        }
    };

    //TODO: add last picture preview to left of button, allow user to edit that photo (use same activity as the one used when tapping a page in DocumentViewActivity)
    //TODO: add camera options

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera2);

        Bundle extras = getIntent().getExtras();
        operationMode = extras.getInt("mode");

        docName = operationMode == 0 ? "" : extras.getString("docName");
        documentFolderPath = operationMode == 0 ? AppConstants.DOCUMENT_SAVE_LOCATION + System.currentTimeMillis() + "/" : extras.getString("path");
        priorPictures = operationMode == 0 ? 0 : extras.getInt("priorPictures");
        previewView = (PreviewView) findViewById(R.id.camera_preview_view);
        takePictureButton = (Button) findViewById(R.id.button_take_picture);
        doneButton = (Button) findViewById(R.id.button_done_pictures);
        pictureImageView = (ImageView) findViewById(R.id.last_picture_preview);
        cameraOverlay = (CameraOverlay) findViewById(R.id.camera_preview_view_overlay);
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
                    Toast.makeText(CameraActivity2.this, "ruh roh... that shouldn't have happened", Toast.LENGTH_LONG).show();
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
                Bitmap imageBM = toBitmap(image);
                if(imageBM != null) {
                    Utils.bitmapToMat(imageBM, mat);
                    int rotation = image.getImageInfo().getRotationDegrees();
                    quadrilateralDetection(rotation, imageBM, mat);
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

    private Bitmap toBitmap(ImageProxy image) {
        if(image.getFormat() != ImageFormat.YUV_420_888) {
            return null;
        }
        @SuppressLint("UnsafeOptInUsageError") Image i = image.getImage();
        Image.Plane[] planes = i.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, i.getWidth(), i.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void quadrilateralDetection(int rotationDegrees, Bitmap originalImage, Mat mat) {
        Mat output = new Mat(mat.size(), mat.type());
        //preprocessing
        Imgproc.cvtColor(mat, output, Imgproc.COLOR_BGR2GRAY);
        Imgproc.pyrDown(output, output, new Size(output.cols()/2, output.rows()/2));
        Imgproc.pyrUp(output, output, mat.size());
        Imgproc.adaptiveThreshold(output, output, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 115, 4);
        Imgproc.medianBlur(output, output, 11);
        Imgproc.Canny(output, output, 200, 250);
        Imgproc.dilate(output, output, new Mat(), new Point(-1, 1), 1);

        //find contours:
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(output, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        int maxAreaIdx = -1;
        double maxArea = -1;
        MatOfPoint finalContour = null;
        for(int i = 0; i < contours.size(); i++) {
            MatOfPoint contour = contours.get(i);
            double contourArea = Imgproc.contourArea(contour);
            if(contourArea > maxArea) {
                maxArea = contourArea;
                maxAreaIdx = i;
                finalContour = contour;
            }
        }
        if(maxAreaIdx != -1) {
            MatOfPoint2f curve = new MatOfPoint2f(finalContour.toArray());
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);
            Imgproc.drawContours(mat, contours, maxAreaIdx, new Scalar(0, 255, 0), 10);
            for(Point p : approxCurve.toArray()) {
                Imgproc.drawMarker(mat, p, new Scalar(255, 0, 0), 0, 5, 5);
            }
            if(approxCurve.total() == 4) {
                //gotten 4 corners, now get the lines connecting them
                getQuadrilateralEdges(rotationDegrees, mat.size(), approxCurve.toList());
                cameraOverlay.drawOutline(edges, corners);
                //cameraOverlay.test(mat.size(), approxCurve.toList());
                //cameraOverlay.drawPoints(mat.size(), approxCurve.toList());
                if(Math.abs(maxArea - trackArea) <= trackArea * trackTolerance) {
                    //start/continue timer
                    if(!timerStarted && !stopAuto) {
                        countDownTimer.start();
                        timerStarted = true;
                    }
                } else {
                    //stop timer
                    edges.clear();
                    corners.clear();
                    countDownTimer.cancel();
                    timerStarted = false;
                }
                trackArea = maxArea;
            } else {
                edges.clear();
                corners.clear();
                cameraOverlay.clear();
            }
        }
        //Utils.matToBitmap(mat, originalImage);
        //pictureImageView.setImageBitmap(originalImage);
    }

    private void getQuadrilateralEdges(int rotationDegrees, Size size, List<Point> corners) {
        double canvasW = cameraOverlay.getWidth();
        double canvasH = cameraOverlay.getHeight();
        //convert points in matrix with the given size onto the canvas
        double scaleW = rotationDegrees == 0 || rotationDegrees == 180 ? canvasW / size.width : canvasW / size.height;
        double scaleH = rotationDegrees == 0 || rotationDegrees == 180 ? canvasH / size.height : canvasH / size.width;
        for(Point p : corners) {
            p.x *= scaleW;
            p.y *= scaleH;
            for(int i = 0; i < rotationDegrees; i += 90) {
                double temp = p.x;
                p.x = i == 0 || i == 270 ? canvasW - p.y : canvasH - p.y;
                p.y = temp;
            }
        }
        //get all lines defined by points (should be 6)
        List<Line> lines = new ArrayList<>();
        for(int i = 0; i < corners.size(); i++) {
            for(int j = i + 1; j < corners.size(); j++) {
                lines.add(new Line(corners.get(i), corners.get(j)));
            }
        }
        List<Integer> removeIdx = new ArrayList<>();
        //remove any lines that fully intersect
        for(int i = 0; i < lines.size(); i++) {
            for(int j = i + 1; j < lines.size(); j++) {
                if(lines.get(i).intersects(lines.get(j))) {
                    if(!removeIdx.contains(i)) removeIdx.add(i);
                    if(!removeIdx.contains(j)) removeIdx.add(j);
                }
            }
        }
        edges.clear();
        for(int i = 0; i < lines.size(); i++) {
            if(!removeIdx.contains(i)) {
                edges.add(lines.get(i));
            }
        }
        this.corners.clear();
        this.corners.addAll(corners);
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
        updateButtonText();
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                done();
            }
        });
    }

    private void updateButtonText() {
        takePictureButton.setText(Integer.toString(picturesTaken));
    }

    private void updateImageView(File picture) {
        //get file dimensions and load accordingly:
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(picture.getPath(), options);
        int w = options.outWidth;
        int h = options.outHeight;
        int scale = Math.min(w, h)/120;
        BitmapFactory.Options loadOptions = new BitmapFactory.Options();
        loadOptions.inSampleSize = scale;
        pictureImageView.setImageBitmap(BitmapFactory.decodeFile(picture.getPath(), loadOptions));
    }

    private void takePicture() {
        if(stopAuto) return;
        int fileNumber = picturesTaken + priorPictures;
        File file = new File(documentFolderPath + fileNumber + ".jpg");
        if(!file.exists()) {
            file.getParentFile().mkdirs();
        }
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(file).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        // make copy of image if no crop, otherwise modify with crop
                        final File fileModified = new File(documentFolderPath + fileNumber + "_modded.jpg");
                        pageList.add(fileNumber + "_modded.jpg");
                        if(corners.size() == 0) {
                            try {
                                Files.copy(file.toPath(), fileModified.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            //order the corners
                            List<Point> cornersCopy = new ArrayList<>(corners);
                            int minSumIdx = -1;
                            int maxSumIdx = -1;
                            double minSum = Double.MAX_VALUE;
                            double maxSum = -1;
                            for(int i = 0; i < cornersCopy.size(); i++) {
                                Point p = cornersCopy.get(i);
                                double sum = p.x + p.y;
                                if(sum < minSum) {
                                    minSum = sum;
                                    minSumIdx = i;
                                }
                                if(sum > maxSum) {
                                    maxSum = sum;
                                    maxSumIdx = i;
                                }
                            }
                            Point tl = cornersCopy.get(minSumIdx);
                            Point br = cornersCopy.get(maxSumIdx);

                            int minDiffIdx = -1;
                            int maxDiffIdx = -1;
                            double minDiff = Double.MAX_VALUE;
                            double maxDiff = -1;
                            for(int i = 0; i < cornersCopy.size(); i++) {
                                if(i == minSumIdx || i == maxSumIdx) continue;
                                Point p = cornersCopy.get(i);
                                double diff = p.x - p.y;
                                if(diff < minDiff) {
                                    minDiff = diff;
                                    minDiffIdx = i;
                                }
                                if(diff > maxDiff) {
                                    maxDiff = diff;
                                    maxDiffIdx = i;
                                }
                            }
                            Point tr = cornersCopy.get(maxDiffIdx);
                            Point bl = cornersCopy.get(minDiffIdx);
                            System.out.println(tl.x + " " + tl.y);
                            Mat cropped = fourPointTransform(tl, tr, br, bl, file);
                            Imgcodecs.imwrite(fileModified.getPath(), cropped);
                        }
                        picturesTaken++;
                        updateButtonText();
                        updateImageView(fileModified);
                        saveInfoFile();
                    }
                    @Override
                    public void onError(ImageCaptureException error) {
                        // insert your code here.
                        Toast.makeText(CameraActivity2.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private Mat fourPointTransform(Point tl, Point tr, Point br, Point bl, File file) {
        int orientation = -1;
        Matrix m = new Matrix();
        try {
            ExifInterface exif = new ExifInterface(file.getPath());
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            if(orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
            else if(orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
            else if(orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Bitmap fullImage = BitmapFactory.decodeFile(file.getPath());
        if(orientation != -1) {
            fullImage = Bitmap.createBitmap(fullImage, 0, 0, fullImage.getWidth(), fullImage.getHeight(), m, true);
        }
        Mat imageMat = new Mat();
        Utils.bitmapToMat(fullImage, imageMat);

        //scale points:
        double scaleW = ((double) imageMat.width()) / ((double) cameraOverlay.getWidth());
        double scaleH = ((double) imageMat.height()) / ((double) cameraOverlay.getHeight());
        System.out.println("debug1 " + scaleW + " " + scaleH);
        tl.x *= scaleW;
        tl.y *= scaleH;
        tr.x *= scaleW;
        tr.y *= scaleH;
        bl.x *= scaleW;
        bl.y *= scaleH;
        br.x *= scaleW;
        br.y *= scaleH;
        // get width of new image (adaptive)
        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));
        int maxWidth = Math.max((int)widthA, (int)widthB);
        //get height of new image (adaptive)
        double heightA = Math.sqrt(Math.pow(tr.x - br.x, 2) + Math.pow(tr.y - br.y, 2));
        double heightB = Math.sqrt(Math.pow(tl.x - bl.x, 2) + Math.pow(tl.y - bl.y, 2));
        //TODO: add option for preset cropped ratios
        int maxHeight = Math.max((int)heightA, (int)heightB);
        Mat sourceCorners = new Mat(4, 1, CvType.CV_32FC2);
        sourceCorners.put(0, 0, (int)tl.x, (int)tl.y, (int)tr.x, (int)tr.y, (int)br.x, (int)br.y, (int)bl.x, (int)bl.y);
        Mat destCorners = new Mat(4, 1, CvType.CV_32FC2);
        destCorners.put(0, 0, 0, 0, maxWidth - 1, 0, maxWidth - 1, maxHeight - 1, 0, maxHeight - 1);
        Mat perspectiveMat = Imgproc.getPerspectiveTransform(sourceCorners, destCorners);
        Mat warped = new Mat();
        Imgproc.warpPerspective(imageMat, warped, perspectiveMat, new Size(maxWidth, maxHeight));
        //remove blue from picture....
        Imgproc.cvtColor(warped, warped, Imgproc.COLOR_BGR2RGB);
        return warped;
    }

    private void saveInfoFile() {
        if(picturesTaken == 0) {
            return;
        }
        String docDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        int totalPictures = picturesTaken + priorPictures;
        String docPages = totalPictures + (totalPictures == 1 ? " page" : " pages");
        File infoFile = new File(documentFolderPath + "infoFile.txt");
        if(!infoFile.exists()) {
            infoFile.getParentFile().mkdirs();
            docName = Long.toString(System.currentTimeMillis());
        }

        try(OutputStream output = new FileOutputStream(infoFile)){
            output.write((docName + "\n").getBytes());
            output.write((docDateTime + "\n").getBytes());
            output.write((docPages + "\n").getBytes());
            output.write("false".getBytes());
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
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
        stopAuto = true;
        if(timerStarted) {
            countDownTimer.cancel();
            timerStarted = false;
        }
        if(picturesTaken != 0) {
            resultCode = Activity.RESULT_OK;
            returnResult.putExtra("path", documentFolderPath);
            returnResult.putExtra("name", docName);
            returnResult.putStringArrayListExtra("fileList", pageList);
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        setResult(resultCode, returnResult);
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
        stopAuto = true;
        if(picturesTaken == 0) {
            done();
        } else {
            //ask if user wants to save images or not
            if(timerStarted) {
                countDownTimer.cancel();
                timerStarted = false;
            }
        }
    }
}
