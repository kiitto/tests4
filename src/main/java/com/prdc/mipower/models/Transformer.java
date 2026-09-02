package com.prdc.mipower.models;

/**
 * A transformer's solved-result flow/loss/loading record. Not explicitly
 * requested by name, but added alongside {@link Line} because the OUT0
 * file's data genuinely has both a "TRANSFORMER FLOWS AND TRANSFORMER
 * LOSSES" table and a "LINE FLOWS AND LINE LOSSES" table, and {@link Branch}
 * exists specifically to model both through one shared, inherited shape.
 */
public class Transformer extends Branch {

    public Transformer(int number, int fromBus, String fromName, int toBus, String toName,
                        double mwFlow, double mvarFlow, double mwLoss, double mvarLoss,
                        double loadingPercent, String loadingBand) {
        super(number, fromBus, fromName, toBus, toName, mwFlow, mvarFlow, mwLoss, mvarLoss,
                loadingPercent, loadingBand);
    }

    @Override
    public String getKind() {
        return "Transformer";
    }
}
