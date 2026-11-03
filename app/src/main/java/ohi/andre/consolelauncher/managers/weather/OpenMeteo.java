package ohi.andre.consolelauncher.managers.weather;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Weather from Open-Meteo (https://open-meteo.com), which is free and needs no API key.
 *
 * The weather line in HelCLI is configured with placeholders such as %main and %temp, which were
 * originally names in OpenWeatherMap's answers. To keep everyone's existing weather_format working,
 * Open-Meteo's answer is rewritten into the same shape: the same placeholders keep working.
 *
 * Supported placeholders: %main, %description, %temp, %feels_like, %temp_min, %temp_max,
 * %humidity, %pressure, %speed, %deg, %all (cloud cover %), %precipitation, %name (place), %id,
 * and (new in HelCLI) %sunrise, %sunset, %daylight, %rain_chance, %uv, %wind_dir, %unit, %wind_unit.
 *
 * Plain Java (only org.json), so it can be unit tested.
 */
public class OpenMeteo {

    /** Marks a weather request that goes to Open-Meteo instead of a plain URL. */
    public static final String SCHEME = "openmeteo:";

    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String GEOCODING = "https://geocoding-api.open-meteo.com/v1/search";

    private OpenMeteo() {}

    /** A request for fixed coordinates. */
    public static String request(double latitude, double longitude) {
        return SCHEME + format(latitude, 4) + "," + format(longitude, 4);
    }

    /** A request for a place name (for example "Berlin" or "San Antonio, Texas"). */
    public static String request(String placeName) {
        return SCHEME + "q=" + placeName.trim();
    }

    public static boolean isRequest(String s) {
        return s != null && s.startsWith(SCHEME);
    }

    /** The place name of a request, or null if the request is for coordinates. */
    public static String placeOf(String request) {
        String body = request.substring(SCHEME.length());
        return body.startsWith("q=") ? body.substring(2) : null;
    }

    /** The coordinates of a request, or null if it's for a place name. */
    public static double[] coordinatesOf(String request) {
        String body = request.substring(SCHEME.length());
        if (body.startsWith("q=")) return null;
        String[] parts = body.split(",");
        if (parts.length != 2) return null;
        try {
            return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses "lat,lon" coordinates as typed in weather_location, or returns null. */
    public static double[] parseCoordinates(String s) {
        if (s == null || !s.contains(",")) return null;
        String[] parts = s.split(",");
        if (parts.length != 2) return null;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new double[] {lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String forecastUrl(double latitude, double longitude, String units) {
        boolean imperial = "imperial".equalsIgnoreCase(units);
        return FORECAST + "?latitude=" + format(latitude, 4) + "&longitude=" + format(longitude, 4)
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,"
                + "cloud_cover,surface_pressure,wind_speed_10m,wind_direction_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_probability_max"
                + "&forecast_days=1&timezone=auto"
                + "&temperature_unit=" + (imperial ? "fahrenheit" : "celsius")
                + "&wind_speed_unit=" + (imperial ? "mph" : "ms");
    }

    /**
     * Open-Meteo's geocoder matches a single place name, so for "San Antonio, Texas" it's asked
     * about "San Antonio" and the rest of the text is used to pick the right match.
     */
    public static String geocodeUrl(String placeName) {
        String name = placeName.split(",")[0].trim();
        try {
            return GEOCODING + "?count=10&format=json&name=" + URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A geocoding result: coordinates and a display name. */
    public static class Place {
        public final double latitude, longitude;
        public final String name;

        public Place(double latitude, double longitude, String name) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.name = name;
        }
    }

    /** Picks the best match from a geocoding answer, or returns null if nothing matched. */
    public static Place parsePlace(String geocodingJson, String placeName) throws JSONException {
        JSONArray results = new JSONObject(geocodingJson).optJSONArray("results");
        if (results == null || results.length() == 0) return null;

        String[] parts = placeName.split(",");
        String hint = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : null;

        JSONObject best = results.getJSONObject(0);
        if (hint != null && hint.length() > 0) {
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String where = (r.optString("admin1", "") + " " + r.optString("country", "") + " "
                        + r.optString("country_code", "")).toLowerCase(Locale.ROOT);
                if (where.contains(hint)) {
                    best = r;
                    break;
                }
            }
        }

        return new Place(best.getDouble("latitude"), best.getDouble("longitude"), best.optString("name", parts[0].trim()));
    }

    /**
     * Rewrites an Open-Meteo forecast into the shape the weather_format placeholders expect.
     *
     * @param units "metric" (Celsius), "imperial" (Fahrenheit) or "standard" (Kelvin)
     */
    public static String toWeatherJson(String forecastJson, String units, String placeName) throws JSONException {
        return toWeatherJson(forecastJson, units, placeName, true);
    }

    /**
     * @param use24h sunrise and sunset as 18:12 (true) or 6:12 PM (false), like the phone's clock
     */
    public static String toWeatherJson(String forecastJson, String units, String placeName, boolean use24h) throws JSONException {
        JSONObject forecast = new JSONObject(forecastJson);
        JSONObject current = forecast.getJSONObject("current");
        JSONObject daily = forecast.optJSONObject("daily");

        boolean kelvin = "standard".equalsIgnoreCase(units);
        int code = current.optInt("weather_code", -1);
        String[] words = describe(code);

        JSONObject weather = new JSONObject();
        weather.put("id", code);
        weather.put("main", words[0]);
        weather.put("description", words[1]);

        JSONObject main = new JSONObject();
        main.put("temp", temperature(current.optDouble("temperature_2m"), kelvin));
        main.put("feels_like", temperature(current.optDouble("apparent_temperature"), kelvin));
        if (daily != null) {
            main.put("temp_min", temperature(first(daily, "temperature_2m_min"), kelvin));
            main.put("temp_max", temperature(first(daily, "temperature_2m_max"), kelvin));
        }
        main.put("pressure", Math.round(current.optDouble("surface_pressure", 0)));
        main.put("humidity", Math.round(current.optDouble("relative_humidity_2m", 0)));

        JSONObject wind = new JSONObject();
        wind.put("speed", round(current.optDouble("wind_speed_10m", 0), 1));
        wind.put("deg", Math.round(current.optDouble("wind_direction_10m", 0)));

        JSONObject clouds = new JSONObject();
        clouds.put("all", Math.round(current.optDouble("cloud_cover", 0)));

        JSONObject out = new JSONObject();
        out.put("weather", new JSONArray().put(weather));
        out.put("main", main);
        out.put("wind", wind);
        out.put("clouds", clouds);
        out.put("precipitation", round(current.optDouble("precipitation", 0), 1));
        out.put("name", placeName == null ? "" : placeName);

//        extra codes: %sunrise %sunset %daylight %rain_chance %uv %wind_dir %unit %wind_unit
        boolean imperial = "imperial".equalsIgnoreCase(units);
        out.put("unit", kelvin ? "K" : imperial ? "°F" : "°C");
        out.put("wind_unit", imperial ? "mph" : "m/s");
        out.put("wind_dir", compass(current.optDouble("wind_direction_10m", Double.NaN)));
        if (daily != null) {
            String sunrise = clock(firstString(daily, "sunrise"), use24h);
            String sunset = clock(firstString(daily, "sunset"), use24h);
            if (sunrise != null) out.put("sunrise", sunrise);
            if (sunset != null) out.put("sunset", sunset);
            double daylight = first(daily, "daylight_duration");
            if (!Double.isNaN(daylight)) out.put("daylight", (long) daylight / 3600 + "h " + ((long) daylight % 3600) / 60 + "m");
            double rain = first(daily, "precipitation_probability_max");
            if (!Double.isNaN(rain)) out.put("rain_chance", Math.round(rain));
            double uv = first(daily, "uv_index_max");
            if (!Double.isNaN(uv)) out.put("uv", round(uv, 1));
        }
        return out.toString();
    }

    /** A short word ("Rain") and a description ("light rain") for a WMO weather code. */
    public static String[] describe(int code) {
        switch (code) {
            case 0: return new String[] {"Clear", "clear sky"};
            case 1: return new String[] {"Clear", "mainly clear"};
            case 2: return new String[] {"Clouds", "partly cloudy"};
            case 3: return new String[] {"Clouds", "overcast"};
            case 45: return new String[] {"Fog", "fog"};
            case 48: return new String[] {"Fog", "freezing fog"};
            case 51: return new String[] {"Drizzle", "light drizzle"};
            case 53: return new String[] {"Drizzle", "drizzle"};
            case 55: return new String[] {"Drizzle", "heavy drizzle"};
            case 56: return new String[] {"Drizzle", "light freezing drizzle"};
            case 57: return new String[] {"Drizzle", "freezing drizzle"};
            case 61: return new String[] {"Rain", "light rain"};
            case 63: return new String[] {"Rain", "rain"};
            case 65: return new String[] {"Rain", "heavy rain"};
            case 66: return new String[] {"Rain", "light freezing rain"};
            case 67: return new String[] {"Rain", "freezing rain"};
            case 71: return new String[] {"Snow", "light snow"};
            case 73: return new String[] {"Snow", "snow"};
            case 75: return new String[] {"Snow", "heavy snow"};
            case 77: return new String[] {"Snow", "snow grains"};
            case 80: return new String[] {"Rain", "light showers"};
            case 81: return new String[] {"Rain", "showers"};
            case 82: return new String[] {"Rain", "violent showers"};
            case 85: return new String[] {"Snow", "light snow showers"};
            case 86: return new String[] {"Snow", "snow showers"};
            case 95: return new String[] {"Thunderstorm", "thunderstorm"};
            case 96: return new String[] {"Thunderstorm", "thunderstorm with hail"};
            case 99: return new String[] {"Thunderstorm", "thunderstorm with heavy hail"};
            default: return new String[] {"Unknown", "unknown"};
        }
    }

    private static String firstString(JSONObject daily, String key) {
        JSONArray a = daily.optJSONArray(key);
        return a == null || a.length() == 0 ? null : a.optString(0, null);
    }

    /** "2026-11-03T18:12" (local time) to "18:12" or "6:12 PM". */
    static String clock(String isoLocal, boolean use24h) {
        if (isoLocal == null) return null;
        int t = isoLocal.indexOf('T');
        if (t < 0 || isoLocal.length() < t + 6) return null;
        try {
            int h = Integer.parseInt(isoLocal.substring(t + 1, t + 3));
            String m = isoLocal.substring(t + 4, t + 6);
            if (use24h) return String.format(Locale.US, "%02d:%s", h, m);
            return (h % 12 == 0 ? 12 : h % 12) + ":" + m + (h < 12 ? " AM" : " PM");
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 0-360 degrees (the direction the wind comes from) to N, NE, E, ... */
    static String compass(double degrees) {
        if (Double.isNaN(degrees)) return "-";
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(((degrees % 360) + 360) % 360 / 45) % 8];
    }

    private static double first(JSONObject daily, String key) {
        JSONArray a = daily.optJSONArray(key);
        return a == null || a.length() == 0 ? Double.NaN : a.optDouble(0);
    }

    private static double temperature(double celsiusOrFahrenheit, boolean kelvin) {
        if (Double.isNaN(celsiusOrFahrenheit)) return 0;
        return kelvin ? round(celsiusOrFahrenheit + 273.15, 2) : round(celsiusOrFahrenheit, 1);
    }

    private static double round(double d, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(d * f) / f;
    }

    private static String format(double d, int decimals) {
        return String.format(Locale.US, "%." + decimals + "f", d);
    }
}
