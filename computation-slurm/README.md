### Slurm based computation manager

This computation manager implementation submits commands to a [slurm](https://slurm.schedmd.com/) managed infrastructure.

In order to use it, you need to follow fhe following steps.

#### 1. Add the implementation to your classpath

If you use maven for your project, add the following dependency:
```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-computation-slurm</artifactId>
    <version>${powsyblhpc.version}</version>
    <scope>runtime</scope>
</dependency>
```

#### 2. Configure the implementation as your computation manager

In your powsybl configuration file, you may choose to use that computation manager for you short or long computations:
```yml
default-computation-manager:
    short-time-execution-computation-manager-factory: com.powsybl.computation.slurm.SlurmComputationManagerFactory
    long-time-execution-computation-manager-factory: com.powsybl.computation.slurm.SlurmComputationManagerFactory
```

#### 3. Configure the infrastructure information

Finally, you need to define how to connect to the slurm infrastructure in you powsybl configuration :

```yml
slurm-computation-manager:
    remote: true
    hostname: hostname
    port: 22
    username: username
    password: password
    remote-dir: /remote/dir
    local-dir: /local/dir
    max-ssh-connection: 10
    max-retry: 5
    polling-time: 10
```

If `remote` is set to `true`, the computation manager sends command execution requests and copies files through ssh protocol. Otherwise, the connection settings are ignored and command execution requests are submitted on localhost, assuming that slurm is installed. This second scheme allows to more efficiently submit jobs from within a slurm managed infrastructure.

In order to know when the command execution has actually finished, the computation managers polls for a "flag" file created in the remote directory on execution end.
