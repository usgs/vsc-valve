REM A small script to restart tomcat running as a service on Windows
REM Intended to be called as a scheduled task
REM Author: Tom Parker

set APACHE_SERVICE="Apache Tomcat"

net stop %APACHE_SERVICE%

net start %APACHE_SERVICE%