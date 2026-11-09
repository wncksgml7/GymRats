package com.gymrats.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.gymrats.MainActivity;
import com.gymrats.R;

public class SignUpActivity extends AppCompatActivity {

    // Firebase 이메일 인증의 최소 비밀번호 길이
    private static final int MIN_PASSWORD_LENGTH = 6;

    private FirebaseAuth auth;

    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private TextInputEditText editPasswordConfirm;
    private Button btnSignUp;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        auth = FirebaseAuth.getInstance();

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editPasswordConfirm = findViewById(R.id.editPasswordConfirm);
        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);
        TextView textGoLogin = findViewById(R.id.textGoLogin);

        btnSignUp.setOnClickListener(v -> signUp());
        textGoLogin.setOnClickListener(v -> finish());

        // 로그인 텍스트 색상 변경
        TextView textGoSignUp = findViewById(R.id.textGoLogin);
        int primary = MaterialColors.getColor(textGoSignUp, androidx.appcompat.R.attr.colorPrimary);
        String fullText = textGoSignUp.getText().toString();
        String highlight = "로그인";
        int start = fullText.indexOf(highlight);

        if (start >= 0) {
            SpannableString spannable = new
                    SpannableString(fullText);
            spannable.setSpan(
                    new ForegroundColorSpan(primary),
                    start, start + highlight.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textGoSignUp.setText(spannable);
        }
    }

    private void signUp() {
        String email = getText(editEmail);
        String password = getText(editPassword);
        String passwordConfirm = getText(editPasswordConfirm);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            Toast.makeText(this, "비밀번호는 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(passwordConfirm)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        // 가입 성공 시 자동으로 로그인 상태가 되므로 메인으로 이동
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this,
                                AuthErrorMessages.from(task.getException()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!loading);
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
