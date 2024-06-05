# Build

## Simple Java Build
If you're interested in the Slurm computation manager only, you can easily compile the project using the following command:
```
$> mvn package
```

## Full Build
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

### Targets

| Target | Description |
| ------ | ----------- |
| clean | Clean modules |
| clean-thirdparty | Clean the thirdparty libraries |
| compile | Compile modules |
| package | Compile modules and create a distributable package |
| __install__ | __Compile modules and install it__ |
| docs | Generate the documentation (Doxygen/Javadoc) |
| help | Display this help |

### Options

The install.sh script options are saved in the *install.cfg* configuration file. This configuration file is loaded and
updated each time you use the install.sh script.

### Global options

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