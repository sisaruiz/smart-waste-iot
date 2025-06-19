package org.unipi.smartwaste.mqtt;

import org.unipi.smartwaste.db.DBDriver;

import org.eclipse.paho.client.mqttv3.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class MQTTHandler implements MqttCallback, Runnable {

    private static final MQTTHandler instance = new MQTTHandler();

    private MqttClient mqttClient;
    private final String broker = "tcp://127.0.0.1:1883";
    private final String clientId = "SmartBin-MQTTHandler";

    private final List<String> topics = Arrays.asList(
            "temperature",
            "humidity",
            "distance",
            "button"
    );

    private MQTTHandler() {
    }

    public static MQTTHandler getInstance() {
        return instance;
    }

    @Override
    public void run() {
        try {
            mqttClient = new MqttClient(broker, clientId);
            mqttClient.setCallback(this);
            connectAndSubscribe();
        } catch (MqttException e) {
            System.err.println("Could not connect to the MQTT broker: " + e.getMessage());
        }
    }

    private void connectAndSubscribe() throws MqttException {
        mqttClient.connect();
        for (String t : topics) {
            mqttClient.subscribe(t);
        }
        System.out.println("Connected and subscribed to topics: " + topics);
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

            if (topic.equals("button")) {
                String status = (String) obj.get("button");
                if (status != null && status.equals("pressed")) {
                    if (DBDriver.insertButtonPress() < 1) {
                        System.err.println("Database error: could not insert button press");
                    }
                } else {
                    System.err.println("Invalid button message content: " + payloadStr);
                }
            } else {
                Object val = obj.get("value");
                if (val instanceof Number) {
                    long value = ((Number) val).longValue();

                    // Extract sensor_ip from JSON if present, else empty string
                    String sensorIp = "";
                    Object ipObj = obj.get("sensor_ip");
                    if (ipObj != null) {
                        sensorIp = ipObj.toString();
                    }

                    if (DBDriver.insertData(value, topic, sensorIp) < 1) {
                        System.err.println("Database error: could not insert data for " + topic);
                    }
                } else {
                    System.err.println("Invalid 'value' in message for topic " + topic + ": " + payloadStr);
                }
            }

        } catch (ParseException e) {
            System.err.println("Failed to parse MQTT message payload: " + payloadStr);
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not needed for subscriber-only handler
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
