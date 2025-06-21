package org.unipi.smartwaste.app;

import org.unipi.smartwaste.mqtt.MQTTHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    // first data retrieval completed?
    private static volatile boolean dataReady = false;

    public static void main(String[] args) {
        ScheduledExecutorService executorService = null;

        try {
            // static config of sensors and actuators
            DeviceConfigManager.loadConfig("config/devices_config.json");

            executorService = Executors.newScheduledThreadPool(4);

            executorService.schedule(MQTTHandler.getInstance(), 0, TimeUnit.SECONDS);

            executorService.scheduleAtFixedRate(() -> {
                PeriodicDataRetrieval.getInstance().run();

                dataReady = true;

            }, 30, 10, TimeUnit.SECONDS);

            new Thread(CommandLineInterface.getInstance()).start();

        } catch (Exception e) {
            System.err.println("Error initializing application: " + e.getMessage());
            e.printStackTrace();
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    public static boolean isDataReady() {
        return dataReady;
    }
}
