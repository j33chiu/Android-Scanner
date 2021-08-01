package com.chijo.scanner;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;

import com.chijo.scanner.itemTouchHelper.ItemTouchHelperAdapter;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.backends.pipeline.PipelineDraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.imagepipeline.request.Postprocessor;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

import jp.wasabeef.fresco.processors.BlurPostprocessor;

public class DocumentListAdapter extends RecyclerView.Adapter<DocumentListAdapter.ViewHolder> implements ItemTouchHelperAdapter {
    private ArrayList<Document> mData; //currently shown list of documents
    private ArrayList<Document> mDataFull; //full list of documents (both current and archived)
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    private final int SORT_MODE_ALPHA_ASC = 0;
    private final int SORT_MODE_ALPHA_DESC = 1;
    private final int SORT_MODE_DATE_TIME_ASC = 2;
    private final int SORT_MODE_DATE_TIME_DESC = 3;
    private final int SORT_MODE_PAGES_ASC = 4;
    private final int SORT_MODE_PAGES_DESC = 5;
    private int sortMode = SORT_MODE_ALPHA_ASC;

    private Context mContext;
    private Postprocessor blurPostProcessor;
    private boolean archiveMode = false;
    private boolean selectMode = false;
    private int selectedDocs = 0;

    // data is passed into the constructor
    DocumentListAdapter(Context context, ArrayList<Document> data) {
        mInflater = LayoutInflater.from(context);
        mDataFull = new ArrayList<>(data);
        mData = new ArrayList<>(mDataFull);
        this.blurPostProcessor = new BlurPostprocessor(context, 10);
        mContext = context;
        sort(false);
    }

