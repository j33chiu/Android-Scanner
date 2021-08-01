package com.chijo.scanner;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;
import android.view.ViewGroup;

public class ViewAnimations {

    private static final int defaultYDist = 255;

    public static boolean fabMove(final View view, boolean moveDown) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)view.getLayoutParams();
        view.animate().setDuration(150)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                    }
                })
                .translationY(moveDown ? defaultYDist - lp.rightMargin : 0);
        return moveDown;
    }
}
