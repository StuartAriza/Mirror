package com.stuart.smartmirror;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class NoteEditorActivity extends Activity {
    static final String EXTRA_NOTE_ID = "note_id";

    private static final int[] PAPER_COLORS = {
            0xFFFFF59D,
            0xFFF8BBD0,
            0xFFB3E5FC,
            0xFFC8E6C9,
            0xFFFFFFFF
    };
    private static final float[] PEN_WIDTHS = {0.007f, 0.012f, 0.022f};

    private NoteRepository.Note note;
    private NoteCanvasView canvas;
    private Button penButton;
    private int penWidthIndex = 1;
    private boolean existingNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String noteId = getIntent().getStringExtra(EXTRA_NOTE_ID);
        note = noteId == null ? null : NoteRepository.find(this, noteId);
        existingNote = note != null;
        if (note == null) {
            note = NoteRepository.createNote();
        }
        buildScreen();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(32), dp(32), dp(32), dp(28));
        root.setBackgroundColor(Color.BLACK);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        Button back = toolbarButton(R.string.photo_booth_back);
        Button undo = toolbarButton(R.string.note_undo);
        penButton = toolbarButton(R.string.note_pen_medium);
        Button clear = toolbarButton(R.string.note_clear);
        Button delete = toolbarButton(R.string.note_delete);
        Button save = toolbarButton(R.string.note_save);
        toolbar.addView(back, weightedToolbarParams());
        toolbar.addView(undo, weightedToolbarParams());
        toolbar.addView(penButton, weightedToolbarParams());
        toolbar.addView(clear, weightedToolbarParams());
        if (existingNote) {
            toolbar.addView(delete, weightedToolbarParams());
        }
        toolbar.addView(save, weightedToolbarParams());

        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams colorsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        colorsParams.topMargin = dp(14);
        root.addView(colors, colorsParams);
        for (int color : PAPER_COLORS) {
            View swatch = colorSwatch(color);
            colors.addView(swatch, colorParams());
        }

        FrameLayout canvasHolder = new FrameLayout(this);
        canvasHolder.setForegroundGravity(Gravity.CENTER);
        LinearLayout.LayoutParams holderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        holderParams.topMargin = dp(20);
        root.addView(canvasHolder, holderParams);

        canvas = new NoteCanvasView(this);
        canvas.setNote(note);
        canvas.setEditable(true);
        canvas.setPenWidth(PEN_WIDTHS[penWidthIndex]);
        FrameLayout.LayoutParams canvasParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        canvasHolder.addView(canvas, canvasParams);

        TextView hint = new TextView(this);
        hint.setText(R.string.note_editor_hint);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(12);
        root.addView(hint, hintParams);

        back.setOnClickListener(view -> finish());
        undo.setOnClickListener(view -> canvas.undo());
        penButton.setOnClickListener(view -> cyclePenWidth());
        clear.setOnClickListener(view -> confirmClear());
        delete.setOnClickListener(view -> confirmDelete());
        save.setOnClickListener(view -> saveAndReturn());

        setContentView(root);
    }

    private Button toolbarButton(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(outlineBackground());
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private View colorSwatch(int color) {
        View swatch = new View(this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setStroke(dp(2), Color.WHITE);
        background.setCornerRadius(dp(4));
        swatch.setBackground(background);
        swatch.setContentDescription(getString(R.string.note_paper_color));
        swatch.setOnClickListener(view -> canvas.setPaperColor(color));
        return swatch;
    }

    private GradientDrawable outlineBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setStroke(dp(1), 0x77FFFFFF);
        background.setCornerRadius(dp(2));
        return background;
    }

    private LinearLayout.LayoutParams weightedToolbarParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(64), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams colorParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(70), dp(54));
        params.setMargins(dp(8), 0, dp(8), 0);
        return params;
    }

    private void cyclePenWidth() {
        penWidthIndex = (penWidthIndex + 1) % PEN_WIDTHS.length;
        canvas.setPenWidth(PEN_WIDTHS[penWidthIndex]);
        int label = penWidthIndex == 0
                ? R.string.note_pen_thin
                : penWidthIndex == 1
                ? R.string.note_pen_medium
                : R.string.note_pen_thick;
        penButton.setText(label);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.note_clear_title)
                .setMessage(R.string.note_clear_message)
                .setPositiveButton(R.string.note_clear, (dialog, which) -> canvas.clearDrawing())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.note_delete_title)
                .setMessage(R.string.note_delete_message)
                .setPositiveButton(R.string.note_delete, (dialog, which) -> {
                    NoteRepository.delete(this, note.id);
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveAndReturn() {
        NoteRepository.save(this, note);
        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
