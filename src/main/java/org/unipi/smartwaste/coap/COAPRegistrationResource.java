package org.unipi.smartwaste.coap;

import org.unipi.smartwaste.db.DBDriver;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.sql.SQLException;

public class COAPRegistrationResource extends CoapResource {

    public COAPRegistrationResource(String name) {
        super(name);
        getAttributes().setTitle("Registration Resource");
    }

    @Override
    public void handlePOST(CoapExchange exchange) {
        String s = new String(exchange.getRequestPayload());
        JSONObject obj;
        JSONParser parser = new JSONParser();
        try {
            obj = (JSONObject) parser.parse(s);
        } catch (ParseException e) {
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Invalid JSON");
            return;
        }

        String ip = exchange.getSourceAddress().getHostAddress();  // use getHostAddress()
        System.out.println("The actuator with IP " + ip + " is registering");

        String type = (String) obj.get("type");
        if (type == null || type.isEmpty()) {
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST, "Missing actuator type");
            return;
        }

        try {
            int modified = DBDriver.updateActuatorStatus(ip, type, false); // actuators start OFF
            if (modified < 1) {
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            } else {
                exchange.respond(CoAP.ResponseCode.CREATED);
            }
        } catch (SQLException e) {
            System.err.println("Database error: cannot connect");
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

}
