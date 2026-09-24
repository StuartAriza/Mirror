package com.stuart.smartmirror;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Selects a lightweight weather drawable for an Open-Meteo WMO weather code. */
final class WeatherIconView extends ImageView {
    WeatherIconView(Context context) {
        super(context);
        prepare();
    }

    WeatherIconView(Context context, AttributeSet attributes) {
        super(context, attributes);
        prepare();
    }

    void setWeather(int code, boolean isDay) {
        setImageResource(drawableFor(code, isDay));
    }

    private void prepare() {
        setScaleType(ScaleType.CENTER_INSIDE);
        setColorFilter(Color.WHITE);
    }

    private int drawableFor(int code, boolean isDay) {
        if (code == 0 || code == 1) {
            return isDay ? R.drawable.weather_sunny : R.drawable.weather_clear_night;
        }
        if (code == 2) {
            return isDay
                    ? R.drawable.weather_partly_cloudy
                    : R.drawable.weather_partly_cloudy_night;
        }
        if (code == 3) {
            return isDay
                    ? R.drawable.weather_cloudy
                    : R.drawable.weather_partly_cloudy_night;
        }
        if (code == 45 || code == 48) {
            return R.drawable.weather_fog;
        }
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            return R.drawable.weather_snow;
        }
        if (code >= 95) {
            return R.drawable.weather_thunder;
        }
        return R.drawable.weather_rain;
    }
}
