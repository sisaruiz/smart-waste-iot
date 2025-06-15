package org.unipi.smartwaste.coap;

import org.unipi.smartwaste.db.DBDriver;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

public class COAPClient {

    private static HashMap<String, Boolean> sensors = new HashMap<>();

    static {
        sensors.put("temperature", false);
        sensors.put("humidity", false);
        sensors.put("distance", false);
        sensors.put("button", false);
        sensors.put("anomaly", false);
        // Add more sensors if needed
    }

    /**
     * Sends a PUT request to actuator resource with optional threshold and action.
     * @param ip The IPv6 address of the sensor/actuator node
     * @param resource The resource name (e.g., "temperature", "distance", "button")
     * @param action Boolean flag indicating action (true = ON/activate, false = OFF/deactivate)
     * @param overThreshold Optional threshold value (nullable)
     * @throws SQLException If DB update fails
     */
    public static void actuatorCall(String ip, String resource, Boolean action, Integer overThreshold) throws SQLException {
        CoapClient client = new CoapClient("coap://[" + ip + "]/" + resource);
        JSONObject object = new JSONObject();

        if (overThreshold != null) {
            object.put("threshold", overThreshold);
        }
        object.put("action", action);

        String payload = object.toJSONString();

        try {
            CoapResponse response = client.put(payload, MediaTypeRegistry.APPLICATION_JSON);

            if (response == null) {
                System.err.println("[COAPClient] No response from actuator at [" + ip + "]/" + resource);
                return;
            }

            CoAP.ResponseCode code = response.getCode();
            switch (code) {
                case CONTENT:
                case CHANGED:
                    System.out.println("[COAPClient] Actuator " + resource + " state updated on " + ip);
                    DBDriver.updateActuatorStatus("/" + ip, resource, action);
                    break;
                case BAD_OPTION:
                    System.err.println("[COAPClient] Bad parameters sent to actuator: " + resource);
                    break;
                default:
                    System.err.println("[COAPClient] Unexpected response code: " + code);
                    break;
            }
        } catch (ConnectorException | IOException e) {
            System.err.println("[COAPClient] Exception while sending PUT: " + e.getMessage());
        }
    }

    /**
     * Convenience method to trigger a button actuator (no threshold)
     */
    public static void triggerButton(String ip) throws SQLException {
        actuatorCall(ip, "button", true, null);
    }

    /**
     * Convenience method to set temperature threshold actuator
     */
    public static void setTemperatureThreshold(String ip, int threshold) throws SQLException {
        actuatorCall(ip, "temperature", true, threshold);
    }

    /**
     * Convenience method to set distance threshold actuator (bin fullness)
     */
    public static void setDistanceThreshold(String ip, int threshold) throws SQLException {
        actuatorCall(ip, "distance", true, threshold);
    }

    /**
     * Fetches the current sensor data using GET request.
     * @param ip IPv6 address of sensor node
     * @param resource Sensor resource name
     * @return JSON string response or null on failure
     */
    public static String getSensorData(String ip, String resource) {
        CoapClient client = new CoapClient("coap://[" + ip + "]/" + resource);

        try {
            CoapResponse response = client.get();
            if (response != null && response.isSuccess()) {
                return response.getResponseText();
            } else {
                System.err.println("[COAPClient] Failed to get data from " + resource + " at " + ip);
                return null;
            }
        } catch (ConnectorException | IOException e) {
            System.err.println("[COAPClient] Exception while sending GET: " + e.getMessage());
            return null;
        }
    }

    /**
     * Updates the internal sensor flags when danger or alert is detected or cleared
     * @param sensorName sensor key ("temperature", "humidity", etc)
     * @param val true if alert/danger active, false otherwise
     */
    public static void setSensors(String sensorName, boolean val) {
        if (sensors.containsKey(sensorName)) {
            sensors.put(sensorName, val);
        } else {
            System.err.println("[COAPClient] Unknown sensor: " + sensorName);
        }
    }

    /**
     * Get current sensor alert status map
     * @return sensor map (sensorName -> boolean alert)
     */
    public static HashMap<String, Boolean> getSensors() {
        return sensors;
    }
}
