package org.unipi.smartwaste.db;

import java.sql.SQLException;
import java.util.HashMap;

public class testDB {
    public static void main(String[] args) {
        try {
            int rowsUpdated = DBDriver.updateActuators("/192.168.0.1", "temperature", true);
            System.out.println("Rows updated (actuator): " + rowsUpdated);

            HashMap<String, Object> actuatorStatus = DBDriver.retrieveActuator("temperature");
            System.out.println("Actuator status: " + actuatorStatus);

            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during actuator status test");
        }

        try {
            System.out.println("Insert temperature 20: " +
                DBDriver.insertData(20L, "temperature"));
            System.out.println("Insert humidity 40: " +
                DBDriver.insertData(40L, "humidity"));
            System.out.println("Insert temperature 22: " +
                DBDriver.insertData(22L, "temperature"));
            System.out.println("Insert distance 75: " +
                DBDriver.insertData(75L, "distance"));

            HashMap<String, Integer> latestValues = DBDriver.retrieveData();
            System.out.println("Latest sensor values: " + latestValues);

            Integer latestTemp = DBDriver.getLatestSensorValue("temperature");
            System.out.println("Latest temperature value: " + latestTemp);

            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during sensor data test");
        }
    }
}
