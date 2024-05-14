/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.computation.slurm;

import com.jcraft.jsch.JSchException;

/**
 *
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class UncheckedJSchException extends RuntimeException {

    private static final long serialVersionUID = -5020160880471004733L;

    public UncheckedJSchException(JSchException cause) {
        super(cause);
    }

    @Override
    public synchronized JSchException getCause() {
        return (JSchException) super.getCause();
    }
}
