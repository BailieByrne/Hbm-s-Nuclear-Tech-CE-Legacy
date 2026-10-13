package com.hbm.interfaces;

import java.util.UUID;

public interface IExplosionRay {

    void update(long msBudget);

    void cancel();

    boolean isComplete();

    default boolean hasFailed() {
        return false;
    }

    boolean isContained();

    void setDetonator(UUID detonator);
}
