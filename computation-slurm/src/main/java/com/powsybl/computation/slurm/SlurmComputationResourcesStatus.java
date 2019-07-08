/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.ComputationResourcesStatus;
import org.joda.time.DateTime;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * @author Yichen Tang <yichen.tang at rte-france.com>
 */
class SlurmComputationResourcesStatus implements ComputationResourcesStatus {

    private final DateTime dateTime;
    private final int availableCores;
    private final int busyCores;
    private final Map<String, Integer> busyCoresPerApp;

    SlurmComputationResourcesStatus(DateTime dateTime, int availableCores, int busyCores, Map<String, Integer> busyCoresPerApp) {
        this.dateTime = Objects.requireNonNull(dateTime);
        this.availableCores = availableCores;
        this.busyCores = busyCores;
        this.busyCoresPerApp = Collections.unmodifiableMap(Objects.requireNonNull(busyCoresPerApp));
    }

    @Override
    public DateTime getDate() {
        return dateTime;
    }

    @Override
    public int getAvailableCores() {
        return availableCores;
    }

    @Override
    public int getBusyCores() {
        return busyCores;
    }

    @Override
    public Map<String, Integer> getBusyCoresPerApp() {
        return busyCoresPerApp;
    }
}
