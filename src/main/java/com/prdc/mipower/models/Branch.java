package com.prdc.mipower.models;

import com.prdc.mipower.utils.Constants;

/**
 * A solved-result flow/loss/loading record for one branch (transmission
 * line or transformer) between two buses, from an OUT0 file's "LINE FLOWS
 * AND LINE LOSSES" / "TRANSFORMER FLOWS AND TRANSFORMER LOSSES" tables.
 *
 * <p>Abstract base for {@link Line} and {@link Transformer} -- both share
 * every field and most behavior; only {@link #getKind()} differs. This
 * models OUT0 *results*, which have a fixed, known shape (unlike the .dat0
 * *input* side, which stays fully dynamic via {@link DatRecord}).
 */
public abstract class Branch {

    public final int number;
    public final int fromBus;
    public final String fromName;
    public final int toBus;
    public final String toName;
    public final double mwFlow;
    public final double mvarFlow;
    public final double mwLoss;
    public final double mvarLoss;
    public final double loadingPercent;
    public final String loadingBand;

    protected Branch(int number, int fromBus, String fromName, int toBus, String toName,
                      double mwFlow, double mvarFlow, double mwLoss, double mvarLoss,
                      double loadingPercent, String loadingBand) {
        this.number = number;
        this.fromBus = fromBus;
        this.fromName = fromName;
        this.toBus = toBus;
        this.toName = toName;
        this.mwFlow = mwFlow;
        this.mvarFlow = mvarFlow;
        this.mwLoss = mwLoss;
        this.mvarLoss = mvarLoss;
        this.loadingPercent = loadingPercent;
        this.loadingBand = (loadingBand != null) ? loadingBand : "";
    }

    /** "Line" or "Transformer". */
    public abstract String getKind();

    public String label() {
        return getKind() + " " + fromBus + " (" + fromName + ") \u2192 " + toBus + " (" + toName + ")";
    }

    public boolean isOverloaded() {
        return loadingPercent >= Constants.OVERLOAD_THRESHOLD_PCT;
    }

    public boolean isHighlyLoaded() {
        return loadingPercent >= Constants.HIGH_LOADING_THRESHOLD_PCT
                && loadingPercent < Constants.OVERLOAD_THRESHOLD_PCT;
    }
}
