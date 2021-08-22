package com.chijo.scanner.pageviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.chijo.scanner.R;

import java.util.ArrayList;

public class PageViewerAdapter extends RecyclerView.Adapter<PageViewerHolder>{

    private Context mContext;
    private Bitmap[] documentImageList;
    private int page;

    public PageViewerAdapter(Context context, Bitmap[] documentImageList, int onOpenPosition) {
        mContext = context;
        this.documentImageList = documentImageList;
        page = onOpenPosition;
    }

    @Override
    public PageViewerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PageViewerHolder holder = new PageViewerHolder(LayoutInflater.from(mContext).inflate(R.layout.page_viewer_cell_item, parent, false));
        bindViewHolder(holder, page);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewerHolder holder, int position) {
        holder.displayImage.setImageBitmap(documentImageList[position]);
    }



    @Override
    public int getItemCount() {
        return documentImageList.length;
    }

}
