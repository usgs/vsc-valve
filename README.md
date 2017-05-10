# Valve
---
Valve is a web interface to the databases created and maintained via the [vsc-vdx](https://github.com/usgs/vsc-vdx) project. It provides a set of data charting and export capabilities designed explicitly for Volcano monitoring datasets.

## Requirements
---
1. java 1.7+
2. Apache Tomcat 6+
3. Access to a MySQL database instance containing vsc-vdx datasets.

## Installation
---
1. Copy the generated WAR file from the Valve3Web/target directory to your Apache Tomcat server location. If you have Tomcat set to automatically expand WAR files, restart the service and move on to the next step. Otherwise, run:
    ```
    $ mkdir valve3
    $ cd valve3 && cp Valve3.war
    $ unzip Valve3.war
    ```
2. Your Tomcat server.xml file will need an entry that looks something like the following:
    ```
    <Context path="/valve3" docBase="/path/to/webapps/valve3" privileged="false" reloadable="true" crossContext="false"></Context>
    ```
3. Make sure that the Tomcat user owns the data/ and img/ directories. If not, run:
    ```
    $ chown tomcat:www data/ img/
    ```
4. Configure your instance of valve3:
   1. The root directory of the app contains three text files that need to be edited. By default, the Valve application ships with maps and filters for Hawaii. New maps can be placed in the /maps directory and can be configured via the images.txt and filters.txt files. Do so as necessary for your situation.
   2. Next up is the dist-data.config file in /WEB-INF/config. This file should be renamed 'data.config' and should contain everything needed to connect to the instance of vsc-vdx that will be used as well as definte the menu layout within Valve itself. The sample file should be pretty self-expanatory.
   3. Finally, rename dist-valve3.config to valve3.config and edit it. This file contains a bunch of configuration settings that are used by Valve.
5. Restart Tomcat and you should be up and running.
