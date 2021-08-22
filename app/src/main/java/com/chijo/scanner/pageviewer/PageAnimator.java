package com.chijo.scanner.pageviewer;

import android.view.View;

import androidx.viewpager2.widget.ViewPager2;

import com.chijo.scanner.AppConstants;

// from https://developer.android.com/training/animation/screen-slide-2#pagetransformer

public class PageAnimator implements ViewPager2.PageTransformer {

    int type = 0; // zoom-out

    public PageAnimator(int type) {
        this.type = type;
    }

    private static final float MIN_SCALE = 0.85f;
    private static final float MIN_ALPHA = 0.5f;

    public void transformPage(View view, float position) {
        int pageWidth = view.getWidth();
        int pageHeight = view.getHeight();

        if (position < -1) { // [-Infinity,-1)
            // This page is way off-screen to the left.
            view.setAlpha(0f);

        } else if (position <= 1) { // [-1,1]
            if (type == AppConstants.TRANSFORMER_TYPE_DEPTH) {
                if (position <= 0) { // [-1,0]
                    // Use the default slide transition when moving to the left page
                    view.setAlpha(1f);
                    view.setTranslationX(0f);
                    view.setTranslationZ(0f);
                    view.setScaleX(1f);
                    view.setScaleY(1f);

                } else if (position <= 1) { // (0,1]
                    // Fade the page out.
                    view.setAlpha(1 - position);

                    // Counteract the default slide transition
                    view.setTranslationX(pageWidth * -position);
                    // Move it behind the left page
                    view.setTranslationZ(-1f);

                    // Scale the page down (between MIN_SCALE and 1)
                    float scaleFactor = MIN_SCALE
                            + (1 - MIN_SCALE) * (1 - Math.abs(position));
                    view.setScaleX(scaleFactor);
                    view.setScaleY(scaleFactor);
                }
            } else {
                // Modify the default slide transition to shrink the page as well
                float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
                float vertMargin = pageHeight * (1 - scaleFactor) / 2;
                float horzMargin = pageWidth * (1 - scaleFactor) / 2;
                if (position < 0) {
                    view.setTranslationX(horzMargin - vertMargin / 2);
                } else {
                    view.setTranslationX(-horzMargin + vertMargin / 2);
                }

                // Scale the page down (between MIN_SCALE and 1)
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);

                // Fade the page relative to its size.
                view.setAlpha(MIN_ALPHA +
                        (scaleFactor - MIN_SCALE) /
                                (1 - MIN_SCALE) * (1 - MIN_ALPHA));
            }
        } else { // (1,+Infinity]
            // This page is way off-screen to the right.
            view.setAlpha(0f);
        }
    }

}
