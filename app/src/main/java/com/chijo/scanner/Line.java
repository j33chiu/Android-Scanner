package com.chijo.scanner;

import org.opencv.core.Point;

public class Line {
    public Point p1;
    public Point p2;

    public Line(Point p1, Point p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public boolean intersects(Line otherLine) {
        if(p1.equals(otherLine.p1)) return false;
        if(p1.equals(otherLine.p2)) return false;
        if(p2.equals(otherLine.p1)) return false;
        if(p2.equals(otherLine.p2)) return false;
        //case of 0 "run"
        if(p2.x - p1.x == 0) { //this line vertical
            if(otherLine.p2.x - otherLine.p1.x == 0) return false; //both vertical
            if(p1.x > Math.min(otherLine.p1.x, otherLine.p2.x) && p1.x < Math.max(otherLine.p1.x, otherLine.p2.x)) return true; //otherline has this line in range
            return false;
        } else { //this line not vertical
            if(otherLine.p2.x - otherLine.p1.x == 0) { //otherline vertical
                if(otherLine.p1.x > Math.min(p1.x, p2.x) && otherLine.p1.x < Math.max(p1.x, p2.x)) return true; //this line has otherline in range
                return false;
            } else {
                double m1 = (p2.y - p1.y)/(p2.x - p1.x);
                double m2 = (otherLine.p2.y - otherLine.p1.y)/(otherLine.p2.x - otherLine.p1.x);
                if(m1 == m2) return false; //parallel
                double b1 = p1.y - (m1 * p1.x);
                double b2 = otherLine.p1.y - (m2 * otherLine.p1.x);
                double intersectX = (b2 - b1)/(m1 - m2);
                if(intersectX > Math.min(p1.x, p2.x) && intersectX < Math.max(p1.x, p2.x)) return true;
                if(intersectX > Math.min(otherLine.p1.x, otherLine.p2.x) && intersectX < Math.max(otherLine.p1.x, otherLine.p2.x)) return true;
                return false;
            }
        }
    }
}
