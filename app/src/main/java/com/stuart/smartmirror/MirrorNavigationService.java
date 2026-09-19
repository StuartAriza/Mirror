package com.stuart.smartmirror;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MirrorNavigationService extends AccessibilityService {
    private WindowManager windowManager;
    private View navigationStrip;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        showNavigationStrip();
    }

    private void showNavigationStrip() {
        if (navigationStrip != null) {
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setPadding(dp(4), dp(6), dp(4), dp(6));
        strip.setContentDescription(getString(R.string.navigation_service_name));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xB3000000);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0x55FFFFFF);
        strip.setBackground(background);

        strip.addView(makeButton("‹", R.string.back, GLOBAL_ACTION_BACK));
        strip.addView(makeButton("○", R.string.home, GLOBAL_ACTION_HOME));
        strip.addView(makeButton("□", R.string.recents, GLOBAL_ACTION_RECENTS));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(60),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = dp(6);

        navigationStrip = strip;
        windowManager.addView(navigationStrip, params);
    }

    private TextView makeButton(String glyph, int description, int action) {
        TextView button = new TextView(this);
        button.setText(glyph);
        button.setTextColor(Color.WHITE);
        button.setTextSize(30);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(getString(description));
        button.setMinHeight(dp(60));
        button.setMinWidth(dp(52));
        button.setOnClickListener(view -> performGlobalAction(action));
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This service intentionally does not inspect app content or events.
    }

    @Override
    public void onInterrupt() {
        // No ongoing feedback to interrupt.
    }

    @Override
    public void onDestroy() {
        if (navigationStrip != null && windowManager != null) {
            windowManager.removeView(navigationStrip);
            navigationStrip = null;
        }
        super.onDestroy();
    }
}

