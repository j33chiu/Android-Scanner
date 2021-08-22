package com.chijo.scanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.chijo.scanner.itemTouchHelper.ItemTouchHelperAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DocumentViewAdapter extends RecyclerView.Adapter<DocumentViewAdapter.ViewHolder> implements ItemTouchHelperAdapter {
    private List<Bitmap> mData; //currently shown list of pages
    private List<Bitmap> mDataFull; //full list of pages
    private List<String> mPageNames; //aligns with mData
    private List<String> mPageNamesFull; //aligns with mDataFull
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;
    private boolean changesMade = false;
    private String documentPath;

    // data is passed into the constructor
    DocumentViewAdapter(Context context, List<Bitmap> data, List<String> pageNames, String documentPath) {
        this.mInflater = LayoutInflater.from(context);
        this.mData = data;
        this.mDataFull = new ArrayList<>(data);
        this.mPageNames = pageNames;
        this.mPageNamesFull = new ArrayList<>(pageNames);
        this.documentPath = documentPath;
    }

    // inflates the row layout from xml when needed
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.page_list_item, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the item
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Bitmap pageImage = mData.get(position);
        holder.page.setImageBitmap(pageImage);
        //ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.page.getLayoutParams();
        //System.out.println(params.width + " " + params.height);
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size();
    }

    public List<String> getOrderedPageNames() {
        return mPageNames;
    }

    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        ImageView page;
        boolean shouldNormalClick = true;

        ViewHolder(View itemView) {
            super(itemView);
            page = itemView.findViewById(R.id.document_page_image);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if(!shouldNormalClick) {
                shouldNormalClick = true;
                return;
            }
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }

        @Override
        public boolean onLongClick(View view) {
            if (mClickListener != null) {
                mClickListener.onItemLongClick(view, getAdapterPosition());
                shouldNormalClick = false;
            }
            return false;
        }
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(mData, i, i + 1);
                Collections.swap(mPageNames, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(mData, i, i - 1);
                Collections.swap(mPageNames, i, i - 1);
            }
        }
        changesMade = true;
        notifyItemMoved(fromPosition, toPosition);
        handleFileChanges();
    }

    public void onItemSwiped(int position) {
        FileHelper.deleteFile(documentPath, mPageNames.get(position));
        FileHelper.deleteFile(documentPath, mPageNames.get(position).charAt(0) + ".jpg");
        mData.remove(position);
        mPageNames.remove(position);
        changesMade = true;
        notifyItemRemoved(position);
        handleFileChanges();
    }

    private void handleFileChanges() {
        int pages = getItemCount();
        for(int i = 0; i < pages; i++) {
            //check if i == file/page name number
            String pageName = mPageNames.get(i);
            int pageI = Integer.parseInt(Character.toString(pageName.charAt(0)));
            if (i != pageI) {
                FileHelper.renameFile(documentPath, pageName, i + "_modded_tmp.jpg");
                FileHelper.renameFile(documentPath, pageI + ".jpg", i + "_tmp.jpg");
            }
        }
        //rename temp filenames now
        for(int i = 0; i < pages; i++) {
            FileHelper.renameFile(documentPath, i + "_modded_tmp.jpg", i + "_modded.jpg");
            FileHelper.renameFile(documentPath, i + "_tmp.jpg", i + ".jpg");
            mPageNames.set(i, i + "_modded.jpg");
        }
    }

    Bitmap getItem(int id) {
        return mData.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    // parent activity will implement this method to respond to click events
    public interface ItemClickListener {
        void onItemClick(View view, int position);

        void onItemLongClick(View view, int adapterPosition);
    }

    public void removePage(Bitmap doc) {
        mDataFull.remove(doc);
        notifyDataSetChanged();
    }

    public void addPage(Bitmap doc, String name) {
        //mData.add(doc); //not needed because handled in documentViewActivity
        mDataFull.add(doc);
        //mPageNames.add(name); //not needed because handled in documentViewActivity
        mPageNamesFull.add(name);
        notifyItemInserted(mData.size() - 1);
    }

    public boolean getChangesMade() {
        return changesMade;
    }

}