    // inflates the row layout from xml when needed
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.document_list_item, parent, false);
        return new ViewHolder(view);
    }

    // binds the data to the item
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Document doc = mData.get(position);
        holder.checkmark.setVisibility(doc.getSelected() ? View.VISIBLE : View.INVISIBLE);
        holder.name.setText(doc.getDocumentName());
        holder.date.setText(doc.getLastUpdatedDate());
        holder.pages.setText(doc.getPages());
        File f = new File(doc.getPath());
        for(File page : f.listFiles()) {
            if(page.getName().equals("0_modded.jpg")) {
                Uri uri = Uri.parse("file://" + page.getPath());
                ImageRequest request = selectMode ?
                        ImageRequestBuilder.newBuilderWithSource(uri)
                                .setResizeOptions(new ResizeOptions(120, 120))
                                //.setPostprocessor(blurPostProcessor)
                                .build() :
                        ImageRequestBuilder.newBuilderWithSource(uri)
                                .setResizeOptions(new ResizeOptions(120, 120))
                                .build();
                holder.frescoPreview.setImageRequest(request);
                break;
            }
        }
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return mData.size();
    }


    // stores and recycles views as they are scrolled off screen
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        ImageView checkmark;
        TextView name;
        TextView date;
        TextView pages;
        ImageView preview;
        SimpleDraweeView frescoPreview;
        boolean shouldNormalClick = true;

        ViewHolder(View itemView) {
            super(itemView);
            checkmark = itemView.findViewById(R.id.image_overlay);
            name = itemView.findViewById(R.id.document_list_name);
            date = itemView.findViewById(R.id.document_list_date_time);
            pages = itemView.findViewById(R.id.document_list_pages);
            preview = itemView.findViewById(R.id.document_list_preview);
            frescoPreview = itemView.findViewById(R.id.document_list_preview_fresco);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if(!shouldNormalClick) {
                shouldNormalClick = true;
                return;
            }
            if (mClickListener != null && !selectMode) mClickListener.onItemClick(view, getAdapterPosition(), false);
            else {
                checkmark.setVisibility(checkmark.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                selectedDocs += checkmark.getVisibility() == View.VISIBLE ? 1 : -1;
                mData.get(getAdapterPosition()).setSelected(checkmark.getVisibility() == View.VISIBLE);
                mClickListener.onItemClick(view, getAdapterPosition(), true);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (mClickListener != null) {
                checkmark.setVisibility(checkmark.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                selectedDocs += checkmark.getVisibility() == View.VISIBLE ? 1 : -1;
                mData.get(getAdapterPosition()).setSelected(checkmark.getVisibility() == View.VISIBLE);
                mClickListener.onItemLongClick(view, getAdapterPosition());
                shouldNormalClick = false;
            }
            return false;
        }
    }

    public void onItemMove(int fromPosition, int toPosition) {
        //should not be able to move items
    }

    public void onItemSwiped(int position) {
        Document doc = mData.get(position);
        mData.remove(position);
        notifyItemRemoved(position);
        if(doc.isArchived()) {
            //fully delete from device
            mDataFull.remove(doc);
            permDelete(doc);
        } else {
            archive(doc);
            //undo option:
            showUndoSnackbar(doc, position);
        }
    }

    public void mimicSwipe(Document doc) {
        int position = mData.indexOf(doc);
        mData.remove(position);
        notifyItemRemoved(position);
        if(doc.isArchived()) {
            //fully delete from device
            mDataFull.remove(doc);
            permDelete(doc);
        } else {
            archive(doc);
        }
    }

    public void restore(Document doc) {
        if (!doc.isArchived() || !archiveMode) {
            return;
        }
        int position = mData.indexOf(doc);
        mData.remove(position);
        notifyItemRemoved(position);
        unArchive(doc);
    }

    private void permDelete(Document doc) {
        FileHelper.deleteDocument(doc);
    }

    private void archive(Document doc) {
        doc.setIsArchived("true");
        FileHelper.writeIsArchived(doc, "true");
    }

    private void unArchive(Document doc) {
        doc.setIsArchived("false");
        FileHelper.writeIsArchived(doc, "false");
    }

    private void showUndoSnackbar(Document deletedDoc, int deletedDocPosition) {
        Snackbar snackbar = Snackbar.make(((Activity) mContext).findViewById(R.id.main_activity_layout), R.string.document_archived_snackbar, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.snackbar_undo, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mData.add(deletedDocPosition, deletedDoc);
                notifyItemInserted(deletedDocPosition);
                unArchive(deletedDoc);
            }
        });
        snackbar.show();
    }

    Document getItem(int id) {
        return mData.get(id);
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    // parent activity will implement this method to respond to click events
    public interface ItemClickListener {
        void onItemClick(View view, int position, boolean isSelecting);

        void onItemLongClick(View view, int position);
    }

    public void removeDocument(Document doc) {
        String path = doc.getPath();
        for(int i = 0; i < mDataFull.size(); i++) {
            if(mDataFull.get(i).getPath().equals(path)) {
                mDataFull.remove(i);
                break;
            }
        }
        for(int i = 0; i < mData.size(); i++) {
            if(mData.get(i).getPath().equals(path)) {
                mData.remove(i);
                break;
            }
        }
        sort(false);
        notifyDataSetChanged();
    }

    public void addDocument(Document doc) {
        //TODO: with many documents, constantly sorting after an insertion may cause lag
        mDataFull.add(doc);
        mData.add(doc);
        sort(archiveMode);
        notifyDataSetChanged();
    }

    public void replaceDocument(Document doc) {
        for(Document d : mDataFull) {
            if(d.getPath().equals(doc.getPath())) {
                mDataFull.set(mDataFull.indexOf(d), doc);
                break;
            }
        }
        for(Document d : mData) {
            if(d.getPath().equals(doc.getPath())) {
                mData.set(mData.indexOf(d), doc);
                break;
            }
        }
        sort(false);
        notifyDataSetChanged();
    }

    public void filter(String text) {
        mData.clear();
        if(text.isEmpty()) {
            mData.addAll(mDataFull);
        } else {
            text = text.toLowerCase();
            for(Document doc: mDataFull){
                if(doc.search(text)){
                    mData.add(doc);
                }
            }
        }
        sort(false);
        notifyDataSetChanged();
    }

    public void resetSelected() {
        setSelect(false);
        selectedDocs = 0;
        for (Document d : mDataFull) {
            d.setSelected(false);
        }
    }

    public void setSelect(boolean selectMode) {
        this.selectMode = selectMode;
    }

    public int getSelected() {
        return selectedDocs;
    }

    public ArrayList<Document> getSelectedDocs() {
        ArrayList<Document> selectedList = new ArrayList<>();
        for(Document d : mData) {
            if (d.getSelected()) {
                selectedList.add(d);
            }
        }
        return selectedList;
    }

    public void toggleShowArchived(boolean shouldShow) {
        archiveMode = shouldShow;
        mData = new ArrayList<>(mDataFull);
        sort(shouldShow);
        notifyDataSetChanged();
    }

    private void sort(boolean includeArchived) {
        switch(sortMode) {
            case 0:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc1.getDocumentName().compareTo(doc2.getDocumentName());
                    }
                });
                break;
            case 1:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc2.getDocumentName().compareTo(doc1.getDocumentName());
                    }
                });
                break;
            case 2:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc1.getLastUpdatedDate().compareTo(doc2.getLastUpdatedDate());
                    }
                });
            case 3:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc2.getLastUpdatedDate().compareTo(doc1.getLastUpdatedDate());
                    }
                });
                break;
            case 4:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc1.getPages().compareTo(doc2.getPages());
                    }
                });
                break;
            case 5:
                mData.sort(new Comparator<Document>() {
                    @Override
                    public int compare(Document doc1, Document doc2) {
                        return doc2.getPages().compareTo(doc1.getPages());
                    }
                });
                break;
            default:
                break;
        }
        for(int i = 0; i < mData.size(); i++) {
            if(mData.get(i).isArchived() != includeArchived) {
                mData.remove(i);
                i--;
            }
        }
    }

}
