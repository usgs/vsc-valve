#!/bin/bash

# Start Selenium Remote Control instances - see README.txt in this directory

count=${1-1}
cd /home/andrew/projects/isti/pkg/selenium-grid-1.0.6/

port=5555
while [ "$count" -gt 0 ]
do
  ant \
    -Dport=$port \
    -Dhost=quiet \
    -DhubURL=http://quiet:4444 \
    -Denvironment='Firefox on Linux' \
    launch-remote-control &
  let "count=$count-1"
  let "port=$port+1"
done
