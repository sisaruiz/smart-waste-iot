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
            // Insert sample sensor data (provide sensor IP now)
            System.out.println("Insert temperature 20 from 192.168.0.100: " +
                DBDriver.insertSensorData(20L, "temperature", "192.168.0.100"));
            System.out.println("Insert humidity 40 from 192.168.0.101: " +
                DBDriver.insertSensorData(40L, "humidity", "192.168.0.101"));
            System.out.println("Insert temperature 22 from 192.168.0.100: " +
                DBDriver.insertSensorData(22L, "temperature", "192.168.0.100"));
            System.out.println("Insert distance 75 from 192.168.0.102: " +
                DBDriver.insertSensorData(75L, "distance", "192.168.0.102"));

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
