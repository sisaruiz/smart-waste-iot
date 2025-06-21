package org.unipi.smartwaste.coap;

import org.unipi.smartwaste.db.DBDriver;
import org.unipi.smartwaste.app.DeviceConfigManager;

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
     * IP is obtained internally from DeviceConfigManager
     * @param resource The resource name (e.g., "temperature", "full_bin", "anomaly_fire")
     * @param action Boolean flag indicating action (true = ON/activate, false = OFF/deactivate)
     * @param overThreshold Optional threshold value (nullable)
     * @throws SQLException If DB update fails
     */
    public static void actuatorCall(String resource, Boolean action, Integer overThreshold) throws SQLException {
        try {
            DeviceConfigManager.Device device = DeviceConfigManager.getDevice();
            String ip = device.ipv6;
            String coapUri = "coap://[" + ip + "]/" + resource;
            CoapClient client = new CoapClient(coapUri);
            JSONObject object = new JSONObject();

            if (overThreshold != null) {
                object.put("threshold", overThreshold);
            }
            object.put("action", action);

            String payload = object.toJSONString();

            CoapResponse response = client.put(payload, MediaTypeRegistry.APPLICATION_JSON);

            if (response == null) {
                System.err.println("[COAPClient] No response from actuator at " + coapUri);
                return;
            }

            CoAP.ResponseCode code = response.getCode();
            switch (code) {
                case CONTENT:
                case CHANGED:
                    System.out.println("[COAPClient] Actuator '" + resource + "' state updated on " + ip);
                    // Update DB with IP and actuator resource
                    DBDriver.updateActuators(ip, resource, action);
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
    public static void triggerButton() throws SQLException {
        actuatorCall("button", true, null);
    }

    /**
     * Convenience method to set temperature threshold actuator
     */
    public static void setTemperatureThreshold(int threshold) throws SQLException {
        actuatorCall("temperature", true, threshold);
    }

    /**
     * Convenience method to set distance threshold actuator (bin fullness)
     */
    public static void setDistanceThreshold(int threshold) throws SQLException {
        actuatorCall("distance", true, threshold);
    }

    /**
     * Fetches the current sensor data using GET request.
     * @param resource Sensor resource name
     * @return JSON string response or null on failure
     */
    public static String getSensorData(String resource) {
        try {
            DeviceConfigManager.Device device = DeviceConfigManager.getDevice();
            String ip = device.ipv6;
            String coapUri = "coap://[" + ip + "]/" + resource;
            CoapClient client = new CoapClient(coapUri);

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
