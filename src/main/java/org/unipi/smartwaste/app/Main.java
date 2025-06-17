package org.unipi.smartwaste.app;

import org.unipi.smartwaste.coap.COAPRegistrationHandler;
import org.unipi.smartwaste.mqtt.MQTTHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        ScheduledExecutorService executorService = null;

        try {
            executorService = Executors.newScheduledThreadPool(4);

            // Start COAP registration and MQTT handler immediately
            executorService.schedule(COAPRegistrationHandler.getInstance(), 0, TimeUnit.SECONDS);
            executorService.schedule(MQTTHandler.getInstance(), 0, TimeUnit.SECONDS);

            // Start periodic data retrieval every 10 seconds, after initial 30 sec delay
            executorService.scheduleAtFixedRate(PeriodicDataRetrieval.getInstance(), 30, 10, TimeUnit.SECONDS);

            // Start CLI in a separate thread so it does not block scheduled executor threads
            new Thread(CommandLineInterface.getInstance()).start();

        } catch (Exception e) {
            System.err.println("Error starting scheduled tasks: " + e.getMessage());
            e.printStackTrace();
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }
}
