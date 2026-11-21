package com.gymrats.ui.community;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.gymrats.R;

public class ReviewCreateFragment extends Fragment {
    public ReviewCreateFragment() { super(R.layout.fragment_review_create); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        String postId = requireArguments().getString("postId");
        ReviewDraftViewModel model = new ViewModelProvider(this).get(ReviewDraftViewModel.class);
        EditText content = view.findViewById(R.id.editReviewContent);
        RatingBar rating = view.findViewById(R.id.reviewStars);
        Button submit = view.findViewById(R.id.btnSubmitReview);
        TextView count = view.findViewById(R.id.textReviewLength);
        TextView message = view.findViewById(R.id.textReviewError);
        ((TextView) view.findViewById(R.id.textReviewPostTitle)).setText(requireArguments().getString("postTitle", "운동법"));
        content.setText(model.content());
        rating.setRating(model.rating());
        Runnable update = () -> {
            int score = (int) rating.getRating();
            String text = content.getText().toString();
            model.draft(text, score);
            count.setText(text.length() + "/500 · 최소 10자");
            ((TextView) view.findViewById(R.id.textReviewScore)).setText(score == 0 ? "별점을 선택해 주세요" : score + "점 / 5점");
            submit.setEnabled(!Boolean.TRUE.equals(model.saving().getValue()) && text.trim().length() >= 10 && score >= 1);
        };
        content.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { update.run(); }
            public void afterTextChanged(Editable s) { }
        });
        rating.setOnRatingBarChangeListener((bar, value, fromUser) -> update.run());
        view.findViewById(R.id.btnBackReview).setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        submit.setOnClickListener(v -> { message.setText(""); model.submit(postId); });
        model.saving().observe(getViewLifecycleOwner(), busy -> {
            content.setEnabled(!busy);
            rating.setEnabled(!busy);
            submit.setText(busy ? "등록 중…" : "후기 등록하기");
            update.run();
        });
        model.result().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            model.consumeResult();
            if (result.isEmpty()) {
                Toast.makeText(requireContext(), "후기를 등록했습니다.", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).popBackStack();
            } else message.setText(result);
        });
        update.run();
    }
}
