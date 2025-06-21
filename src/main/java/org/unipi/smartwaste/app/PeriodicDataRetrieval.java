package org.unipi.smartwaste.app;

import org.unipi.smartwaste.coap.COAPClient;
import org.unipi.smartwaste.db.DBDriver;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class PeriodicDataRetrieval implements Runnable {

    // Thresholds per sensor key (only max for fill_level)
    private static final Map<String, Integer> thresholdsMin = new HashMap<>();
    private static final Map<String, Integer> thresholdsMax = new HashMap<>();

    // Tracks whether actuator is currently ON for each sensor key
    private static final Map<String, Boolean> actuatorState = new HashMap<>();

    // Singleton instance
    private static final PeriodicDataRetrieval instance = new PeriodicDataRetrieval();

    // Static initialization block for thresholds and actuator state
    static {
        // fill_level only has a max threshold
        thresholdsMax.put("fill_level", 80);

        thresholdsMin.put("temperature", -10);
        thresholdsMax.put("temperature", 50);

        thresholdsMin.put("humidity", 0);
        thresholdsMax.put("humidity", 90);

        actuatorState.put("fill_level", false);
        actuatorState.put("temperature", false);
        actuatorState.put("humidity", false);
    }

    // Private constructor for singleton pattern
    private PeriodicDataRetrieval() {}

    // Getter for singleton instance
    public static PeriodicDataRetrieval getInstance() {
        return instance;
    }

    @Override
    public void run() {
        try {
            DeviceConfigManager.Device device = DeviceConfigManager.getDevice();

            // get latest sensor values from DB
            Integer fillLevelValue = DBDriver.getLatestSensorValue("fill_level");
            Integer tempValue = DBDriver.getLatestSensorValue("temperature");
            Integer humidityValue = DBDriver.getLatestSensorValue("humidity");

            // call check methods passing only value and resource string
            checkFillLevel(fillLevelValue, device.coap_resources.full_bin);
            checkSensor("temperature", tempValue, device.coap_resources.anomaly_fire);
            checkSensor("humidity", humidityValue, device.coap_resources.anomaly_leakage);

        } catch (SQLException e) {
            System.err.println("Database error in PeriodicDataRetrieval: " + e.getMessage());
        }
    }

    private void checkFillLevel(Integer value, String resource) throws SQLException {
        if (value == null) {
            System.out.println("No data for fill_level");
            return;
        }
        int max = thresholdsMax.get("fill_level");
        boolean overThreshold = value > max;
        boolean currentlyOn = actuatorState.get("fill_level");

        if (overThreshold && !currentlyOn) {
            System.err.printf("fill_level = %d > %d, activating %s%n", value, max, resource);
            COAPClient.actuatorCall(resource, true, 1);
            actuatorState.put("fill_level", true);
        } else if (!overThreshold && currentlyOn) {
            System.out.printf("fill_level = %d <= %d, deactivating %s%n", value, max, resource);
            COAPClient.actuatorCall(resource, false, 0);
            actuatorState.put("fill_level", false);
        }
    }

    private void checkSensor(String key, Integer value, String resource) throws SQLException {
        if (value == null) {
            System.out.println("No data for " + key);
            return;
        }

        int min = thresholdsMin.getOrDefault(key, Integer.MIN_VALUE);
        int max = thresholdsMax.getOrDefault(key, Integer.MAX_VALUE);

        boolean overThreshold = (value < min || value > max);
        boolean currentlyOn = actuatorState.get(key);

        if (overThreshold && !currentlyOn) {
            System.err.printf("%s = %d outside [%d,%d], activating %s%n", key, value, min, max, resource);
            COAPClient.actuatorCall(resource, true, 1);
            actuatorState.put(key, true);
        } else if (!overThreshold && currentlyOn) {
            System.out.printf("%s = %d back in [%d,%d], deactivating %s%n", key, value, min, max, resource);
            COAPClient.actuatorCall(resource, false, 0);
            actuatorState.put(key, false);
        }
    }

    // Public setter for max fill level threshold
    public static boolean setMaxFillLevelThreshold(int val) {
        int min = 0; // implicit floor
        if (val <= min) {
            System.out.println("Max threshold must be > 0");
            return false;
        }
        thresholdsMax.put("fill_level", val);
        return true;
    }

    // Other threshold setters
    public static boolean setMinTemperatureThreshold(int val) {
        return setThreshold("temperature", val, true);
    }
    public static boolean setMaxTemperatureThreshold(int val) {
        return setThreshold("temperature", val, false);
    }
    public static boolean setMinHumidityThreshold(int val) {
        return setThreshold("humidity", val, true);
    }
    public static boolean setMaxHumidityThreshold(int val) {
        return setThreshold("humidity", val, false);
    }

    // Helper method to set min or max thresholds with validation
    private static boolean setThreshold(String key, int value, boolean isMin) {
        int other = isMin
            ? thresholdsMax.getOrDefault(key, Integer.MAX_VALUE)
            : thresholdsMin.getOrDefault(key, Integer.MIN_VALUE);

        if ((isMin && value >= other) || (!isMin && value <= other)) {
            System.out.println("Invalid threshold: " + (isMin ? "min" : "max") +
                " must be " + (isMin ? "<" : ">") + other);
            return false;
        }

        if (isMin) thresholdsMin.put(key, value);
        else thresholdsMax.put(key, value);

        return true;
    }
}
