package com.gymrats;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;


import com.gymrats.ui.auth.LoginActivity;
import com.gymrats.ui.home.HomeFragment;
import com.gymrats.ui.community.CommunityFragment;
import com.gymrats.ui.profile.MyPageFragment;


public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 로그인되지 않은 상태면 로그인 화면으로 이동
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // 1. NavHostFragment 가져오기
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.fragmentContainer);

        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment not found");
        }

        // NavController 가져오기
        navController = navHostFragment.getNavController();

        // 하단 네비게이션 연결
        BottomNavigationView bottomNavigation =
                findViewById(R.id.bottomNavigation);

        NavigationUI.setupWithNavController(
                bottomNavigation,
                navController
        );

        // Community 상단 탭
        View communityTabs = findViewById(R.id.communityTabs);

        Button btnExerciseFeed =
                findViewById(R.id.btnExerciseFeed);

        Button btnWorldCup =
                findViewById(R.id.btnWorldCup);

        // 현재 화면에 따라 상단 탭 표시 여부 결정
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {

                    int currentId = destination.getId();

                    boolean isCommunityScreen =
                            currentId == R.id.communityFragment ||
                                    currentId == R.id.worldCupFragment;

                    communityTabs.setVisibility(
                            isCommunityScreen ? View.VISIBLE : View.GONE
                    );

                    // 현재 화면에 해당하는 탭은 비활성화
                    btnExerciseFeed.setEnabled(
                            currentId != R.id.communityFragment
                    );

                    btnWorldCup.setEnabled(
                            currentId != R.id.worldCupFragment
                    );
                }
        );

        // 이상형 월드컵 탭
        btnWorldCup.setOnClickListener(v -> {
            navController.navigate(R.id.worldCupFragment);
        });

        // 대체 운동법 탭
        btnExerciseFeed.setOnClickListener(v -> {
            navController.popBackStack(
                    R.id.communityFragment,
                    false
            );
        });
    }
}