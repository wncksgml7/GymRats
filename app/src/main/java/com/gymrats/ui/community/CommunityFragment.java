package com.gymrats.ui.community;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.gymrats.R;

public class CommunityFragment extends Fragment {

    public CommunityFragment() {
        super(R.layout.fragment_community);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        // 등록 버튼을 누르면 게시글 작성 화면으로 이동
        view.findViewById(R.id.btnCreatePost).setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.postCreateFragment)
        );
    }
}