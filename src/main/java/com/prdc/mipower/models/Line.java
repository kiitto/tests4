package com.prdc.mipower.models;

/** A transmission line's solved-result flow/loss/loading record. */
public class Line extends Branch {

    public Line(int number, int fromBus, String fromName, int toBus, String toName,
                double mwFlow, double mvarFlow, double mwLoss, double mvarLoss,
                double loadingPercent, String loadingBand) {
        super(number, fromBus, fromName, toBus, toName, mwFlow, mvarFlow, mwLoss, mvarLoss,
                loadingPercent, loadingBand);
    }

    @Override
    public String getKind() {
        return "Line";
    }
}
