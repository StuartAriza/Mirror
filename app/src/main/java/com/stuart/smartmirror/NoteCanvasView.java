package com.stuart.smartmirror;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

class NoteCanvasView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path paperPath = new Path();
    private NoteRepository.Note note;
    private NoteRepository.Stroke activeStroke;
    private boolean editable;
    private float penWidth = 0.012f;

    NoteCanvasView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setNote(NoteRepository.Note note) {
        this.note = note;
        invalidate();
    }

    NoteRepository.Note getNote() {
        return note;
    }

    void setEditable(boolean editable) {
        this.editable = editable;
    }

    void setPenWidth(float penWidth) {
        this.penWidth = penWidth;
    }

    void setPaperColor(int color) {
        if (note != null) {
            note.paperColor = color;
            invalidate();
        }
    }

    void undo() {
        if (note != null && !note.strokes.isEmpty()) {
            note.strokes.remove(note.strokes.size() - 1);
            invalidate();
        }
    }

    void clearDrawing() {
        if (note != null) {
            note.strokes.clear();
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!editable) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightLimit = MeasureSpec.getSize(heightMeasureSpec);
        int size = heightLimit == 0 ? width : Math.min(width, heightLimit);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (note == null) {
            return;
        }
        drawPaper(canvas);
        drawStrokes(canvas);
        drawPin(canvas);
    }

    private void drawPaper(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float inset = Math.max(5f, Math.min(width, height) * 0.025f);
        float fold = Math.min(width, height) * 0.14f;

        paperPath.reset();
        paperPath.moveTo(inset, inset);
        paperPath.lineTo(width - inset, inset);
        paperPath.lineTo(width - inset, height - fold);
        paperPath.lineTo(width - fold, height - inset);
        paperPath.lineTo(inset, height - inset);
        paperPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(note.paperColor);
        paint.setShadowLayer(Math.max(5f, fold * 0.12f), 0f, fold * 0.08f, 0x77000000);
        canvas.drawPath(paperPath, paint);
        paint.clearShadowLayer();

        Path corner = new Path();
        corner.moveTo(width - inset, height - fold);
        corner.lineTo(width - fold, height - inset);
        corner.lineTo(width - inset, height - inset);
        corner.close();
        paint.setColor(darken(note.paperColor));
        canvas.drawPath(corner, paint);
    }

    private void drawStrokes(Canvas canvas) {
        float scale = Math.min(getWidth(), getHeight());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (NoteRepository.Stroke stroke : note.strokes) {
            if (stroke.points.isEmpty()) {
                continue;
            }
            paint.setColor(stroke.color);
            paint.setStrokeWidth(Math.max(2f, stroke.width * scale));
            if (stroke.points.size() == 1) {
                NoteRepository.Point point = stroke.points.get(0);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(point.x * getWidth(), point.y * getHeight(),
                        paint.getStrokeWidth() / 2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                continue;
            }
            Path path = new Path();
            NoteRepository.Point first = stroke.points.get(0);
            path.moveTo(first.x * getWidth(), first.y * getHeight());
            for (int index = 1; index < stroke.points.size(); index++) {
                NoteRepository.Point point = stroke.points.get(index);
                path.lineTo(point.x * getWidth(), point.y * getHeight());
            }
            canvas.drawPath(path, paint);
        }
    }

    private void drawPin(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = size * 0.065f;
        float radius = size * 0.045f;

        Path point = new Path();
        point.moveTo(cx - radius * 0.28f, cy + radius * 0.65f);
        point.lineTo(cx + radius * 0.28f, cy + radius * 0.65f);
        point.lineTo(cx, cy + radius * 2.1f);
        point.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF8B1E24);
        canvas.drawPath(point, paint);

        paint.setColor(0xFFD94149);
        paint.setShadowLayer(radius * 0.42f, radius * 0.18f, radius * 0.32f, 0x88000000);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.clearShadowLayer();
        paint.setColor(0x66FFFFFF);
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.3f, radius * 0.23f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable || note == null) {
            return false;
        }
        float x = clamp(event.getX() / Math.max(1f, getWidth()));
        float y = clamp(event.getY() / Math.max(1f, getHeight()));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeStroke = new NoteRepository.Stroke(Color.rgb(32, 32, 32), penWidth);
                activeStroke.points.add(new NoteRepository.Point(x, y));
                note.strokes.add(activeStroke);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeStroke != null) {
                    NoteRepository.Point previous = activeStroke.points.get(
                            activeStroke.points.size() - 1);
                    float dx = x - previous.x;
                    float dy = y - previous.y;
                    if (dx * dx + dy * dy > 0.000004f) {
                        activeStroke.points.add(new NoteRepository.Point(x, y));
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                activeStroke = null;
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                activeStroke = null;
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private static int darken(int color) {
        return Color.rgb(
                Math.round(Color.red(color) * 0.82f),
                Math.round(Color.green(color) * 0.82f),
                Math.round(Color.blue(color) * 0.82f));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
