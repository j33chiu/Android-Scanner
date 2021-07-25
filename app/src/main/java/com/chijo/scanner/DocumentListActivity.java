package com.chijo.scanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;

import com.chijo.scanner.itemTouchHelper.ItemTouchHelperCallback2;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.core.ImageTranscoderType;
import com.facebook.imagepipeline.core.MemoryChunkType;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputFilter;
import android.text.Spanned;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;

public class DocumentListActivity extends AppCompatActivity implements DocumentListAdapter.ItemClickListener{

    private DocumentListAdapter documentListAdapter;
    private ArrayList<Document> documents = new ArrayList<>();

    private final int STORAGE_WRITE_REQUEST_CODE = 1;
    private final int STORAGE_READ_REQUEST_CODE = 2;
    private final int CAMERA_LAUNCH_REQUEST_CODE = 1;
    private final int DOCUMENT_OPEN_REQUEST_CODE = 2;

    private final int CAMERA_LAUNCH_MODE = 0;

    private SearchView searchView;
    private String invalidChars = "\n";
    private InputFilter searchViewFilter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence charSequence, int i, int i1, Spanned spanned, int i2, int i3) {
            if (charSequence != null && invalidChars.contains(("" + charSequence))) {
                return "";
            }
            return null;
        }
    };

    private boolean archiveMode = false;

    //TODO: start file explorer activity to create new document from files on device
    //TODO: use shared prefs to store path to each document, as well as the document info

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fresco.initialize(
                this,
                ImagePipelineConfig.newBuilder(this)
                        .setMemoryChunkType(MemoryChunkType.BUFFER_MEMORY)
                        .setImageTranscoderType(ImageTranscoderType.JAVA_TRANSCODER)
                        .experiment().setNativeCodeDisabled(true)
                        .build());
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
            }
        });

        FloatingActionButton archiveFab = findViewById(R.id.archive_toggle_fab);
        archiveFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                archiveMode = !archiveMode;
                documentListAdapter.toggleShowArchived(archiveMode);
                if(archiveMode) {
                    fab.hide();
                    ViewAnimations.archiveFabMove(view, true);
                    toolbar.setTitle(R.string.action_label_archive);
                }
                else {
                    fab.show();
                    ViewAnimations.archiveFabMove(view, false);
                    toolbar.setTitle(R.string.action_label_documents);
                }
            }
        });
        //storage permission check:
        storagePermissionCheck();
        loadDocuments();

        //documents list/grid viewer:
        RecyclerView documentListView = findViewById(R.id.recycler_document_list);
        documentListView.setLayoutManager(new LinearLayoutManager(this));
        documentListAdapter = new DocumentListAdapter(this, documents);
        documentListAdapter.setClickListener(this);
        documentListView.setAdapter(documentListAdapter);

        //itemtouchhelper
        ItemTouchHelper.Callback callback = new ItemTouchHelperCallback2(documentListAdapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(documentListView);
    }

    private void loadDocuments() {
        documents.clear();
        File parentDir = new File(AppConstants.DOCUMENT_SAVE_LOCATION);
        File[] documentsArray = parentDir.listFiles();
        if(documentsArray == null) {
            return;
        }
        for(File f : documentsArray) {
            Document d = loadDocumentFromPath(f.getPath());
            documents.add(d);
        }
    }

    private Document loadDocumentFromPath(String path) {
        File f = new File(path);
        String docName = "";
        String docDateTime = "";
        String docPages = "";
        String docPath = path;
        String docIsArchived = "";
        Bitmap docPreview = null;
        File[] pages = f.listFiles();
        for(File page : pages) {
            if(page.getName().equals("infoFile.txt")) {
                try (BufferedReader br = new BufferedReader(new FileReader(page))) {
                    docName = br.readLine();
                    docDateTime = br.readLine();
                    docPages = br.readLine();
                    docIsArchived = br.readLine();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return new Document(docPath, docName, docDateTime, docPages, docPreview, docIsArchived);
    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private void openCamera() {
        Intent intent = new Intent(this, CameraActivity2.class);
        intent.putExtra("mode", CAMERA_LAUNCH_MODE);
        startActivityForResult(intent, CAMERA_LAUNCH_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CAMERA_LAUNCH_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_OK) {
                //took a picture and made a new doc
                String path = data.getStringExtra("path");
                Document newDoc = loadDocumentFromPath(path);
                documents.add(newDoc);
                documentListAdapter.addDocument(newDoc);
                openDocViewer(data.getStringExtra("name"), path);
            }
        }
        else if(requestCode == DOCUMENT_OPEN_REQUEST_CODE) {
            if(resultCode == Activity.RESULT_OK) {
                //editted the document, so document information changed
                String path = data.getStringExtra("path");
                for(int i = 0; i < documents.size(); i++) {
                    if(documents.get(i).getPath().equals(path)) {
                        Document newDoc = loadDocumentFromPath(path);
                        documents.set(i, newDoc);
                        documentListAdapter.replaceDocument(newDoc);
                        break;
                    }
                }
            } else if(resultCode == AppConstants.DOCUMENT_DELETE_CODE) {
                //deleted document
                String path = data.getStringExtra("path");
                for(int i = 0; i < documents.size(); i++) {
                    if(documents.get(i).getPath().equals(path)) {
                        documentListAdapter.removeDocument(documents.get(i));
                        documents.remove(i);
                        break;
                    }
                }
            } else if(requestCode == AppConstants.DOCUMENT_REORDERED_CODE) {
                documentListAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onItemClick(View view, int position) {
        Document doc = documentListAdapter.getItem(position);
        //open document viewer
        openDocViewer(doc.getDocumentName(), doc.getPath());
    }

    @Override
    public void onItemLongClick(View view, int position) {
        //TODO: allow merging, group archiving
    }

    private void openDocViewer(String docName, String docPath) {
        Intent intent = new Intent(this, DocumentViewActivity.class);
        intent.putExtra("documentName", docName);
        intent.putExtra("path", docPath);
        startActivityForResult(intent, DOCUMENT_OPEN_REQUEST_CODE);
    }

    private void storagePermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_WRITE_REQUEST_CODE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_READ_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_WRITE_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
        if (requestCode == STORAGE_READ_REQUEST_CODE) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        getMenuInflater().inflate(R.menu.search_menu, menu);
        //setup search
        searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                documentListAdapter.filter(query.toLowerCase());
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                documentListAdapter.filter(newText.toLowerCase());
                return false;
            }
        });
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
            case R.id.action_settings:
                return true;
            case R.id.action_sort:
                return true;
            case R.id.action_layout:
                return true;
            case R.id.search:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //loadDocuments();
        //documentListAdapter.notifyDataSetChanged();
        OpenCVLoader.initDebug();
    }
}
