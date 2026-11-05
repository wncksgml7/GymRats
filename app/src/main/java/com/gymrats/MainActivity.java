package com.gymrats;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gymrats.ui.home.HomeFragment;
import com.gymrats.ui.community.CommunityFragment;
import com.gymrats.ui.profile.MyPageFragment;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 앱을 처음 실행했을 때 HomeFragment 표시
        if (savedInstanceState == null) {
            showFragment(new HomeFragment());
        }
        // Home 버튼 클릭
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            showFragment(new HomeFragment());
        });

        // Community 버튼 클릭
        findViewById(R.id.btnCommunity).setOnClickListener(v -> {
            showFragment(new CommunityFragment());
        });

        findViewById(R.id.btnMyPage).setOnClickListener(v -> {
            showFragment(new MyPageFragment());
        });

    }
    // Fragment를 교체하는 메서드
    private void showFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}