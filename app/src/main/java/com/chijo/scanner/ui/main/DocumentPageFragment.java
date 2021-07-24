package com.chijo.scanner.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.chijo.scanner.PhotoViewActivity;
import com.chijo.scanner.R;

/**
 * A placeholder fragment containing a simple view.
 */
public class DocumentPageFragment extends Fragment {
    // When requested, this adapter returns a DemoObjectFragment,
    // representing an object in the collection.
    DocumentPageAdapter documentPageAdapter;
    ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_photo_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        documentPageAdapter = new DocumentPageAdapter(this);
        viewPager = view.findViewById(R.id.document_view_pager);
        viewPager.setAdapter(documentPageAdapter);
    }

    public class DocumentPageAdapter extends FragmentStateAdapter {
        public DocumentPageAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            // Return a NEW fragment instance in createFragment(int)
            Fragment fragment = new ImageViewFragment();
            Bundle args = new Bundle();
            // Our object is just an integer :-P
            args.putInt(ImageViewFragment.ARG_OBJECT, position + 1);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getItemCount() {
            return 100;
        }
    }

    // Instances of this class are fragments representing a single
    // object in our collection.
    public class ImageViewFragment extends Fragment {
        public static final String ARG_OBJECT = "object";

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_photo_view, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            Bundle args = getArguments();
            //set imageview here
        }
    }

}
