package com.chijo.scanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import org.opencv.core.Point;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CameraOverlay extends View {
    private Paint paint = new Paint();
    private List<Line> finalLines = new ArrayList<>();
    private List<Point> finalCorners = new ArrayList<>();

    private List<Line> testLines = new ArrayList<>();

    private Paint testPaint = new Paint();

    public CameraOverlay(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.CYAN);
        paint.setStrokeWidth(10f);
        testPaint.setStyle(Paint.Style.STROKE);
        testPaint.setColor(Color.GREEN);
        testPaint.setStrokeWidth(10f);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for(Point p: finalCorners) {
            canvas.drawPoint((float) p.x, (float) p.y, paint);
        }
        for(Line l: finalLines) {
            canvas.drawLine((float) l.p1.x, (float) l.p1.y, (float) l.p2.x, (float) l.p2.y, paint);
        }
    }

    public void drawOutline(List<Line> edges, List<Point> corners) {
        finalCorners.clear();
        finalCorners.addAll(corners);
        finalLines.clear();
        finalLines.addAll(edges);
        invalidate();
    }
/*
    public void test(Size size, List<Point> corners) {
        double canvasW = getWidth();
        double canvasH = getHeight();
        //convert points in matrix with the given size onto the canvas
        double scaleW = canvasW / size.height;
        double scaleH = canvasH / size.width;
        for(Point p : corners) {
            double temp = p.x * scaleW;
            p.x = scaleH - (p.y * scaleH);
            p.y = temp;
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
        testLines.clear();
        for(int i = 0; i < lines.size(); i++) {
            if(!removeIdx.contains(i)) {
                testLines.add(lines.get(i));
            }
        }
        invalidate();
    }*/

    public void clear() {
        finalCorners.clear();
        finalLines.clear();
        invalidate();
    }
}
