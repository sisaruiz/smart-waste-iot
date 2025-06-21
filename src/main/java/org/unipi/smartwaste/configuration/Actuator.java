package org.unipi.smartwaste.configuration;

public class Actuator {

    private String type;      // e.g. "fire", "leak", "full"
    private String address;   // IP address or hostname
    private int port;         // CoAP port, usually 5683
    private String resource;  // CoAP resource path, e.g. "actuator/fire"

    // Default no-args constructor (needed for Gson or JSON deserialization)
    public Actuator() {
    }

    // All-args constructor for convenience
    public Actuator(String type, String address, int port, String resource) {
        this.type = type;
        this.address = address;
        this.port = port;
        this.resource = resource;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    @Override
    public String toString() {
        return "Actuator{" +
                "type='" + type + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                ", resource='" + resource + '\'' +
                '}';
    }
}
