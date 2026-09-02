package com.prdc.mipower.models;

/**
 * A generating unit at a bus. Currently derived from {@link Bus} data (the
 * OUT0 "BUS VOLTAGES AND POWERS" table already carries MW/MVAr generation
 * per bus) via {@link #fromBus}, rather than from the separate "GENERATOR
 * DATA FOR FREQUENCY DEPENDENT LOAD FLOW" block in the OUT0 file (P-RATE,
 * P-MIN, P-MAX, droop, participation factor, etc.), which {@code Out0Parser}
 * doesn't parse yet in this module. If those extra fields are wanted later,
 * this class's constructor is where they'd be added and {@code Out0Parser}
 * is where that block would get parsed.
 */
public class Generator {

    public final int busNumber;
    public final String busName;
    public final double mwGeneration;
    public final double mvarGeneration;

    public Generator(int busNumber, String busName, double mwGeneration, double mvarGeneration) {
        this.busNumber = busNumber;
        this.busName = busName;
        this.mwGeneration = mwGeneration;
        this.mvarGeneration = mvarGeneration;
    }

    /** Derives a Generator view from a Bus that is actually generating. */
    public static Generator fromBus(Bus bus) {
        return new Generator(bus.number, bus.name, bus.mwGeneration, bus.mvarGeneration);
    }
}
