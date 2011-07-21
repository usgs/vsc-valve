#!/bin/bash

# A simple script to deploy Tomcat, useful in testing.

# Set Tomcat location here
tomcat=/home/andrew/projects/isti/pkg/apache-tomcat-6.0.29/

pushd "$tomcat"
bin/shutdown.sh
sleep 5
pushd webapps
rm -fr valve3
tar xvfz ../../../valve/Valve3Web/dist/valve3-bin.tar.gz
popd
bin/startup.sh
popd

tail -100f "$tomcat/logs/catalina.out"

#log=`ls -1 "$tomcat"/logs/valve3.* | sort | head -1`
#tail -100f "$log"

