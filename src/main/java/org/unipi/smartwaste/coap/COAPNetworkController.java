package org.unipi.smartwaste.coap;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.unipi.smartwaste.configuration.Actuator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class COAPNetworkController {

    private final Map<String, Actuator> actuatorMap = new HashMap<>();

    public COAPNetworkController(List<Actuator> actuators) {
        for (Actuator actuator : actuators) {
            actuatorMap.put(actuator.getType().toLowerCase(), actuator);
        }
        System.out.println("[COAPNetworkController] Initialized with actuators: " + actuatorMap.keySet());
    }

    /**
     * Trigger an actuator by type, sending a PUT or POST to its CoAP resource
     * @param actuatorType actuator type e.g. "fire", "leak", "full"
     */
    public void triggerActuator(String actuatorType) {
        Actuator actuator = actuatorMap.get(actuatorType.toLowerCase());
        if (actuator == null) {
            System.err.println("[COAPNetworkController] Unknown actuator type: " + actuatorType);
            return;
        }

        String coapUri = buildCoapUri(actuator);
        CoapClient client = new CoapClient(coapUri);

        System.out.println("[COAPNetworkController] Triggering actuator '" + actuatorType + "' at " + coapUri);

        try {
            CoapResponse response = client.put("", 0); // Empty payload, plain text

            if (response != null) {
                System.out.println("[COAPNetworkController] Response: " + response.getCode() + " - " + response.getResponseText());
            } else {
                System.err.println("[COAPNetworkController] No response from actuator " + actuatorType);
            }
        } catch (Exception e) {
            System.err.println("[COAPNetworkController] Error triggering actuator '" + actuatorType + "': " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.shutdown();
        }
    }

    private String buildCoapUri(Actuator actuator) {
        // Example: coap://[address]:[port]/[resource]
        String address = actuator.getAddress();
        int port = actuator.getPort();
        String resource = actuator.getResource();

        return String.format("coap://%s:%d/%s", address, port, resource);
    }

    /**
     * Cleanup if needed (not strictly required here)
     */
    public void close() {
        // No persistent resources here, but implement if you add persistent clients
        System.out.println("[COAPNetworkController] Closed");
    }
}
