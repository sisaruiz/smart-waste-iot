package org.unipi.smartwaste.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.unipi.smartwaste.configuration.Sensor;
import org.unipi.smartwaste.db.DBDriver;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MQTTHandler implements MqttCallback {

    private final MqttClient client;
    private final DBDriver db;

    // Maps topic to sensor type (e.g., "sensors/temp" → "temperature")
    private final Map<String, String> topicToSensorType = new ConcurrentHashMap<>();

    // Latest values received from each sensor type (e.g., "temperature" → "52.1")
    private final Map<String, String> latestSensorValues = new ConcurrentHashMap<>();

    public MQTTHandler(List<Sensor> sensors, DBDriver db) {
        this.db = db;

        String brokerUrl = "tcp://localhost:1883"; // Replace with your actual broker URL
        String clientId = MqttClient.generateClientId();

        try {
            client = new MqttClient(brokerUrl, clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            client.setCallback(this);
            client.connect(options);

            System.out.println("[MQTTHandler] Connected to MQTT broker: " + brokerUrl);

            // Subscribe to all sensor topics
            for (Sensor sensor : sensors) {
                String topic = sensor.getTopic();
                topicToSensorType.put(topic, sensor.getType());
                client.subscribe(topic);
                System.out.println("[MQTTHandler] Subscribed to topic: " + topic + " as " + sensor.getType());
            }

        } catch (MqttException e) {
            throw new RuntimeException("[MQTTHandler] Failed to connect or subscribe to MQTT broker", e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[MQTTHandler] Connection lost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        System.out.println("[MQTTHandler] Message arrived - Topic: " + topic + ", Payload: " + payload);

        String sensorType = topicToSensorType.get(topic);

        if (sensorType != null) {
            latestSensorValues.put(sensorType, payload); // store in-memory for control logic
            db.insertSensorReading(sensorType, payload); // persist in database
        } else {
            System.out.println("[MQTTHandler] Received message on unknown topic: " + topic);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not used since this class doesn't publish messages
    }

    public String getLatestSensorValue(String sensorType) {
        return latestSensorValues.get(sensorType);
    }

    public void printSensorStatus() {
        System.out.println("[MQTTHandler] Latest sensor values:");
        for (Map.Entry<String, String> entry : latestSensorValues.entrySet()) {
            System.out.println("- " + entry.getKey() + ": " + entry.getValue());
        }
    }

    public void close() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                System.out.println("[MQTTHandler] Disconnected from MQTT broker");
            }
        } catch (MqttException e) {
            System.err.println("[MQTTHandler] Error closing MQTT connection: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
