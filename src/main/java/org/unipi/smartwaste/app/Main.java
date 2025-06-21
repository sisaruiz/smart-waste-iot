package org.unipi.smartwaste.app;

import org.unipi.smartwaste.configuration.Configuration;
import org.unipi.smartwaste.control.ControlLogicThread;
import org.unipi.smartwaste.db.DBDriver;
import org.unipi.smartwaste.mqtt.MQTTHandler;
import org.unipi.smartwaste.coap.COAPNetworkController;

import com.google.gson.Gson;

import java.io.FileReader;
import java.util.Scanner;

public class Main {

    private static final String LOG = "[Smart Waste]";

    private static final String[] possibleCommands = {
        ":get status",
        ":trigger fire",
        ":trigger leak",
        ":trigger full",
        ":get configuration",
        ":help",
        ":quit"
    };

    public static void main(String[] args) {

        System.out.println(LOG + " Welcome to the Smart Waste System!");

        System.out.println(LOG + " Loading configuration...");
        Configuration configuration = null;
        try (FileReader reader = new FileReader("config/devices.json")) {
            configuration = new Gson().fromJson(reader, Configuration.class);
        } catch (Exception e) {
            System.err.println(LOG + " Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println(configuration);

        // Initialize DB
        DBDriver db = new DBDriver();

        // Initialize MQTT handler
        MQTTHandler mqttHandler = new MQTTHandler(configuration.getSensors(), db);

        // Initialize CoAP actuator controller
        COAPNetworkController coapController = new COAPNetworkController(configuration.getActuators());

        // Start control logic thread
        ControlLogicThread controlLogic = new ControlLogicThread(mqttHandler, coapController);
        controlLogic.start();

        // Start user input loop
        Scanner scanner = new Scanner(System.in);
        printPossibleCommands();

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim().toLowerCase();

            if (isValidCommand(userInput)) {
                System.out.println(LOG + " Executing command: " + userInput);

                switch (userInput) {
                    case ":quit":
                        controlLogic.stopThread();
                        mqttHandler.close();
                        coapController.close();
                        db.close();
                        scanner.close();
                        System.out.println(LOG + " Bye!");
                        return;
                    case ":get status":
                        System.out.println(LOG + " Current sensor readings:");
                        mqttHandler.printSensorStatus();
                        break;
                    case ":trigger fire":
                        coapController.triggerActuator("fire");
                        break;
                    case ":trigger leak":
                        coapController.triggerActuator("leak");
                        break;
                    case ":trigger full":
                        coapController.triggerActuator("full");
                        break;
                    case ":get configuration":
                        System.out.println(configuration);
                        break;
                    case ":help":
                        printPossibleCommands();
                        break;
                }
            } else {
                System.out.println(LOG + " Invalid command. Type ':help' to see the list of available commands.");
            }
        }
    }

    private static void printPossibleCommands() {
        System.out.println(LOG + " Available commands:");
        for (String command : possibleCommands) {
            System.out.println(LOG + " - " + command);
        }
    }

    private static boolean isValidCommand(String userInput) {
        for (String command : possibleCommands) {
            if (command.equals(userInput)) return true;
        }
        return false;
    }
}
