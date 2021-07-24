package com.chijo.scanner;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.chijo.scanner.ui.main.DocumentPageFragment;

public class PhotoViewActivity extends AppCompatActivity {

    //TODO: <PRIORITY> https://stackoverflow.com/questions/54643379/proper-implementation-of-viewpager2-in-android

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);
        ViewPager viewPager = findViewById(R.id.document_view_pager);
    }
}