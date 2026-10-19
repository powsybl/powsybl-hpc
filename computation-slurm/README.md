<!-- 
Copyright (c) 2026, RTE (http://www.rte-france.com)
This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
SPDX-License-Identifier: MPL-2.0
-->
### Slurm-based computation manager

This computation manager implementation submits commands to a [Slurm](https://slurm.schedmd.com/)-managed infrastructure.
To use it, follow these steps.
> [!NOTE]
> To monitor when command execution has finished, the computation manager polls for a "flag" file created in the working directory upon execution completion (in `remote-dir` or `working-dir` depending on remote/local mode) .

#### 1. Add the implementation to your classpath

If you use Maven for your project, add the following dependency:
```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-computation-slurm</artifactId>
    <version>${powsyblhpc.version}</version>
    <scope>runtime</scope>
</dependency>
```

#### 2. Configure the implementation as your computation manager

In your PowSyBl configuration file, you may configure this computation manager for short or long computations:
```yml
default-computation-manager:
    short-time-execution-computation-manager-factory: com.powsybl.computation.slurm.SlurmComputationManagerFactory
    long-time-execution-computation-manager-factory: com.powsybl.computation.slurm.SlurmComputationManagerFactory
```

#### 3. Configure the infrastructure information

Finally, define how to connect to the Slurm infrastructure in your PowSyBl configuration.

**Core Configuration Properties**

These properties apply across both local and remote Slurm execution modes.

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `remote` | Boolean | `true` | When `true`, job submission and file copying are performed remotely over SSH. Remote execution properties must be provided (see below). When `false`, the connection settings are ignored and command execution requests are submitted on localhost, assuming that Slurm is installed. This allows to submit jobs more efficiently from within a Slurm-managed infrastructure. |
| `local-dir` | Path | *Required* | Path to the local staging directory used for preparing computation inputs and receiving simulation outputs. |
| `polling-time` | Integer | `10` | Interval (in seconds) between checks for the "flag" file that indicates task completion. |
| `scontrol-time` | Integer | `240` | Interval (in seconds) between periodic `scontrol` command executions to monitor and detect terminated, timed-out, or cancelled Slurm jobs. |
| `job-array` | Boolean | `true` | Enables running a collection of similar jobs at once (see Slurm `sbatch` option `--array`). |

---

**Remote Execution Properties**

These parameters are only used when `remote` is `true`.

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `hostname` | String | *Required* | SSH hostname or IP address of the remote Slurm infrastructure. |
| `port` | Integer | `22` | SSH port. |
| `username` | String | *Required* | SSH login. |
| `password` | String | *Required* | SSH password. |
| `remote-dir` | String | *Required* | Working directory path on the remote Slurm infrastructure where files are copied during job execution. |
| `max-ssh-connection` | Integer | `10` | Maximum number of pooled concurrent SSH sessions. |
| `max-retry` | Integer | `5` | Maximum number of retry attempts when job submission fails. |

---

**Local Execution Properties**

These parameters are only used when `remote` is `false`.

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `working-dir` | String | *Required* | Local working directory where files are copied during job execution. |

---

**YAML Configuration Example**

Example for a remote Slurm:

```yaml
slurm-computation-manager:
  remote: true

  # Shared parameters
  local-dir: "/local/dir"
  polling-time: 10                 
  scontrol-time: 240               
  job-array: true                  

  # Remote mode parameters (Required when remote: true)
  hostname: "slurm.example.com"
  port: 22                     
  username: "powsybl-user"     
  password: "cluster-password" 
  remote-dir: "/shared/slurm/work"
  max-ssh-connection: 10          
  max-retry: 5                    
```

Example for a local Slurm:

```yaml
slurm-computation-manager:
  remote: false

  # Shared parameters
  local-dir: "/local/dir"
  polling-time: 10                 
  scontrol-time: 240               
  job-array: true

  # Local mode parameters (Required when remote: false)
  working-dir: "/tmp/slurm_work_dir"
```
