/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.powsybl.commons.PowsyblException;

/**
 *
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class SlurmException extends PowsyblException {

    public SlurmException(Throwable cause) {
        super(cause);
    }

    public SlurmException(String message) {
        super(message);
    }
}
