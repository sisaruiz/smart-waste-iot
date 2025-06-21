package org.unipi.smartwaste.configuration;

import java.util.List;

public class Configuration {
    private List<Sensor> sensors;
    private List<Actuator> actuators;

    public List<Sensor> getSensors() {
        return sensors;
    }

    public List<Actuator> getActuators() {
        return actuators;
    }

    @Override
    public String toString() {
        return "Sensors:\n" + sensors.toString() + "\nActuators:\n" + actuators.toString();
    }
}
