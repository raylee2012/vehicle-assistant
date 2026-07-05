package com.example.vehicleassistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.vehicleassistant.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_MOCK_FIRST = "mock_first";
    private static final String KEY_MODEL = "model"; // "0.5b" | "1.5b"

    private SwitchCompat switchMockFirst;
    private TextView btnModel05B;
    private TextView btnModel15B;

    public static boolean isMockFirst(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MOCK_FIRST, true);
    }

    /** 返回当前选择的模型: "0.5b" 或 "1.5b"，默认 "1.5b" */
    public static String getModel(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MODEL, "1.5b");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchMockFirst = findViewById(R.id.switch_mock_first);
        btnModel05B = findViewById(R.id.btn_model_05b);
        btnModel15B = findViewById(R.id.btn_model_15b);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        switchMockFirst.setChecked(prefs.getBoolean(KEY_MOCK_FIRST, true));

        String currentModel = prefs.getString(KEY_MODEL, "1.5b");
        updateModelSelection(currentModel);

        switchMockFirst.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(KEY_MOCK_FIRST, checked).apply());

        btnModel05B.setOnClickListener(v -> {
            prefs.edit().putString(KEY_MODEL, "0.5b").apply();
            updateModelSelection("0.5b");
        });

        btnModel15B.setOnClickListener(v -> {
            prefs.edit().putString(KEY_MODEL, "1.5b").apply();
            updateModelSelection("1.5b");
        });
    }

    private void updateModelSelection(String model) {
        boolean is05B = "0.5b".equals(model);
        btnModel05B.setSelected(is05B);
        btnModel15B.setSelected(!is05B);
        btnModel05B.setTextColor(is05B ? Color.WHITE :
                getColor(R.color.primary));
        btnModel15B.setTextColor(!is05B ? Color.WHITE :
                getColor(R.color.primary));
    }

    public void onBack(View view) {
        finish();
    }
}
