package org.unipi.smartwaste.app;

import org.unipi.smartwaste.coap.COAPClient;
import org.unipi.smartwaste.db.DBDriver;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Scanner;

public class CommandLineInterface implements Runnable {

    private static final CommandLineInterface instance = new CommandLineInterface();

    private CommandLineInterface() {}

    public static CommandLineInterface getInstance() {
        return instance;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to Smart Waste Bin Management System\n" +
                "Available commands (insert the corresponding number):\n" +
                "1 - Help list\n" +
                "2 - Change temperature threshold\n" +
                "3 - Change humidity threshold\n" +
                "4 - Check bin status\n" +
                "5 - Trigger actuator");

        while (true) {
            System.out.print("\nInsert a command: ");
            String inputStr = scanner.nextLine().trim();
            int input;
            try {
                input = Integer.parseInt(inputStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid command number.");
                continue;
            }

            switch (input) {
                case 1 -> helpList();
                case 2 -> changeThreshold("temperature", scanner);
                case 3 -> changeThreshold("humidity", scanner);
                case 4 -> {
                    if (!Main.isDataReady()) {
                        System.out.println("No sensor data available yet. Please wait a moment and try again.");
                    } else {
                        try {
                            printBinStatus();
                        } catch (SQLException e) {
                            System.out.println("Unable to retrieve bin status.");
                        }
                    }
                }
                case 5 -> {
                    if (!Main.isDataReady()) {
                        System.out.println("No sensor data available yet. Please wait a moment and try again.");
                    } else {
                        handleActuatorControl(scanner);
                    }
                }
                default -> System.out.println("Unknown command.");
            }
        }
    }

    private static void helpList() {
        System.out.println(
                "\nAvailable commands:\n" +
                "1 - Help list\n" +
                "2 - Change temperature threshold\n" +
                "3 - Change humidity threshold\n" +
                "4 - Check bin status\n" +
                "5 - Trigger actuator"
        );
    }

    private static void changeThreshold(String type, Scanner scanner) {
        System.out.print("Do you want to change the MIN or MAX threshold? ");
        String minMax = scanner.nextLine().trim().toUpperCase();

        System.out.print("Insert the new value: ");
        int value;
        try {
            value = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid value.");
            return;
        }

        boolean done = false;
        if ("MIN".equals(minMax)) {
            done = switch (type) {
                case "temperature" -> PeriodicDataRetrieval.setMinTemperatureThreshold(value);
                case "humidity" -> PeriodicDataRetrieval.setMinHumidityThreshold(value);
                default -> false;
            };
        } else if ("MAX".equals(minMax)) {
            done = switch (type) {
                case "temperature" -> PeriodicDataRetrieval.setMaxTemperatureThreshold(value);
                case "humidity" -> PeriodicDataRetrieval.setMaxHumidityThreshold(value);
                default -> false;
            };
        } else {
            System.out.println("Invalid choice. Type MIN or MAX.");
        }

        if (done) {
            System.out.println("Threshold updated successfully.");
        } else {
            System.out.println("Threshold update failed.");
        }
    }

    private static void printBinStatus() throws SQLException {
        HashMap<String, Object> bin = DBDriver.getBinStatus();

        if (bin == null || bin.isEmpty()) {
            System.out.println("No bin data available.");
            return;
        }

        System.out.println("\nBin status:");
        System.out.println("  Fill level: " + bin.get("fill_level"));
        System.out.println("  Temperature: " + bin.get("temperature"));
        System.out.println("  Humidity: " + bin.get("humidity"));
        System.out.println("  Anomaly Fire Active: " + bin.get("anomaly_fire_active"));
        System.out.println("  Anomaly Leakage Active: " + bin.get("anomaly_leakage_active"));
        System.out.println("  Full Bin Active: " + bin.get("full_active"));
        System.out.println("-----------------------------------");
    }

    private void handleActuatorControl(Scanner scanner) {
        // The IP should be known internally in COAPClient or config, no need to get here
        System.out.println("\nAvailable actuators:");
        System.out.println("1. full_bin");
        System.out.println("2. anomaly_fire");
        System.out.println("3. anomaly_leakage");
        System.out.print("Select actuator (1-3): ");

        int actuatorChoice;
        try {
            actuatorChoice = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid choice.");
            return;
        }

        String actuatorName;
        switch (actuatorChoice) {
            case 1 -> actuatorName = "full_bin";
            case 2 -> actuatorName = "anomaly_fire";
            case 3 -> actuatorName = "anomaly_leakage";
            default -> {
                System.out.println("Invalid actuator choice.");
                return;
            }
        }

        System.out.print("Activate or deactivate? (A/D): ");
        String onOff = scanner.nextLine().trim().toUpperCase();
        boolean activate;
        if ("A".equals(onOff)) {
            activate = true;
        } else if ("D".equals(onOff)) {
            activate = false;
        } else {
            System.out.println("Invalid choice, please enter A or D.");
            return;
        }

        System.out.println("Sending CoAP PUT to actuator '" + actuatorName + "' to " + (activate ? "activate" : "deactivate"));

        try {
            // IP/address is handled inside actuatorCall now (from static config)
            COAPClient.actuatorCall(actuatorName, activate, 0);
            System.out.println("Command sent.");
        } catch (SQLException e) {
            System.out.println("Error sending actuator command: " + e.getMessage());
        }
    }
}
