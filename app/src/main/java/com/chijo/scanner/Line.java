package com.chijo.scanner;

import org.opencv.core.Point;

import java.util.Comparator;

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

    public boolean isEquivalentTo(Line l) {
        boolean lengthEq = false;
        boolean locEq = false;
        boolean slopeEq = false;
        boolean sharedPoint = false;
        boolean threePointEq = false;

        if (Math.abs(len() - l.len()) < 0.05 * Math.max(len(), l.len())) lengthEq = true;
        if (dist(p1, l.p1) < 5 && dist(p2, l.p2) < 5) locEq = true;
        if (dist(p1, l.p2) < 5 && dist(p2, l.p1) < 5) locEq = true;
        if (slope() == l.slope()) slopeEq = true;
        if (Math.abs(slope() - l.slope()) < 0.05 * Math.max(slope(), l.slope())) slopeEq = true;
        if (dist(p1, l.p1) < 10 || dist(p1, l.p2) < 10 || dist(p2, l.p1) < 10 || dist(p2, l.p2) < 10) sharedPoint = true;
        if (dist(p1, l.p1) < 10 && (Math.abs(p2.x - l.p2.x) < 10 || Math.abs(p2.y - l.p2.y) < 10)) threePointEq = true;
        if (dist(p1, l.p2) < 10 && (Math.abs(p2.x - l.p1.x) < 10 || Math.abs(p2.y - l.p1.y) < 10)) threePointEq = true;
        if (dist(p2, l.p1) < 10 && (Math.abs(p1.x - l.p2.x) < 10 || Math.abs(p1.y - l.p2.y) < 10)) threePointEq = true;
        if (dist(p2, l.p2) < 10 && (Math.abs(p1.x - l.p1.x) < 10 || Math.abs(p1.y - l.p1.y) < 10)) threePointEq = true;


        return (lengthEq && locEq) || (slopeEq && sharedPoint) || threePointEq;
    }

    public boolean equals(Line l) {
        // get distances between ends and midpoints, add and see if they are less than the length.
            // this filters out shorter lines that are "within" the larger ones
        // pair off endpoints:
        double d1 = dist(p1, l.p1) + dist(p2, l.p2);
        double d2 = dist(p1, l.p2) + dist(p2, l.p1);

        double total = 0;

        if (d1 > d2) {
            //pair p1 with l.p2 and p2 with l.p1
            total = d2;
        } else {
            //pair p1 with l.p1 and p2 with l.p2
            total = d1;
        }
        total += dist(getMidPoint(), l.getMidPoint());

        if (total < Math.max(len(), l.len())) {
            return true;
        }

        return false;
    }

    public double len() {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }

    public static double dist(Point p1, Point p2) {
        return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }

    public Point getMidPoint() {
        return new Point((p1.x + p2.x)/2.0, (p1.y + p2.y)/2.0);
    }

    public double slope() {
        if (p2.x == p1.x) return -1; // vertical line
        if (p2.x > p1.x) {
            return (p2.y - p1.y)/(p2.x - p1.x);
        } else {
            return (p1.y - p2.y)/(p1.x - p2.x);
        }
    }

    public Point getIntersection(Line l) {
        double intersectX = -1;
        double intersectY = -1;

        if (p2.x == p1.x) { //this line is vertical
            if (l.p2.x == l.p1.x) return null; //other line is also vertical
            else {
                intersectX = p1.x;
                double m2 = l.slope();
                double b2 = l.p2.y - (m2 * l.p2.x);
                intersectY = (m2 * intersectX) + b2;
                return new Point (intersectX, intersectY);
            }
        }
        if (l.p2.x == l.p1.x) { //only other line is vertical
            intersectX = l.p1.x;
            double m1 = slope();
            double b1 = p2.y - (m1 * p2.x);
            intersectY = (m1 * intersectX) + b1;
            return new Point (intersectX, intersectY);
        }

        double m1 = slope();
        double b1 = p2.y - (m1 * p2.x);
        double m2 = l.slope();
        double b2 = l.p2.y - (m2 * l.p2.x);

        if (m1 == m2) return null; //parallel lines

        intersectX = (b1 - b2)/(m2 - m1);
        intersectY = (m1 * intersectX) + b1;
        return new Point(intersectX, intersectY);
    }

    public double distanceTo(Point p) {
        return Math.min(dist(p, p1), dist(p, p2));
    }

    public static class LengthComparator implements Comparator<Line> {
        public int compare(Line a, Line b) {
            //want to sort in descending order
            return (int)(b.len() - a.len());
        }
    }

}
