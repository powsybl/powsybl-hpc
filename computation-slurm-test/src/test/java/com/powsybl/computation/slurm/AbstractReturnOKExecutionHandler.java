/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.computation.slurm;

import com.powsybl.computation.AbstractExecutionHandler;
import com.powsybl.computation.ExecutionReport;

import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Yichen TANG <yichen.tang at rte-france.com>
 */
abstract class AbstractReturnOKExecutionHandler extends AbstractExecutionHandler<String> {
    @Override
    public String after(Path workingDir, ExecutionReport report) throws IOException {
        super.after(workingDir, report);
        return "OK";
    }
}
