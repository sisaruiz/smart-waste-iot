package org.unipi.smartwaste.db;

import java.sql.SQLException;

public class testDB {
    public static void main(String[] args) {
        try {
            System.out.println(DBDriver.updateActuatorStatus("/192.168.0.1", "temperature", true));
            System.out.println(DBDriver.getActuatorStatus("temperature"));
            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during actuator status test");
        }

        try {
            System.out.println(DBDriver.insertSensorData(20L, "temperature"));
            System.out.println(DBDriver.insertSensorData(40L, "humidity"));
            System.out.println(DBDriver.insertSensorData(22L, "temperature"));
            System.out.println(DBDriver.insertSensorData(75L, "distance"));  // Added distance sensor test
            System.out.println(DBDriver.getLatestSensorData());
            DBDriver.resetActuators();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during sensor data test");
        }
    }
}
