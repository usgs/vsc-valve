#!/bin/bash

# A simple script to deploy VDX, useful in testing.

# Set Tomcat location here

if [ -e ../vdx ]
then
  rm -fr ../vdx
fi

pushd ..
tar xvfz ../VDX/dist/vdx-bin.tar.gz
cd vdx
chmod +x vdx.sh
./vdx.sh
