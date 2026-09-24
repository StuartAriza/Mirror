package com.stuart.smartmirror;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MirrorSettingsActivity extends Activity {
    private EditText cityInput;
    private CheckBox fahrenheitInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Button[] buttons = buildSettingsScreen();
        Button save = buttons[0];
        Button cancel = buttons[1];

        cityInput.setText(WeatherRepository.getConfiguredCity(this));
        fahrenheitInput.setChecked(WeatherRepository.useFahrenheit(this));

        save.setOnClickListener(view -> saveSettings());
        cancel.setOnClickListener(view -> finish());
    }

    private void saveSettings() {
        String city = cityInput.getText().toString().trim();
        if (city.isEmpty()) {
            cityInput.setError(getString(R.string.city_required));
            cityInput.requestFocus();
            return;
        }

        WeatherRepository.saveConfiguration(this, city, fahrenheitInput.isChecked());
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private Button[] buildSettingsScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(64), dp(72), dp(64), dp(72));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = makeText(R.string.settings_title, 42, Color.WHITE);
        content.addView(title);

        TextView heading = makeText(R.string.settings_weather_heading, 28, Color.WHITE);
        LinearLayout.LayoutParams headingParams = wrapParams();
        headingParams.topMargin = dp(56);
        content.addView(heading, headingParams);

        TextView explanation = makeText(
                R.string.settings_weather_explanation, 19, 0xD9FFFFFF);
        LinearLayout.LayoutParams explanationParams = matchWrapParams();
        explanationParams.topMargin = dp(16);
        content.addView(explanation, explanationParams);

        cityInput = new EditText(this);
        cityInput.setHint(R.string.city_hint);
        cityInput.setTextColor(Color.WHITE);
        cityInput.setHintTextColor(0x8AFFFFFF);
        cityInput.setTextSize(20);
        cityInput.setSingleLine(true);
        cityInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        cityInput.setPadding(dp(20), 0, dp(20), 0);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(0xFF151515);
        inputBackground.setStroke(dp(1), 0x55FFFFFF);
        inputBackground.setCornerRadius(dp(10));
        cityInput.setBackground(inputBackground);
        LinearLayout.LayoutParams cityParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        cityParams.topMargin = dp(32);
        content.addView(cityInput, cityParams);

        fahrenheitInput = new CheckBox(this);
        fahrenheitInput.setText(R.string.use_fahrenheit);
        fahrenheitInput.setTextColor(Color.WHITE);
        fahrenheitInput.setTextSize(20);
        fahrenheitInput.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        checkParams.topMargin = dp(20);
        content.addView(fahrenheitInput, checkParams);

        Button save = new Button(this);
        save.setText(R.string.save_settings);
        save.setTextSize(18);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        saveParams.topMargin = dp(40);
        content.addView(save, saveParams);

        Button cancel = new Button(this);
        cancel.setText(R.string.cancel);
        cancel.setTextSize(18);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        cancelParams.topMargin = dp(16);
        content.addView(cancel, cancelParams);

        setContentView(scroll);
        return new Button[]{save, cancel};
    }

    private TextView makeText(int text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
