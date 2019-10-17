/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.google.common.base.Preconditions;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.computation.ComputationParameters;

import javax.annotation.Nullable;
import java.util.*;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
public class SlurmComputationParameters extends AbstractExtension<ComputationParameters> {

    private final String qos;
    private final Map<String, Integer> priorityByCmdId = new HashMap<>();
    private final Map<String, Integer> niceByCmdId = new HashMap<>();
    private final Map<String, Long> timeminByCmdId = new HashMap<>();

    public SlurmComputationParameters(ComputationParameters parameters, @Nullable String qos) {
        super(parameters);
        this.qos = qos;
    }

    public Optional<String> getQos() {
        if (qos == null || qos.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(qos);
    }

    public SlurmComputationParameters setPriority(String cmdId, int priority) {
        Objects.requireNonNull(cmdId);
        Preconditions.checkArgument(priority >= 0, "Priority must be >= 0");
        priorityByCmdId.put(cmdId, priority);
        return this;
    }

    public OptionalInt getPriority(String cmdId) {
        Integer i = priorityByCmdId.get(cmdId);
        return i == null ? OptionalInt.empty() : OptionalInt.of(i);
    }

    public SlurmComputationParameters setNice(String cmdId, int nice) {
        Objects.requireNonNull(cmdId);
        Preconditions.checkArgument(nice >= 0, "Invalid --nice value");
        niceByCmdId.put(cmdId, nice);
        return this;
    }

    public OptionalInt getNice(String cmdId) {
        Integer i = niceByCmdId.get(cmdId);
        return i == null ? OptionalInt.empty() : OptionalInt.of(i);
    }

    public SlurmComputationParameters setTimemin(String cmdId, long l) {
        timeminByCmdId.put(cmdId, l);
        return this;
    }

    public OptionalLong getTimemin(String cmdId) {
        Long aLong = timeminByCmdId.get(cmdId);
        return aLong == null ? OptionalLong.empty() : OptionalLong.of(aLong);
    }

    @Override
    public String getName() {
        return "SlurmComputationParameters";
    }
}
