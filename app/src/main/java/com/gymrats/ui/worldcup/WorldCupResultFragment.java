package com.gymrats.ui.worldcup;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.gymrats.R;
import com.gymrats.databinding.FragmentWorldCupResultBinding;

public class WorldCupResultFragment extends Fragment {

    public WorldCupResultFragment() {
        super(R.layout.fragment_world_cup_result);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        FragmentWorldCupResultBinding binding =
                FragmentWorldCupResultBinding.bind(view);

        binding.tvWinnerTitle.setText(
                requireArguments().getString("winnerTitle")
        );

        binding.btnViewRanking.setOnClickListener(v ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.worldCupRankingFragment));

        binding.btnCloseResult.setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack());
    }
}