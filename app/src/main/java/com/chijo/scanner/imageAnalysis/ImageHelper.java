package com.chijo.scanner.imageAnalysis;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import androidx.camera.core.ImageProxy;

import com.chijo.scanner.Line;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
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

    public static Mat getImageOutline(Mat original) {
        //TODO: white paper on white background - edges NOT detected
        Mat mat = new Mat(original.size(), original.type());
        // run canny on each colour channel
        ArrayList<Mat> channels = new ArrayList<>();
        Core.split(original, channels);

        Mat out = new Mat(original.rows(), original.cols(), original.type(), new Scalar(0, 0, 0));
        Imgproc.cvtColor(out, out, Imgproc.COLOR_RGB2GRAY);

        for (Mat m : channels) {
            //Scalar alpha = new Scalar(1.5);
            //Core.multiply(m, alpha, m);

            Imgproc.medianBlur(m, m, 7);
            Imgproc.Canny(m, m, 150, 200);
            Imgproc.dilate(m, m, new Mat(), new Point(-1, 1), 1);
            Core.add(out, m, out);
        }

        /* works well except for when page is same colour as bg
        Imgproc.cvtColor(original, mat, Imgproc.COLOR_BGR2GRAY);
        Imgproc.medianBlur(mat, mat, 7);
        Imgproc.Canny(mat, mat, 150, 200);
        Imgproc.dilate(mat, mat, new Mat(), new Point(-1, 1), 1);
        */

        //Imgproc.pyrDown(mat, mat, new Size(mat.cols()/2, mat.rows()/2));
        //Imgproc.pyrUp(mat, mat,  new Size(mat.cols() * 2, mat.rows() * 2));
        //Imgproc.adaptiveThreshold(mat, mat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 175, 4);
        //Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGB);
        return out;
    }

    private static final List<MatOfPoint> contours = new ArrayList<>();

    public static Mat findContours(Mat mat, Mat original) {
        //find contours:
        contours.clear();
        Imgproc.findContours(mat, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
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
        //filter:
            //filter by size (remove all the small contours)
            //filter by shape (area of rectangle? tolerance?)
        Mat contourOnly = new Mat(original.rows(), original.cols(), original.type(), new Scalar(0, 0, 0));

        if(maxAreaIdx != -1) {
            MatOfPoint2f curve = new MatOfPoint2f(finalContour.toArray());
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);
            Imgproc.drawContours(original, contours, maxAreaIdx, new Scalar(0, 255, 0), 5);
            Imgproc.drawContours(contourOnly, contours, maxAreaIdx, new Scalar(255, 255, 255), 3);
            if(approxCurve.total() >= 4 && approxCurve.total() <= 6) {
                //gotten 4 corners, now get the lines connecting them
                Point [] points = approxCurve.toArray();
                for (int i = 0; i < points.length; i++) {
                    Imgproc.line(original, points[i], points[(i + 1) == points.length ? 0 : i + 1], new Scalar(0, 0, 255));
                    Imgproc.drawMarker(original, points[i], new Scalar(255, 0, 0), 0, 5, 5);
                }
            } else {
                //edges.clear();
                //corners.clear();
                //cameraOverlay.clear();
            }
        }

        return contourOnly;
    }

    public static List<Line> approximateEdges(Mat original) {
        List<Line> lines = new ArrayList<>();
        Mat singleChannel = new Mat(original.rows(), original.cols(), original.type(), new Scalar(0, 0, 0));
        Imgproc.cvtColor(original, singleChannel, Imgproc.COLOR_RGB2GRAY);
        Mat houghLines = new Mat();
        Imgproc.HoughLinesP(singleChannel, houghLines, 1, Math.PI/180, 200, 75, 15);
        for (int i = 0; i < houghLines.rows(); i++) {
            double[] l = houghLines.get(i, 0);
            //Imgproc.line(original, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(0, 255, 0), 2, Imgproc.LINE_AA, 0);
            lines.add(new Line(new Point(l[0], l[1]), new Point(l[2], l[3])));
        }

        //process lines (remove those that are "equivalent")
        for (int i = 0; i < lines.size() - 1; i++) {
            for (int j = i + 1; j < lines.size(); j++) {
                if (lines.get(i).equals(lines.get(j))) {
                    if (lines.get(i).len() > lines.get(j).len()) {
                        lines.remove(j);
                        j--;
                    } else {
                        lines.remove(i);
                        i--;
                        break;
                    }
                }
            }
        }
        // use common corners to remove the odd ones out
        /*
        for (int i = 0; i < lines.size() - 1; i++) {
            Line l1 = lines.get(i);
            int connectedLines = 0;
            boolean p1Shared = false;
            boolean p2Shared = false;
            for (int j = i + 1; j < lines.size(); j++) {
                Line l2 = lines.get(j);
                int shared = 0;
                if (Line.dist(l1.p1, l2.p1) < 10) {
                    shared ++;
                    p1Shared = true;
                }
                if (Line.dist(l1.p1, l2.p2) < 10) {
                    shared ++;
                    p1Shared = true;
                }
                if (Line.dist(l1.p2, l2.p1) < 10) {
                    shared ++;
                    p2Shared = true;
                }
                if (Line.dist(l1.p2, l2.p2) < 10) {
                    shared ++;
                    p2Shared = true;
                }
                if (shared >= 2) {
                    lines.remove(j);
                    j--;
                } else if (shared == 1) {
                    connectedLines++;
                }
            }
            if (connectedLines < 2) {
                lines.remove(i);
                i--;
            }
        }
        */

        return lines;
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
