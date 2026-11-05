package com.gymrats;

import android.os.Bundle;

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


import com.gymrats.ui.home.HomeFragment;
import com.gymrats.ui.community.CommunityFragment;
import com.gymrats.ui.profile.MyPageFragment;


public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // BottomNavigationView 가져오기
        BottomNavigationView bottomNavigation =
                findViewById(R.id.bottomNavigation);
        // BottomNavigationView와 NavController 연결
        NavigationUI.setupWithNavController(
                bottomNavigation,
                navController
        );
    }
}