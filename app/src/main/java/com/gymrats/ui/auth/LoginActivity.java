package com.gymrats.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import com.google.android.material.color.MaterialColors;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;

    private TextInputEditText editEmail;
    private TextInputEditText editPassword;
    private Button btnLogin;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);

        // 회원가입 텍스트 색상 변경
        TextView textGoSignUp = findViewById(R.id.textGoSignUp);
        int primary = MaterialColors.getColor(textGoSignUp, androidx.appcompat.R.attr.colorPrimary);
        String fullText = textGoSignUp.getText().toString();
        String highlight = "회원가입";
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

        btnLogin.setOnClickListener(v -> login());
        textGoSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));

        // 화면 초기화 후 기존 로그인 사용자의 문서도 확인
        if (auth.getCurrentUser() != null) {
            goToMain();
        }
    }

    private void login() {
        String email = getText(editEmail);
        String password = getText(editPassword);

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goToMain();
                    } else {
                        Toast.makeText(this,
                                AuthErrorMessages.from(task.getException()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void goToMain() {
        if (auth.getCurrentUser() == null) {
            return;
        }

        setLoading(true);

        UserDocumentManager.createIfMissing(
                auth.getCurrentUser().getUid()
        ).addOnCompleteListener(this, task -> {
            setLoading(false);

            if (task.isSuccessful()) {
                openMainActivity();
            } else {
                Toast.makeText(
                        this,
                        "회원 정보 준비에 실패했습니다. 네트워크를 확인한 후 다시 로그인해주세요.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
