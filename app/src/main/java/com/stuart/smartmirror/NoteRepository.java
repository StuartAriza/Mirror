package com.stuart.smartmirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class NoteRepository {
    private static final String PREFS = "mirror_post_its";
    private static final String KEY_NOTES = "notes";

    static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class Stroke {
        final int color;
        final float width;
        final List<Point> points = new ArrayList<>();

        Stroke(int color, float width) {
            this.color = color;
            this.width = width;
        }
    }

    static final class Note {
        final String id;
        int paperColor;
        float x;
        float y;
        float scale;
        float rotation;
        final List<Stroke> strokes = new ArrayList<>();

        Note(String id, int paperColor, float x, float y, float scale, float rotation) {
            this.id = id;
            this.paperColor = paperColor;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.rotation = rotation;
        }
    }

    private NoteRepository() {
    }

    static Note createNote() {
        return new Note(
                UUID.randomUUID().toString(),
                Color.rgb(255, 245, 157),
                0.08f,
                0.38f,
                1f,
                0f);
    }

    static List<Note> load(Context context) {
        List<Note> notes = new ArrayList<>();
        String raw = preferences(context).getString(KEY_NOTES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                notes.add(fromJson(array.getJSONObject(index)));
            }
        } catch (JSONException ignored) {
            // Keep the mirror usable if a partially written note cannot be read.
        }
        return notes;
    }

    static Note find(Context context, String id) {
        for (Note note : load(context)) {
            if (note.id.equals(id)) {
                return note;
            }
        }
        return null;
    }

    static void save(Context context, Note note) {
        List<Note> notes = load(context);
        boolean replaced = false;
        for (int index = 0; index < notes.size(); index++) {
            if (notes.get(index).id.equals(note.id)) {
                notes.set(index, note);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            notes.add(note);
        }
        write(context, notes);
    }

    static void move(Context context, String id, float x, float y) {
        List<Note> notes = load(context);
        for (Note note : notes) {
            if (note.id.equals(id)) {
                note.x = clamp(x);
                note.y = clamp(y);
                write(context, notes);
                return;
            }
        }
    }

    static void transform(Context context, String id, float x, float y,
            float scale, float rotation) {
        List<Note> notes = load(context);
        for (Note note : notes) {
            if (note.id.equals(id)) {
                note.x = clamp(x);
                note.y = clamp(y);
                note.scale = Math.max(0.65f, Math.min(1.65f, scale));
                note.rotation = normalizeRotation(rotation);
                write(context, notes);
                return;
            }
        }
    }

    static void delete(Context context, String id) {
        List<Note> notes = load(context);
        for (int index = notes.size() - 1; index >= 0; index--) {
            if (notes.get(index).id.equals(id)) {
                notes.remove(index);
            }
        }
        write(context, notes);
    }

    private static void write(Context context, List<Note> notes) {
        JSONArray array = new JSONArray();
        for (Note note : notes) {
            array.put(toJson(note));
        }
        preferences(context).edit().putString(KEY_NOTES, array.toString()).apply();
    }

    private static JSONObject toJson(Note note) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", note.id);
            object.put("paperColor", note.paperColor);
            object.put("x", note.x);
            object.put("y", note.y);
            object.put("scale", note.scale);
            object.put("rotation", note.rotation);
            JSONArray strokes = new JSONArray();
            for (Stroke stroke : note.strokes) {
                JSONObject strokeObject = new JSONObject();
                strokeObject.put("color", stroke.color);
                strokeObject.put("width", stroke.width);
                JSONArray points = new JSONArray();
                for (Point point : stroke.points) {
                    JSONArray pair = new JSONArray();
                    pair.put(point.x);
                    pair.put(point.y);
                    points.put(pair);
                }
                strokeObject.put("points", points);
                strokes.put(strokeObject);
            }
            object.put("strokes", strokes);
        } catch (JSONException ignored) {
            // JSONObject only receives primitive values here.
        }
        return object;
    }

    private static Note fromJson(JSONObject object) throws JSONException {
        Note note = new Note(
                object.getString("id"),
                object.optInt("paperColor", Color.rgb(255, 245, 157)),
                (float) object.optDouble("x", 0.08d),
                (float) object.optDouble("y", 0.38d),
                (float) object.optDouble("scale", 1d),
                (float) object.optDouble("rotation", 0d));
        JSONArray strokes = object.optJSONArray("strokes");
        if (strokes == null) {
            return note;
        }
        for (int strokeIndex = 0; strokeIndex < strokes.length(); strokeIndex++) {
            JSONObject strokeObject = strokes.getJSONObject(strokeIndex);
            Stroke stroke = new Stroke(
                    strokeObject.optInt("color", Color.rgb(32, 32, 32)),
                    (float) strokeObject.optDouble("width", 0.012d));
            JSONArray points = strokeObject.optJSONArray("points");
            if (points != null) {
                for (int pointIndex = 0; pointIndex < points.length(); pointIndex++) {
                    JSONArray pair = points.getJSONArray(pointIndex);
                    stroke.points.add(new Point(
                            (float) pair.getDouble(0),
                            (float) pair.getDouble(1)));
                }
            }
            if (!stroke.points.isEmpty()) {
                note.strokes.add(stroke);
            }
        }
        return note;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float normalizeRotation(float rotation) {
        while (rotation > 180f) rotation -= 360f;
        while (rotation < -180f) rotation += 360f;
        return rotation;
    }
}
