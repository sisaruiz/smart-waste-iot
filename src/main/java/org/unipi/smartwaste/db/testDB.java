package org.unipi.smartwaste.db;

import java.sql.SQLException;
import java.util.HashMap;

public class testDB {
    public static void main(String[] args) {
        try {
            // Test updating actuator status
            int rowsUpdated = DBDriver.updateActuatorStatus("/192.168.0.1", "temperature", true);
            System.out.println("Rows updated (actuator): " + rowsUpdated);

            // Test retrieving actuator status
            HashMap<String, Object> actuatorStatus = DBDriver.retrieveActuator("temperature");
            System.out.println("Actuator status: " + actuatorStatus);

            // Clear actuators table
            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during actuator status test");
        }

        try {
            // Insert sample sensor data
            System.out.println("Insert temperature 20: " + DBDriver.insertSensorData(20L, "temperature"));
            System.out.println("Insert humidity 40: " + DBDriver.insertSensorData(40L, "humidity"));
            System.out.println("Insert temperature 22: " + DBDriver.insertSensorData(22L, "temperature"));
            System.out.println("Insert distance 75: " + DBDriver.insertSensorData(75L, "distance"));

            // Retrieve latest sensor values
            HashMap<String, Integer> latestValues = DBDriver.retrieveData();
            System.out.println("Latest sensor values: " + latestValues);

            // Clear actuators table again
            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during sensor data test");
        }
    }
}
