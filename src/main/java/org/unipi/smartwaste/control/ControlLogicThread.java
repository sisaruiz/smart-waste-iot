package org.unipi.smartwaste.control;

import org.unipi.smartwaste.coap.COAPNetworkController;
import org.unipi.smartwaste.mqtt.MQTTHandler;

public class ControlLogicThread extends Thread {

    private final MQTTHandler mqttHandler;
    private final COAPNetworkController coapController;

    private volatile boolean running = true;

    public ControlLogicThread(MQTTHandler mqttHandler, COAPNetworkController coapController) {
        this.mqttHandler = mqttHandler;
        this.coapController = coapController;
    }

    @Override
    public void run() {
        System.out.println("[ControlLogic] Started");

        while (running) {
            try {
                // Fetch latest sensor values
                String tempStr = mqttHandler.getLatestSensorValue("temperature");
                String humidityStr = mqttHandler.getLatestSensorValue("humidity");
                String fillStr = mqttHandler.getLatestSensorValue("fill_level");

                // Apply threshold logic
                if (tempStr != null) {
                    double temperature = Double.parseDouble(tempStr);
                    if (temperature > 50) {
                        System.out.println("[ControlLogic] High temperature detected (" + temperature + "°C). Triggering fire actuator.");
                        coapController.triggerActuator("fire");
                    }
                }

                if (humidityStr != null) {
                    double humidity = Double.parseDouble(humidityStr);
                    if (humidity > 90) {
                        System.out.println("[ControlLogic] High humidity detected (" + humidity + "%). Triggering leak actuator.");
                        coapController.triggerActuator("leak");
                    }
                }

                if (fillStr != null) {
                    int fill = Integer.parseInt(fillStr);
                    if (fill > 90) {
                        System.out.println("[ControlLogic] Bin is almost full (" + fill + "%). Triggering full actuator.");
                        coapController.triggerActuator("full");
                    }
                }

                Thread.sleep(5000); // Check interval

            } catch (Exception e) {
                System.err.println("[ControlLogic] Error during logic evaluation: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("[ControlLogic] Stopped");
    }

    public void stopThread() {
        running = false;
    }
}
