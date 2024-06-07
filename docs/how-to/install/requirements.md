# Requirements
The project contains both Java and C++ modules. If you are only interested in the Slurm computation manager, you can build
the Java part only. If you're also interested in the MPI computation manager, you need to compiler C++ modules also.

## Java
To build the Java modules, you need the following requirements:
- JDK *(17 or greater)*
- Maven *(3.8.0 or greater)*

```
$> yum install java-17-openjdk maven 
```

## C++
To build the C++ modules, you need the additional requirements:
- CMake *(2.6 or greater)*
- A recent C++ compiler (GNU g++ or Clang)
- OpenMPI *(1.8.3 or greater)*
- Some development packages (zlib, bzip2)

```
$> yum install bzip2 bzip2-devel cmake gcc-c++ make wget zlib-devel
```

In order to support the MPI modules, you also need to compile and install the [OpenMPI](https://www.open-mpi.org/) library.
```
$> wget https://download.open-mpi.org/release/open-mpi/v1.8/openmpi-1.8.3.tar.bz2
$> tar xjf openmpi-1.8.3.tar.bz2
$> cd openmpi-1.8.3
$> ./configure --prefix=<INSTALL_DIR> --enable-mpi-thread-multiple
$> make install
$> export PATH=$PATH:<INSTALL_DIR>/bin
$> export LD_LIBRARY_PATH=<INSTALL_DIR>/lib:$LD_LIBRARY_PATH
```
