# PowSyBl HPC

[![Actions Status](https://github.com/powsybl/powsybl-hpc/workflows/CI/badge.svg)](https://github.com/powsybl/powsybl-hpc/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-hpc&metric=coverage)](https://sonarcloud.io/component_measures?id=com.powsybl%3Apowsybl-hpc&metric=coverage)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=com.powsybl%3Apowsybl-hpc&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.powsybl%3Apowsybl-hpc)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)
[![Slack](https://img.shields.io/badge/slack-powsybl-blueviolet.svg?logo=slack)](https://join.slack.com/t/powsybl/shared_invite/zt-rzvbuzjk-nxi0boim1RKPS5PjieI0rA)
[![Javadocs](https://www.javadoc.io/badge/com.powsybl/powsybl-hpc.svg?color=blue)](https://www.javadoc.io/doc/com.powsybl/powsybl-hpc)

PowSyBl (**Pow**er **Sy**stem **Bl**ocks) is an open source framework written in Java, that makes it easy to write complex
software for power systems’ simulations and analysis. Its modular approach allows developers to extend or customize its
features.

PowSyBl is part of the LF Energy Foundation, a project of The Linux Foundation that supports open source innovation projects
within the energy and electricity sectors.

<p align="center">
<img src="https://raw.githubusercontent.com/powsybl/powsybl-gse/main/gse-spi/src/main/resources/images/logo_lfe_powsybl.svg?sanitize=true" alt="PowSyBl Logo" width="50%"/>
</p>

Read more at https://www.powsybl.org !

This project and everyone participating in it is governed by the [PowSyBl Code of Conduct](https://github.com/powsybl/.github/blob/main/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior to [powsybl-tsc@lists.lfenergy.org](mailto:powsybl-tsc@lists.lfenergy.org).

## Environment requirements
The project contains both Java and C++ modules. If you are only interested in the Slurm computation manager, you can build
the Java part only. If you're also interested in the MPI computation manager, you need to compiler C++ modules also.

To build the Java modules, you need the following requirements:
- JDK *(17 or greater)*
- Maven *(3.8.0 or greater)*

```
$> yum install java-1.17.0-openjdk maven 
```

To build the C++ modules, you need the additional requirements:
- CMake *(2.6 or greater)*
- A recent C++ compiler (GNU g++ or Clang)
- OpenMPI *(1.8.3 or greater)*
- Some development packages (zlib, bzip2)

```
$> yum install bzip2 bzip2-devel cmake gcc-c++ make wget zlib-devel
```

### OpenMPI 
In order to support the MPI modules, you need to compile and install the [OpenMPI](https://www.open-mpi.org/) library.
```
$> wget http://www.open-mpi.org/software/ompi/v1.8/downloads/openmpi-1.8.3.tar.bz2
$> tar xjf openmpi-1.8.3.tar.bz2
$> cd openmpi-1.8.3
$> ./configure --prefix=<INSTALL_DIR> --enable-mpi-thread-multiple
$> make install
$> export PATH=$PATH:<INSTALL_DIR>/bin
$> export LD_LIBRARY_PATH=<INSTALL_DIR>/lib:$LD_LIBRARY_PATH
```

## Build

### Simple Java Build
If you're interested in the Slurm computation manager only, you can easily compile the project using the following command:
```
$> mvn package
```

### Full Build
We provide a toolchain to help people to build this project. This toolchain downloads thirdparties (boost, libarchive,
log4cpp, protobuf) from the Intenet, compiles both Java and C++ modules and create an iTools distribution. It's a
standalone external folder that contains all the built objects required to run powsybl programs through the itools
command-line interface. This repository contains the `install.sh` script to do so easily. By default, the `install.sh`
will download dependencies from the Internet, compile code and finally copy the resulting iTools distribution to the
install folder.
```
$> ./install.sh
```
This will run both the C++ build and the java build and copy their results to the install folder.

A more detailled description of the install.sh script options follows:

#### Targets

| Target | Description |
| ------ | ----------- |
| clean | Clean modules |
| clean-thirdparty | Clean the thirdparty libraries |
| compile | Compile modules |
| package | Compile modules and create a distributable package |
| __install__ | __Compile modules and install it__ |
| docs | Generate the documentation (Doxygen/Javadoc) |
| help | Display this help |

#### Options

The install.sh script options are saved in the *install.cfg* configuration file. This configuration file is loaded and
updated each time you use the install.sh script.

#### Global options

| Option | Description | Default value |
| ------ | ----------- | ------------- |
| --help | Display this help | |
| --prefix | Set the installation directory | $HOME/powsybl |

#### Third-parties

| Option | Description | Default value |
| ------ | ----------- | ------------- |
| --with-thirdparty | Enable the compilation of thirdparty libraries | |
| --without-thirdparty | Disable the compilation of thirdparty libraries | |
| --thirdparty-prefix | Set the thirdparty installation directory | $HOME/powsybl_thirdparty |
| --thirdparty-download | Sets false to compile thirdparty libraries from a local repository | true |
| --thirdparty-packs | Sets the thirdparty libraries local repository | $HOME/powsybl_packs |

#### Default configuration file
```
#  -- Global options --
powsybl_prefix=$HOME/powsybl

#  -- Thirdparty libraries --
thirdparty_build=true
thirdparty_prefix=$HOME/powsybl_thirdparty
thirdparty_download=true
thirdparty_packs=$HOME/powsybl_packs
```
