package org.unipi.smartwaste.configuration;

public class Sensor {
    private String type;
    private String topic;

    public String getType() {
        return type;
    }

    public String getTopic() {
        return topic;
    }

    @Override
    public String toString() {
        return "[" + type + " @ " + topic + "]";
    }
}
