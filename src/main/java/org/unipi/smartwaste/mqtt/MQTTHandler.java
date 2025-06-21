package org.unipi.smartwaste.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.unipi.smartwaste.app.DeviceConfigManager;
import org.unipi.smartwaste.app.DeviceConfigManager.Device;
import org.unipi.smartwaste.app.DeviceConfigManager.MqttTopics;
import org.unipi.smartwaste.db.DBDriver;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

public class MQTTHandler implements MqttCallback, Runnable {

    private static final MQTTHandler instance = new MQTTHandler();

    private MqttClient mqttClient;
    private final String broker = "tcp://127.0.0.1:1883";
    private final String clientId = "SmartBin-MQTTHandler";

    private final Set<String> allTopics = new HashSet<>(); // Stores all MQTT topics from config

    private MQTTHandler() {}

    public static MQTTHandler getInstance() {
        return instance;
    }

    @Override
    public void run() {
        try {
            mqttClient = new MqttClient(broker, clientId);
            mqttClient.setCallback(this);
            collectTopicsFromConfig();
            connectAndSubscribe();
        } catch (MqttException e) {
            System.err.println("Could not connect to the MQTT broker: " + e.getMessage());
        }
    }

    /** Gathers all topics from the config file */
    private void collectTopicsFromConfig() {
        List<Device> devices = DeviceConfigManager.getDevices();

        for (Device device : devices) {
            MqttTopics mqtt = device.mqtt_topics;
            if (mqtt != null) {
                Collections.addAll(allTopics,
                    mqtt.temperature,
                    mqtt.humidity,
                    mqtt.distance,
                    mqtt.button,
                    mqtt.full_bin_cmd,
                    mqtt.anomaly_cmd,
                    mqtt.empty_bin_cmd
                );
            }
        }
    }

    /** Connect to broker and subscribe to all required topics */
    private void connectAndSubscribe() throws MqttException {
        mqttClient.connect();
        for (String topic : allTopics) {
            mqttClient.subscribe(topic);
        }
        System.out.println("Connected and subscribed to topics:\n" + allTopics);
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("MQTT connection lost: " + cause.getMessage());
        attemptReconnect();
    }

    private void attemptReconnect() {
        new Thread(() -> {
            while (!mqttClient.isConnected()) {
                try {
                    Thread.sleep(3000);
                    connectAndSubscribe();
                    System.out.println("Reconnected to MQTT broker.");
                } catch (MqttException | InterruptedException e) {
                    System.err.println("Reconnect failed: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        String payloadStr = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
        JSONParser parser = new JSONParser();

        try {
            JSONObject obj = (JSONObject) parser.parse(payloadStr);

            if (topic.contains("button")) {
                String status = (String) obj.get("button");
                if ("pressed".equalsIgnoreCase(status)) {
                    if (DBDriver.insertButtonPress() < 1) {
                        System.err.println("Database error: could not insert button press");
                    }
                } else {
                    System.err.println("Invalid button message: " + payloadStr);
                }
            } else if (topic.contains("temperature") || topic.contains("humidity") || topic.contains("distance")) {
                Object val = obj.get("value");
                if (val instanceof Number) {
                    long value = ((Number) val).longValue();
                    String sensorIp = "";
                    Object ipObj = obj.get("sensor_ip");
                    if (ipObj != null) {
                        sensorIp = ipObj.toString();
                    }
                    if (DBDriver.insertData(value, topic, sensorIp) < 1) {
                        System.err.println("Database error: could not insert data for topic " + topic);
                    }
                } else {
                    System.err.println("Invalid value in topic " + topic + ": " + payloadStr);
                }
            } else {
                System.out.println("Command received on topic " + topic + ": " + payloadStr);
                // You could add actuator command handling here (if needed)
            }

        } catch (ParseException e) {
            System.err.println("Failed to parse MQTT message: " + payloadStr);
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used — only for publishers
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("Disconnected from MQTT broker.");
            }
        } catch (MqttException e) {
            System.err.println("Error during MQTT disconnect: " + e.getMessage());
        }
    }
}
