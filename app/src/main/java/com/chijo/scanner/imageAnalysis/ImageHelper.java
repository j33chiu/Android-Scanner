package com.chijo.scanner.imageAnalysis;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.camera.core.ImageProxy;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageHelper {
    public static Mat processImage(Mat originalImage) {
        Mat mat = new Mat(originalImage.size(), originalImage.type());

        Imgproc.cvtColor(originalImage, mat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.pyrDown(mat, mat, new Size(mat.cols()/2, mat.rows()/2));
        Imgproc.pyrUp(mat, mat,  new Size(mat.cols() * 2, mat.rows() * 2));
        Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 75, 4);
        Imgproc.medianBlur(mat, mat, 5);
        Imgproc.Canny(mat, mat, 200, 250);
        Imgproc.dilate(mat, mat, new Mat(), new Point(-1, 1), 1);

        return mat;
    }

    public static void getRegionsByColour() {

    }

    //original image is BGR
    public static Mat kmeansColourClustering(Mat originalImage, int k) {
        Imgproc.cvtColor(originalImage, originalImage, Imgproc.COLOR_BGRA2BGR);

        //reshape image for kmeans
        int n = originalImage.rows() * originalImage.cols();
        Mat data = originalImage.reshape(1, n);
        //convert data to 32f
        data.convertTo(data, CvType.CV_32F);
        //prepare results
        Mat labels = new Mat();
        Mat centers = new Mat();
        TermCriteria criteria = new TermCriteria(TermCriteria.COUNT, 10, 1.0);

        Core.kmeans(data, k, labels, criteria, 5, Core.KMEANS_PP_CENTERS, centers);

        //convert centers to uint8
        centers.convertTo(centers, CvType.CV_8UC1, 255.0);
        centers.reshape(3);

        //create output Mat
        Mat out = originalImage.clone();
        int row = 0;
        for (int i = 0; i < out.rows(); i++) {
            for (int j = 0; j < out.cols(); j++) {
                int l = (int)labels.get(row, 0)[0];
                int r = (int)centers.get(l, 2)[0];
                int g = (int)centers.get(l, 1)[0];
                int b = (int)centers.get(l, 0)[0];
                out.put(i, j, b, g, r);
                row++;
            }
        }

        return out;
    }

    public static Bitmap toBitmap(ImageProxy image) {
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

}
