package com.example.vehicleassistant.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.vehicleassistant.R;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_MOCK_FIRST = "mock_first";

    private SwitchCompat switchMockFirst;

    public static boolean isMockFirst(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MOCK_FIRST, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchMockFirst = findViewById(R.id.switch_mock_first);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        switchMockFirst.setChecked(prefs.getBoolean(KEY_MOCK_FIRST, true));

        switchMockFirst.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(KEY_MOCK_FIRST, checked).apply());
    }

    public void onBack(View view) {
        finish();
    }
}
