package org.unipi.smartwaste.coap;

import org.eclipse.californium.core.CoapServer;
import org.unipi.smartwaste.db.DBDriver;

import java.sql.SQLException;

public class COAPRegistrationHandler extends CoapServer implements Runnable {

    private static final COAPRegistrationHandler instance = new COAPRegistrationHandler();

    private COAPRegistrationHandler() {
        // Private constructor to enforce singleton pattern
    }

    public static COAPRegistrationHandler getInstance() {
        return instance;
    }

    @Override
    public void run() {
        try {
            DBDriver.resetActuators();
            System.out.println("[COAPRegistrationHandler] Actuators table reset successfully.");
        } catch (SQLException e) {
            System.err.println("[COAPRegistrationHandler] Unable to reset actuators table: " + e.getMessage());
        }

        COAPRegistrationHandler server = COAPRegistrationHandler.getInstance();
        server.add(new COAPRegistrationResource("registration"));
        server.start();

        System.out.println("[COAPRegistrationHandler] Registration server started");
    }
}
