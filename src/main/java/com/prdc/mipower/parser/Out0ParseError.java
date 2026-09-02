package com.prdc.mipower.parser;

/** Thrown when a selected file is not a recognizable MiPower OUT0 file. */
public class Out0ParseError extends Exception {

    public Out0ParseError(String message) {
        super(message);
    }
}
