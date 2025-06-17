package org.unipi.smartwaste.app;

import org.unipi.smartwaste.coap.COAPClient;
import org.unipi.smartwaste.db.DBDriver;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PeriodicDataRetrieval implements Runnable {

    // Thresholds for bins (adjust as needed)
    private static final Map<String, Integer> thresholdsMin = new HashMap<>();
    private static final Map<String, Integer> thresholdsMax = new HashMap<>();
    // To track actuator states per bin and sensor type
    private static final Map<String, Map<String, Boolean>> checkOffs = new HashMap<>();

    private static final PeriodicDataRetrieval instance = new PeriodicDataRetrieval();

    static {
        // Initialize thresholds, tweak values as needed
        thresholdsMin.put("fill_level", 0);       // %
        thresholdsMax.put("fill_level", 80);      // activate actuator if >80%
        thresholdsMin.put("temperature", -10);    // °C
        thresholdsMax.put("temperature", 50);     // °C
        thresholdsMin.put("humidity", 0);          // %
        thresholdsMax.put("humidity", 90);         // %

        // checkOffs tracks per binId and sensor key if actuator is ON
    }

    private PeriodicDataRetrieval() {}

    public static PeriodicDataRetrieval getInstance() {
        return instance;
    }

    @Override
    public void run() {
        try {
            List<HashMap<String, Object>> bins = DBDriver.retrieveAllBinsStatus();

            if (bins == null || bins.isEmpty()) {
                System.out.println("No bins data retrieved.");
                return;
            }

            for (HashMap<String, Object> bin : bins) {
                String binId = (String) bin.get("id");
                String ip = (String) bin.get("ip");

                checkOffs.putIfAbsent(binId, new HashMap<>());

                checkSensor(binId, ip, "fill_level", (Integer) bin.get("fill_level"));
                checkSensor(binId, ip, "temperature", (Integer) bin.get("temperature"));
                checkSensor(binId, ip, "humidity", (Integer) bin.get("humidity"));
            }

        } catch (SQLException e) {
            System.err.println("Database error in PeriodicDataRetrieval: " + e.getMessage());
        }
    }

    private void checkSensor(String binId, String ip, String key, Integer value) throws SQLException {
        if (value == null) return;

        Map<String, Boolean> binCheckMap = checkOffs.get(binId);

        HashMap<String, Object> actuator = DBDriver.retrieveActuator(key);
        if (actuator == null || actuator.isEmpty()) return;

        boolean actuatorActive = (boolean) actuator.getOrDefault("active", false);

        int minThreshold = thresholdsMin.getOrDefault(key, Integer.MIN_VALUE);
        int maxThreshold = thresholdsMax.getOrDefault(key, Integer.MAX_VALUE);

        if (value < minThreshold || value > maxThreshold) {
            COAPClient.setSensors(key, true);

            if (!actuatorActive) {
                binCheckMap.put(key, true);
                System.err.println("Danger detected on bin " + binId + " " + key + " sensor, activating actuator");
                COAPClient.actuatorCall(ip, key, true, 1);
            }
        } else {
            COAPClient.setSensors(key, false);

            if (actuatorActive && Boolean.TRUE.equals(binCheckMap.get(key))) {
                binCheckMap.put(key, false);
                System.err.println("Turning off actuator for bin " + binId + " " + key + " sensor - no danger");
                COAPClient.actuatorCall(ip, key, false, 0);
            }
        }
    }

    // Setter methods for thresholds
    public static boolean setMinThreshold(String key, Integer val) {
        Integer max = thresholdsMax.get(key);
        if (max != null && val >= max) {
            System.out.println("Min threshold cannot be greater or equal than max threshold");
            return false;
        }
        thresholdsMin.put(key, val);
        return true;
    }

    public static boolean setMaxThreshold(String key, Integer val) {
        Integer min = thresholdsMin.get(key);
        if (min != null && val <= min) {
            System.out.println("Max threshold cannot be less or equal than min threshold");
            return false;
        }
        thresholdsMax.put(key, val);
        return true;
    }

    public static boolean setMinTemperatureThreshold(Integer val) {
        return setMinThreshold("temperature", val);
    }

    public static boolean setMaxTemperatureThreshold(Integer val) {
        return setMaxThreshold("temperature", val);
    }

    public static boolean setMinHumidityThreshold(Integer val) {
        return setMinThreshold("humidity", val);
    }

    public static boolean setMaxHumidityThreshold(Integer val) {
        return setMaxThreshold("humidity", val);
    }

    public static boolean setMinFillLevelThreshold(Integer val) {
        return setMinThreshold("fill_level", val);
    }

    public static boolean setMaxFillLevelThreshold(Integer val) {
        return setMaxThreshold("fill_level", val);
    }
}
