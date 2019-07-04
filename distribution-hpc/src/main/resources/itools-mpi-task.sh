#!/bin/bash
#
# Copyright (c) 2017, RTE (http://www.rte-france.com)
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#

if [ -z $JAVA_HOME ]; then
    JAVA_BIN=`which java 2> /dev/null`
    if [ $? -ne 0 ]; then
        echo "Unable to find java" >&2
        exit -1
    fi
else
    JAVA_BIN=$JAVA_HOME/bin/java
fi

installBinDir=$(dirname $(readlink -f $0))
installDir=${installBinDir%/*}

. $installDir/etc/itools.conf

if [[ -n "$OMPI_MCA_rmaps_base_cpus_per_rank" ]]; then
    CORES=$OMPI_MCA_rmaps_base_cpus_per_rank
else
    CORES=1
fi

tmpdir=$HOME/tmp 
mkdir $tmpdir > /dev/null 2>&1

rank=$OMPI_COMM_WORLD_RANK
if [ $rank = 0 ]; then
    export LD_PRELOAD=libmpi.so
    export LD_LIBRARY_PATH=${installDir}/lib:$LD_LIBRARY_PATH
    [ -n "$powsybl_config_dirs" ] && options+=" -Dpowsybl.config.dirs=$powsybl_config_dirs"
    [ -n "$powsybl_config_name" ] && options+=" -Dpowsybl.config.name=$powsybl_config_name"
    options+=" -Dlogback.configurationFile="
    [ -f "$powsybl_config_dirs/logback-itools.xml" ] && options+="$powsybl_config_dirs" || options+="$installDir/etc"
    options+="/logback-itools.xml"
    [ -z "$java_xmx" ] && java_xmx=8G
    $JAVA_BIN \
-Xmx$java_xmx \
-cp "$installDir/share/java/*" \
-verbose:gc -XX:+PrintGCTimeStamps -Xloggc:$tmpdir/gc.log \
$options \
com.powsybl.computation.mpi.MpiMaster \
"$@" \
--tmp-dir=$tmpdir \
--statistics-db-dir=$HOME \
--statistics-db-name="statistics" \
--cores=$CORES \
--stdout-archive=$tmpdir/stdout-archive.zip
else
    mkdir $HOME/archive > /dev/null 2>&1
    rm -r $tmpdir/itools_common_${rank}* > /dev/null 2>&1
    rm -r $tmpdir/itools_job_${rank}* > /dev/null 2>&1
    rm -r $tmpdir/itools_work_${rank}* > /dev/null 2>&1
    ${installDir}/bin/slave --tmp-dir=$tmpdir --archive-dir=$HOME/archive --log-file=$tmpdir/slave.log --cores=$CORES
fi
