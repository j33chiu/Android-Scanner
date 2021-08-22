package com.chijo.scanner.pageviewer;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.chijo.scanner.FileHelper;
import com.chijo.scanner.R;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executors;

public class PhotoViewActivity extends AppCompatActivity {

    private ViewPager2 viewPager2;
    private PageViewerAdapter pageViewerAdapter;
    private int currentPosition = 0;
    private int totalPages = 0;
    private String documentPath = "";

    private final int BITMAP_DISPLAY_SCALE = 1; // use full scale load

    //TODO: add edit options (filters, recrop, enhancing)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);
        //getSupportActionBar().setTitle("test");

        //get document pages
        documentPath = getIntent().getStringExtra("documentPath");
        currentPosition = getIntent().getIntExtra("pagePosition", 0);
        totalPages = getIntent().getIntExtra("totalPages", 0);

        Bitmap[] bitmapPages = backgroundImageLoad();

        viewPager2 = findViewById(R.id.document_view_pager2);
        pageViewerAdapter = new PageViewerAdapter(this, bitmapPages, currentPosition);
        viewPager2.setAdapter(pageViewerAdapter);
        viewPager2.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                // sets viewpager to the page the user tapped in the document view activity
                viewPager2.setCurrentItem(currentPosition, false);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return super.onOptionsItemSelected(item);
    }

    //loads pictures in background so tapped image can be displayed as fast as possible
    public Bitmap[] backgroundImageLoad() {
        Bitmap[] bitmapPages = new Bitmap[totalPages];

        Runnable loadBitmaps = new Runnable() {
            @Override
            public void run() {
                //load image selected by user
                File documentFolder = new File(documentPath);
                if(!documentFolder.exists()) return;
                File[] files = documentFolder.listFiles();
                Arrays.sort(files);
                if(files == null) return;

                Bitmap currentBmp = FileHelper.getBitmap(documentPath + "/" + currentPosition + "_modded.jpg", BITMAP_DISPLAY_SCALE);
                if (currentBmp == null) {
                    return;
                }
                bitmapPages[currentPosition] = currentBmp;

                //update UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pageViewerAdapter.notifyItemChanged(currentPosition);
                    }
                });

                //load other images selected by user
                for (int i = 0; i < totalPages; i++) {
                    if (i == currentPosition) continue;
                    Bitmap bmp = FileHelper.getBitmap(documentPath + "/" + i + "_modded.jpg", BITMAP_DISPLAY_SCALE);
                    if (bmp == null) {
                        continue;
                    }
                    bitmapPages[i] = bmp;
                    //update UI
                    int finalI = i;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pageViewerAdapter.notifyItemChanged(finalI);
                        }
                    });
                }
            }
        };
        Executors.newSingleThreadExecutor().execute(loadBitmaps);

        return bitmapPages;
    }
}