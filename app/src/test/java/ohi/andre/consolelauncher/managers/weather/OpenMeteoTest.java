package ohi.andre.consolelauncher.managers.weather;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenMeteoTest {

    private static final String FORECAST = "{\"latitude\":52.52,\"longitude\":13.41,"
            + "\"current\":{\"time\":\"2026-11-03T14:00\",\"temperature_2m\":8.4,\"relative_humidity_2m\":81,"
            + "\"apparent_temperature\":5.9,\"precipitation\":0.2,\"weather_code\":61,\"cloud_cover\":100,"
            + "\"surface_pressure\":1003.6,\"wind_speed_10m\":4.3,\"wind_direction_10m\":245},"
            + "\"daily\":{\"time\":[\"2026-11-03\"],\"temperature_2m_max\":[9.1],\"temperature_2m_min\":[4.2]}}";

    @Test
    public void rewritesTheForecastForTheWeatherFormat() throws Exception {
        JSONObject o = new JSONObject(OpenMeteo.toWeatherJson(FORECAST, "metric", "Berlin"));

        JSONObject weather = o.getJSONArray("weather").getJSONObject(0);
        assertEquals("Rain", weather.getString("main"));
        assertEquals("light rain", weather.getString("description"));

        JSONObject main = o.getJSONObject("main");
        assertEquals(8.4, main.getDouble("temp"), 0.001);
        assertEquals(5.9, main.getDouble("feels_like"), 0.001);
        assertEquals(4.2, main.getDouble("temp_min"), 0.001);
        assertEquals(9.1, main.getDouble("temp_max"), 0.001);
        assertEquals(81, main.getInt("humidity"));
        assertEquals(1004, main.getInt("pressure"));
        assertEquals(4.3, o.getJSONObject("wind").getDouble("speed"), 0.001);
        assertEquals("Berlin", o.getString("name"));
    }

    @Test
    public void theDefaultWeatherFormatFindsItsValues() throws Exception {
//        the launcher looks values up with this pattern; "%main" must find the word, not the object
        String json = OpenMeteo.toWeatherJson(FORECAST, "metric", "");
        assertTrue(json.contains("\"main\":\"Rain\""));
        assertTrue(json.matches("(?s).*\"temp\":8\\.4.*"));
    }

    @Test
    public void convertsToKelvin() throws Exception {
        JSONObject o = new JSONObject(OpenMeteo.toWeatherJson(FORECAST, "standard", ""));
        assertEquals(281.55, o.getJSONObject("main").getDouble("temp"), 0.001);
    }

    @Test
    public void asksForFahrenheitInImperial() {
        String url = OpenMeteo.forecastUrl(29.42, -98.49, "imperial");
        assertTrue(url.contains("temperature_unit=fahrenheit"));
        assertTrue(url.contains("wind_speed_unit=mph"));
        assertTrue(url.contains("latitude=29.4200"));
        assertTrue(OpenMeteo.forecastUrl(1, 2, "metric").contains("temperature_unit=celsius"));
    }

    @Test
    public void readsLocations() {
        assertArrayEquals(new double[] {52.52, 13.41}, OpenMeteo.parseCoordinates("52.52, 13.41"), 0.0001);
        assertNull(OpenMeteo.parseCoordinates("Berlin"));
        assertNull(OpenMeteo.parseCoordinates("San Antonio, Texas"));
        assertNull(OpenMeteo.parseCoordinates("123,456"));
        assertNull(OpenMeteo.parseCoordinates("null"));
    }

    @Test
    public void requestsRoundTrip() {
        String byPlace = OpenMeteo.request("San Antonio, Texas");
        assertTrue(OpenMeteo.isRequest(byPlace));
        assertEquals("San Antonio, Texas", OpenMeteo.placeOf(byPlace));
        assertNull(OpenMeteo.coordinatesOf(byPlace));

        String byCoordinates = OpenMeteo.request(52.52, 13.41);
        assertNull(OpenMeteo.placeOf(byCoordinates));
        assertArrayEquals(new double[] {52.52, 13.41}, OpenMeteo.coordinatesOf(byCoordinates), 0.0001);
        assertTrue(OpenMeteo.geocodeUrl("San Antonio, Texas").endsWith("name=San+Antonio"));
    }

    @Test
    public void picksThePlaceThatMatchesTheHint() throws Exception {
        String geo = "{\"results\":["
                + "{\"name\":\"San Antonio\",\"latitude\":-33.59,\"longitude\":-71.61,\"country\":\"Chile\",\"admin1\":\"Valparaíso\"},"
                + "{\"name\":\"San Antonio\",\"latitude\":29.42,\"longitude\":-98.49,\"country\":\"United States\",\"admin1\":\"Texas\"}]}";

        OpenMeteo.Place texas = OpenMeteo.parsePlace(geo, "San Antonio, Texas");
        assertEquals(29.42, texas.latitude, 0.001);

        OpenMeteo.Place first = OpenMeteo.parsePlace(geo, "San Antonio");
        assertEquals(-33.59, first.latitude, 0.001);

        assertNull(OpenMeteo.parsePlace("{}", "Nowhere"));
    }
}
