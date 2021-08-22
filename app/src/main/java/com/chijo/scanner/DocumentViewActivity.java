package com.chijo.scanner;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;

import com.chijo.scanner.itemTouchHelper.ItemTouchHelperCallback;
import com.chijo.scanner.pageviewer.PhotoViewActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DocumentViewActivity extends AppCompatActivity implements DocumentViewAdapter.ItemClickListener {
    //variables:
    private String documentName;
    private String documentPath;
    private boolean changesMade = false; //reordered, deleted or added pages
    private Intent returnResult = new Intent();
    private int resultCode = 0;
    private String isArchived = "false";
    //recycler view:
    private DocumentViewAdapter documentViewAdapter;
    private List<Bitmap> pages = new ArrayList<>();
    private List<String> pageNames = new ArrayList<>();
    //rename edittext:
    private TextInputEditText renameET;
    private String invalidChars = "\n";
    private InputFilter renameETFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3) {
            if (charSequence != null && invalidChars.contains(("" + charSequence))) {
                return "";
            }
            return null;
        }
    };
    //FAB
    private FloatingActionButton addPhotoFab;
    private final int CAMERA_LAUNCH_REQUEST_CODE = 1;
    private final int CAMERA_LAUNCH_MODE = 1;

    private final int EDIT_PICTURE_REQUEST_CODE = 2;

    private ItemTouchHelper itemTouchHelper;

    //TODO: add ability to "read" text from document pages: viewable from edit screen, highlightable/copyable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_view);
        Toolbar toolbar = findViewById(R.id.document_view_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        documentName = getIntent().getStringExtra("documentName");
        documentPath = getIntent().getStringExtra("path");

        renameET = findViewById(R.id.rename_document_ET);
        renameET.setText(documentName);
        renameET.setFilters(new InputFilter[]{renameETFilter});

        loadPages();
        //pages list viewer:
        RecyclerView documentPagesView = findViewById(R.id.recycler_document_pages);
        documentPagesView.setLayoutManager(new LinearLayoutManager(this));
        documentViewAdapter = new DocumentViewAdapter(this, pages, pageNames, documentPath);
        documentViewAdapter.setClickListener(this);
        documentPagesView.setAdapter(documentViewAdapter);

        //itemtouchhelper
        ItemTouchHelper.Callback callback = new ItemTouchHelperCallback(documentViewAdapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(documentPagesView);

        addPhotoFab = findViewById(R.id.addPhotoFab);
        addPhotoFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
            }
        });
    }

    private void openCamera() {
        Intent intent = new Intent(this, CameraActivity2.class);
        intent.putExtra("mode", CAMERA_LAUNCH_MODE);
        intent.putExtra("path", documentPath + "/");
        intent.putExtra("priorPictures", pages.size());
        intent.putExtra("docName", documentName);
        startActivityForResult(intent, CAMERA_LAUNCH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_LAUNCH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                //took a picture
                changesMade = true;
                String path = data.getStringExtra("path");
                ArrayList<String> pageList = data.getStringArrayListExtra("fileList");
                for(String s : pageList) {
                    File f = new File(path + s);
                    int orientation = -1;
                    Matrix m = new Matrix();
                    try {
                        ExifInterface exif = new ExifInterface(f.getPath());
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                        if(orientation == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
                        else if(orientation == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
                        else if(orientation == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getPath(), options);
                    int scale = 8;
                    BitmapFactory.Options loadOptions = new BitmapFactory.Options();
                    loadOptions.inSampleSize = scale;
                    Bitmap loaded = BitmapFactory.decodeFile(f.getPath(), loadOptions);
                    if(orientation != -1) {
                        loaded = Bitmap.createBitmap(loaded, 0, 0, loaded.getWidth(), loaded.getHeight(), m, true);
                    }
                    pages.add(loaded);
                    pageNames.add(f.getName());
                    documentViewAdapter.addPage(loaded, f.getName());
                }
            }
        }
        else if (requestCode == EDIT_PICTURE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                //pictures edited
            }
        }
    }

    private void loadPages() {
        pages.clear();
        pageNames.clear();
        File documentFolder = new File(documentPath);
        if(!documentFolder.exists()) return;
        File[] files = documentFolder.listFiles();
        Arrays.sort(files);
        if(files == null) return;
        for(File f : files) {
            if(!f.getName().equals("infoFile.txt") && f.getName().contains("_modded.jpg")) {
                //load scaled down:
                /*BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                bitmapOptions.inSampleSize = 8;
                pages.add(BitmapFactory.decodeFile(f.getPath(), bitmapOptions));*/

                Bitmap loaded = FileHelper.getBitmap(f.getPath());
                pages.add(loaded);
                pageNames.add(f.getName());
            }
            else if(f.getName().equals("infoFile.txt")) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String docName = br.readLine();
                    String docDateTime = br.readLine();
                    String docPages = br.readLine();
                    isArchived = br.readLine();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        //try sending document path and page, then in edit activity load the bitmaps in the background
        Intent editActivity = new Intent(this, PhotoViewActivity.class);
        editActivity.putExtra("documentPath", documentPath);
        editActivity.putExtra("pagePosition", position);
        editActivity.putExtra("totalPages", documentViewAdapter.getItemCount());

        startActivityForResult(editActivity, EDIT_PICTURE_REQUEST_CODE);
    }

    @Override
    public void onItemLongClick(View view, int position) {
        //Toast.makeText(this, "long click", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_document_view, menu);
        getMenuInflater().inflate(R.menu.menu_share, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        switch(id){
            case R.id.action_document_help:
                Toast.makeText(this, "Horizontal swipe on pictures to delete\nHold and vertical drag to reorder", Toast.LENGTH_LONG).show();
                return true;
            case R.id.action_text_detect:
                Toast.makeText(this, "Not implemented yet...", Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void deleteDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteDirectory(child);
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if(!documentName.equals(renameET.getText().toString()) || changesMade || documentViewAdapter.getChangesMade()) {
            resultCode = Activity.RESULT_OK;
            returnResult.putExtra("path", documentPath);
            saveDocument();
        }
        setResult(resultCode, returnResult);
        finish();
    }

    private void saveDocument() {
        int numPages = documentViewAdapter.getItemCount();
        String docName = renameET.getText().toString();
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        String pages = numPages + (numPages == 1 ? " page" : " pages");
        //write to infofile
        FileHelper.writeAll(documentPath, docName, dateTime, pages, isArchived == null ? "false" : isArchived);
    }



}
