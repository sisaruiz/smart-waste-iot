package org.unipi.smartwaste.app;

import org.unipi.smartwaste.coap.COAPClient;
import org.unipi.smartwaste.db.DBDriver;

import java.sql.SQLException;
import java.util.*;

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
                "4 - Check bins status");
        while (true) {
            System.out.println("Insert a command: ");
            String inputStr = scanner.nextLine().trim();
            int input;
            try {
                input = Integer.parseInt(inputStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid command number");
                continue;
            }

            switch (input) {
                case 1:
                    helpList();
                    break;

                case 2:
                    changeThreshold("temperature", scanner);
                    break;

                case 3:
                    changeThreshold("humidity", scanner);
                    break;

                case 4:
                    try {
                        printBinsStatus(scanner);
                    } catch (SQLException e) {
                        System.out.println("Unable to retrieve bins status");
                    }
                    break;

                default:
                    System.out.println("Unknown command");
            }
        }
    }

    private static void helpList() {
        System.out.println(
                "Available commands:\n" +
                "1 - Help list\n" +
                "2 - Change temperature threshold\n" +
                "3 - Change humidity threshold\n" +
                "4 - Check bins status"
        );
    }

    private static void changeThreshold(String type, Scanner scanner) {
        System.out.println("Do you want to change the MIN or MAX threshold?");
        String minMax = scanner.nextLine().trim().toUpperCase();
        System.out.println("Insert the new value:");
        int value;
        try {
            value = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid value");
            return;
        }

        boolean done = false;
        if ("MIN".equals(minMax)) {
            if ("temperature".equals(type)) {
                done = PeriodicDataRetrieval.setMinTemperatureThreshold(value);
            } else if ("humidity".equals(type)) {
                done = PeriodicDataRetrieval.setMinHumidityThreshold(value);
            }
        } else if ("MAX".equals(minMax)) {
            if ("temperature".equals(type)) {
                done = PeriodicDataRetrieval.setMaxTemperatureThreshold(value);
            } else if ("humidity".equals(type)) {
                done = PeriodicDataRetrieval.setMaxHumidityThreshold(value);
            }
        } else {
            System.out.println("Invalid choice. Type MIN or MAX.");
        }

        if (done) {
            System.out.println("Threshold updated successfully.");
        } else {
            System.out.println("Threshold update failed.");
        }
    }

    private static void printBinsStatus(Scanner scanner) throws SQLException {
        List<HashMap<String, Object>> bins = DBDriver.retrieveAllBinsStatus();

        for (HashMap<String, Object> bin : bins) {
            String binId = (String) bin.get("id");
            String ip = (String) bin.get("ip");
            System.out.println("Bin ID: " + binId);
            System.out.println("  Fill level: " + bin.get("fill_level"));
            System.out.println("  Temperature: " + bin.get("temperature"));
            System.out.println("  Humidity: " + bin.get("humidity"));
            System.out.println("  Anomaly actuator active: " + bin.get("anomaly_active"));
            System.out.println("  Full actuator active: " + bin.get("full_active"));
            System.out.println("-----------------------------------");

            if (Boolean.TRUE.equals(bin.get("anomaly_active"))) {
                System.out.println("Do you want to deactivate the anomaly actuator for Bin ID " + binId + "? (YES/NO)");
                String input = scanner.nextLine().trim();
                if ("YES".equalsIgnoreCase(input)) {
                    COAPClient.actuatorCall(ip, "anomaly", false, 0);
                    System.out.println("Anomaly actuator deactivated for Bin ID " + binId);
                }
            }
        }

        if (bins.isEmpty()) {
            System.out.println("No bins found.");
        }
    }
}
