package com.prdc.mipower.models;

/**
 * A solved-result bus voltage/power record from an OUT0 file's "BUS
 * VOLTAGES AND POWERS" table.
 *
 * <p>{@code voltageFlag} carries MiPower's own marker characters: "@" means
 * below the minimum voltage limit, "#" means above the maximum voltage
 * limit, empty means within limits.
 */
public class Bus {

    public final int number;
    public final String name;
    public final double voltagePu;
    public final double angleDeg;
    public final double mwGeneration;
    public final double mvarGeneration;
    public final double mwLoad;
    public final double mvarLoad;
    public final String voltageFlag;

    public Bus(int number, String name, double voltagePu, double angleDeg,
               double mwGeneration, double mvarGeneration, double mwLoad, double mvarLoad,
               String voltageFlag) {
        this.number = number;
        this.name = name;
        this.voltagePu = voltagePu;
        this.angleDeg = angleDeg;
        this.mwGeneration = mwGeneration;
        this.mvarGeneration = mvarGeneration;
        this.mwLoad = mwLoad;
        this.mvarLoad = mvarLoad;
        this.voltageFlag = (voltageFlag != null) ? voltageFlag : "";
    }

    public boolean isBelowMinVoltage() {
        return voltageFlag.contains("@");
    }

    public boolean isAboveMaxVoltage() {
        return voltageFlag.contains("#");
    }

    public boolean hasVoltageViolation() {
        return isBelowMinVoltage() || isAboveMaxVoltage();
    }

    public boolean isGenerating() {
        return mwGeneration > 0.0 || mvarGeneration != 0.0;
    }
}
