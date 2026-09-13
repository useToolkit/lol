package com.usetoolkit.btcfloating;

import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

public class SafeMainActivity extends MainActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setOnApplyWindowInsetsListener((v, insets) -> {
                int left = insets.getSystemWindowInsetLeft();
                int top = insets.getSystemWindowInsetTop();
                int right = insets.getSystemWindowInsetRight();
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(left, top, right, bottom);
                return insets;
            });
            content.requestApplyInsets();
        }
    }
}
