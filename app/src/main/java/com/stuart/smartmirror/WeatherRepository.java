package com.stuart.smartmirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.conscrypt.Conscrypt;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class WeatherRepository {
    private static volatile Throwable tlsSetupError;
    private static volatile OkHttpClient httpClient;
    private static final String PREFS = "mirror_weather";
    private static final String KEY_CITY = "city";
    private static final String KEY_FAHRENHEIT = "fahrenheit";
    private static final String KEY_LOCATION = "cached_location";
    private static final String KEY_TEMPERATURE = "cached_temperature";
    private static final String KEY_CONDITION = "cached_condition";
    private static final String KEY_HIGH = "cached_high";
    private static final String KEY_LOW = "cached_low";
    private static final String KEY_UNIT = "cached_unit";
    private static final String KEY_UPDATED_AT = "cached_updated_at";

    interface Callback {
        void onSuccess(WeatherData data);

        void onError(String message);
    }

    static final class WeatherData {
        final String location;
        final int temperature;
        final String condition;
        final int high;
        final int low;
        final String unit;
        final long updatedAt;

        WeatherData(
                String location,
                int temperature,
                String condition,
                int high,
                int low,
                String unit,
                long updatedAt) {
            this.location = location;
            this.temperature = temperature;
            this.condition = condition;
            this.high = high;
            this.low = low;
            this.unit = unit;
            this.updatedAt = updatedAt;
        }
    }

    private WeatherRepository() {
    }

    static void installModernTls() {
        try {
            Provider provider = Conscrypt.newProviderBuilder()
                    .setName("MirrorConscrypt")
                    .provideTrustManager(true)
                    .defaultTlsProtocol("TLSv1.2")
                    .build();
            Security.insertProviderAt(provider, 1);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm(), provider);
            trustManagerFactory.init((KeyStore) null);
            X509TrustManager trustManager = findTrustManager(
                    trustManagerFactory.getTrustManagers());
            SSLContext context = SSLContext.getInstance("TLS", provider);
            context.init(null, new TrustManager[]{trustManager}, null);
            httpClient = new OkHttpClient.Builder()
                    .sslSocketFactory(context.getSocketFactory(), trustManager)
                    .dns(new Ipv4Dns())
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
            tlsSetupError = null;
        } catch (Throwable error) {
            tlsSetupError = error;
        }
    }

    private static X509TrustManager findTrustManager(TrustManager[] managers)
            throws IOException {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IOException("No X509 trust manager is available");
    }

    static String getConfiguredCity(Context context) {
        return preferences(context).getString(KEY_CITY, "").trim();
    }

    static boolean useFahrenheit(Context context) {
        return preferences(context).getBoolean(KEY_FAHRENHEIT, false);
    }

    static void saveConfiguration(Context context, String city, boolean fahrenheit) {
        SharedPreferences prefs = preferences(context);
        boolean changed = !city.equals(prefs.getString(KEY_CITY, ""))
                || fahrenheit != prefs.getBoolean(KEY_FAHRENHEIT, false);
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_CITY, city)
                .putBoolean(KEY_FAHRENHEIT, fahrenheit);
        if (changed) {
            editor.remove(KEY_UPDATED_AT);
            editor.remove(KEY_LOCATION);
        }
        editor.apply();
    }

    static WeatherData readCache(Context context) {
        SharedPreferences prefs = preferences(context);
        long updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L);
        String location = prefs.getString(KEY_LOCATION, "");
        if (updatedAt == 0L || location.isEmpty()) {
            return null;
        }
        return new WeatherData(
                location,
                prefs.getInt(KEY_TEMPERATURE, 0),
                prefs.getString(KEY_CONDITION, ""),
                prefs.getInt(KEY_HIGH, 0),
                prefs.getInt(KEY_LOW, 0),
                prefs.getString(KEY_UNIT, "°C"),
                updatedAt);
    }

    static void fetch(Context context, ExecutorService executor, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final String city = getConfiguredCity(appContext);
        final boolean fahrenheit = useFahrenheit(appContext);

        executor.execute(() -> {
            try {
                if (tlsSetupError != null) {
                    String detail = tlsSetupError.getMessage();
                    throw new IOException("Modern TLS could not start: "
                            + tlsSetupError.getClass().getSimpleName()
                            + (detail == null ? "" : " - " + detail));
                }
                LocationResult location = geocode(city);
                WeatherData weather = requestForecast(location, fahrenheit);
                writeCache(appContext, weather);
                mainHandler.post(() -> callback.onSuccess(weather));
            } catch (Exception exception) {
                String message;
                if (exception instanceof LocationNotFoundException) {
                    message = "City not found. Check the spelling in Mirror settings.";
                } else {
                    String detail = exception.getMessage();
                    if (detail == null || detail.trim().isEmpty()) {
                        detail = exception.getClass().getSimpleName();
                    }
                    message = "Could not update weather: " + detail;
                }
                mainHandler.post(() -> callback.onError(message));
            }
        });
    }

    static String formatUpdatedTime(long timestamp) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(timestamp));
    }

    private static LocationResult geocode(String city) throws IOException, JSONException,
            LocationNotFoundException {
        String endpoint = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(city, "UTF-8")
                + "&count=1&language=en&format=json";
        JSONObject root = new JSONObject(readUrl(endpoint));
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new LocationNotFoundException();
        }

        JSONObject first = results.getJSONObject(0);
        String name = first.getString("name");
        String admin = first.optString("admin1", "");
        String displayName = admin.isEmpty() ? name : name + ", " + admin;
        return new LocationResult(
                displayName,
                first.getDouble("latitude"),
                first.getDouble("longitude"));
    }

    private static WeatherData requestForecast(LocationResult location, boolean fahrenheit)
            throws IOException, JSONException {
        String unitParameter = fahrenheit ? "fahrenheit" : "celsius";
        String endpoint = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min"
                        + "&temperature_unit=%s&timezone=auto&forecast_days=1",
                location.latitude,
                location.longitude,
                unitParameter);

        JSONObject root = new JSONObject(readUrl(endpoint));
        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        int code = current.getInt("weather_code");
        int temperature = (int) Math.round(current.getDouble("temperature_2m"));
        int high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        int low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));

        return new WeatherData(
                location.displayName,
                temperature,
                describeWeather(code),
                high,
                low,
                fahrenheit ? "°F" : "°C",
                System.currentTimeMillis());
    }

    private static String readUrl(String endpoint) throws IOException {
        String response = readUrlOnce(endpoint);
        if (!looksLikeJson(response)) {
            throw unexpectedResponse(response);
        }
        return response;
    }

    private static String readUrlOnce(String endpoint) throws IOException {
        OkHttpClient client = httpClient;
        if (client == null) {
            throw new IOException("Secure network client is unavailable");
        }
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "EchelonMirror/0.3")
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("Server returned an empty response");
            }
            return response.body().string();
        }
    }

    private static boolean looksLikeJson(String response) {
        if (response == null) {
            return false;
        }
        String trimmed = response.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static IOException unexpectedResponse(String response) {
        String preview = response == null ? "empty response" : response
                .replaceAll("\\s+", " ")
                .trim();
        if (preview.length() > 120) {
            preview = preview.substring(0, 120) + "…";
        }
        return new IOException("Server returned a web page: " + preview);
    }

    private static final class Ipv4Dns implements Dns {
        @Override
        public List<InetAddress> lookup(String hostname) throws UnknownHostException {
            // These current public addresses bypass broken IPv6 and captive DNS
            // on the Echelon's home Wi-Fi while OkHttp still sends the hostname
            // for SNI and verifies the correct HTTPS certificate.
            if ("geocoding-api.open-meteo.com".equals(hostname)) {
                return Arrays.asList(InetAddress.getByName("202.61.206.6"));
            }
            if ("api.open-meteo.com".equals(hostname)) {
                return Arrays.asList(InetAddress.getByName("188.40.99.226"));
            }

            List<InetAddress> ipv4 = new ArrayList<>();
            for (InetAddress address : InetAddress.getAllByName(hostname)) {
                if (address instanceof Inet4Address) {
                    ipv4.add(address);
                }
            }
            if (ipv4.isEmpty()) {
                throw new UnknownHostException("No IPv4 address for " + hostname);
            }
            return ipv4;
        }
    }

    private static void writeCache(Context context, WeatherData data) {
        preferences(context).edit()
                .putString(KEY_LOCATION, data.location)
                .putInt(KEY_TEMPERATURE, data.temperature)
                .putString(KEY_CONDITION, data.condition)
                .putInt(KEY_HIGH, data.high)
                .putInt(KEY_LOW, data.low)
                .putString(KEY_UNIT, data.unit)
                .putLong(KEY_UPDATED_AT, data.updatedAt)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String describeWeather(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Current conditions";
    }

    private static final class LocationResult {
        final String displayName;
        final double latitude;
        final double longitude;

        LocationResult(String displayName, double latitude, double longitude) {
            this.displayName = displayName;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static final class LocationNotFoundException extends Exception {
    }
}
