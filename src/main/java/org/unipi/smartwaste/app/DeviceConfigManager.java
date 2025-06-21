package org.unipi.smartwaste.app;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;

public class DeviceConfigManager {

    private static Device device = null;

    public static void loadConfig(String path) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject deviceJson = (JSONObject) parser.parse(new FileReader(path));

        device = new Device();
        device.ipv6 = (String) deviceJson.get("ipv6");

        JSONObject coapResourcesJson = (JSONObject) deviceJson.get("coap_resources");
        if (coapResourcesJson != null) {
            device.coap_resources = new CoapResources();
            device.coap_resources.temperature = (String) coapResourcesJson.get("temperature");
            device.coap_resources.humidity = (String) coapResourcesJson.get("humidity");
            device.coap_resources.distance = (String) coapResourcesJson.get("distance");
            device.coap_resources.button = (String) coapResourcesJson.get("button");
            device.coap_resources.full_bin = (String) coapResourcesJson.get("full_bin");          
            device.coap_resources.anomaly_fire = (String) coapResourcesJson.get("anomaly_fire");    
            device.coap_resources.anomaly_leakage = (String) coapResourcesJson.get("anomaly_leakage");
        }

        JSONObject mqttTopicsJson = (JSONObject) deviceJson.get("mqtt_topics");
        if (mqttTopicsJson != null) {
            device.mqtt_topics = new MqttTopics();
            device.mqtt_topics.temperature = (String) mqttTopicsJson.get("temperature");
            device.mqtt_topics.humidity = (String) mqttTopicsJson.get("humidity");
            device.mqtt_topics.distance = (String) mqttTopicsJson.get("distance");
            device.mqtt_topics.button = (String) mqttTopicsJson.get("button");
        }

        System.out.println("Loaded device config with IPv6: '" + device.ipv6 + "'");
    }

    public static Device getDevice() {
        return device;
    }

    public static class Device {
        public String ipv6;
        public CoapResources coap_resources;
        public MqttTopics mqtt_topics;
    }

    public static class CoapResources {
        public String temperature;
        public String humidity;
        public String distance;
        public String button;

        // Actuator CoAP resource paths
        public String full_bin;
        public String anomaly_fire;
        public String anomaly_leakage;
    }

    public static class MqttTopics {
        public String temperature;
        public String humidity;
        public String distance;
        public String button;
    }
}
