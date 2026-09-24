package com.stuart.smartmirror;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

final class PostItView extends NoteCanvasView {
    interface Listener {
        void onTransformed(NoteRepository.Note note, float x, float y);

        void onOpen(NoteRepository.Note note);
    }

    private final Listener listener;
    private final int touchSlop;
    private final int baseSize;
    private float downRawX;
    private float downRawY;
    private float startX;
    private float startY;
    private float startScale;
    private float startRotation;
    private float startAngle;
    private float startDistance;
    private float centerParentX;
    private float centerParentY;
    private boolean moved;
    private int gestureMode;

    private static final int MODE_MOVE = 0;
    private static final int MODE_RESIZE = 1;
    private static final int MODE_ROTATE = 2;

    PostItView(Context context, NoteRepository.Note note, int baseSize, Listener listener) {
        super(context);
        this.listener = listener;
        this.baseSize = baseSize;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setNote(note);
        setEditable(false);
        setContentDescription(context.getString(R.string.open_post_it));
        setClickable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        View parentView = (View) getParent();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                startX = getX();
                startY = getY();
                startScale = getNote().scale;
                startRotation = getNote().rotation;
                centerParentX = startX + getWidth() / 2f;
                centerParentY = startY + getHeight() / 2f;
                gestureMode = gestureModeFor(event.getX(), event.getY());
                startAngle = angleToCenter(event.getRawX(), event.getRawY());
                startDistance = distanceToCenter(event.getRawX(), event.getRawY());
                moved = false;
                bringToFront();
                animate().scaleX(1.03f).scaleY(1.03f).setDuration(80L).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                    moved = true;
                }
                if (gestureMode == MODE_ROTATE) {
                    float angle = angleToCenter(event.getRawX(), event.getRawY());
                    float rotation = normalizeRotation(startRotation + angle - startAngle);
                    getNote().rotation = rotation;
                    setRotation(rotation);
                } else if (gestureMode == MODE_RESIZE) {
                    float distance = distanceToCenter(event.getRawX(), event.getRawY());
                    float scale = startDistance <= 1f
                            ? startScale
                            : startScale * distance / startDistance;
                    scale = Math.max(0.65f, Math.min(1.65f, scale));
                    getNote().scale = scale;
                    int newSize = Math.round(baseSize * scale);
                    getLayoutParams().width = newSize;
                    getLayoutParams().height = newSize;
                    requestLayout();
                    setX(centerParentX - newSize / 2f);
                    setY(centerParentY - newSize / 2f);
                    clampInside(parentView);
                } else {
                    setX(startX + dx);
                    setY(startY + dy);
                    clampInside(parentView);
                }
                return true;
            case MotionEvent.ACTION_UP:
                animate().scaleX(1f).scaleY(1f).setDuration(80L).start();
                if (moved || gestureMode != MODE_MOVE) {
                    float availableX = Math.max(1f, parentView.getWidth() - getWidth());
                    float availableY = Math.max(1f, parentView.getHeight() - getHeight());
                    listener.onTransformed(getNote(), getX() / availableX, getY() / availableY);
                } else {
                    performClick();
                    listener.onOpen(getNote());
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                animate().scaleX(1f).scaleY(1f).setDuration(80L).start();
                return true;
            default:
                return true;
        }
    }

    private int gestureModeFor(float x, float y) {
        float size = Math.min(getWidth(), getHeight());
        float pinX = getWidth() / 2f;
        float pinY = size * 0.065f;
        float pinRadius = size * 0.15f;
        float pinDx = x - pinX;
        float pinDy = y - pinY;
        if (pinDx * pinDx + pinDy * pinDy <= pinRadius * pinRadius) {
            return MODE_ROTATE;
        }
        if (x >= getWidth() * 0.72f && y >= getHeight() * 0.72f) {
            return MODE_RESIZE;
        }
        return MODE_MOVE;
    }

    private float angleToCenter(float rawX, float rawY) {
        int[] parentLocation = new int[2];
        ((View) getParent()).getLocationOnScreen(parentLocation);
        float centerRawX = parentLocation[0] + centerParentX;
        float centerRawY = parentLocation[1] + centerParentY;
        return (float) Math.toDegrees(Math.atan2(rawY - centerRawY, rawX - centerRawX));
    }

    private float distanceToCenter(float rawX, float rawY) {
        int[] parentLocation = new int[2];
        ((View) getParent()).getLocationOnScreen(parentLocation);
        float dx = rawX - (parentLocation[0] + centerParentX);
        float dy = rawY - (parentLocation[1] + centerParentY);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void clampInside(View parentView) {
        float maxX = Math.max(0f, parentView.getWidth() - getWidth());
        float maxY = Math.max(0f, parentView.getHeight() - getHeight());
        setX(Math.max(0f, Math.min(maxX, getX())));
        setY(Math.max(0f, Math.min(maxY, getY())));
    }

    private static float normalizeRotation(float rotation) {
        while (rotation > 180f) rotation -= 360f;
        while (rotation < -180f) rotation += 360f;
        return rotation;
    }
}
