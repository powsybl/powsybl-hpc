/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.computation.ComputationParameters;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * @author Yichen TANG {@literal <yichen.tang at rte-france.com>}
 */
public class SlurmComputationParameters extends AbstractExtension<ComputationParameters> {

    private final String qos;
    private final Integer mem;

    public SlurmComputationParameters(ComputationParameters parameters, @Nullable String qos) {
        this(parameters, qos, null);
    }

    /**
     * Additionnal parameters for slurm
     * @param parameters base parameters to extend
     * @param qos name of the QOS
     * @param mem memory usage limit (in MBytes)
     */
    public SlurmComputationParameters(ComputationParameters parameters, @Nullable String qos, Integer mem) {
        super(parameters);
        this.qos = qos;
        this.mem = mem;
    }

    public OptionalInt getMem() {
        return mem != null && mem >= 0 ? OptionalInt.of(mem) : OptionalInt.empty();
    }

    public Optional<String> getQos() {
        if (qos == null || qos.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(qos);
    }

    @Override
    public String getName() {
        return "SlurmComputationParameters";
    }
}
