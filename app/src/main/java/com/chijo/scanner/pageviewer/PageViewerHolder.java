package com.chijo.scanner.pageviewer;

import android.view.View;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.chijo.scanner.R;

public class PageViewerHolder extends RecyclerView.ViewHolder {

    public ImageView displayImage;

    public PageViewerHolder(View itemView) {
        super(itemView);
        displayImage = itemView.findViewById(R.id.page_viewer_imageview);
    }

}
