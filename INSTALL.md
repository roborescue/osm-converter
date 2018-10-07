# Installation instructions

## Required Software

* JOSM revision 6950 (http://josm.openstreetmap.de)
* RoboCup Rescue Simulation Software (https://github.com/roborescue/rcrs-server)
* Open JDK 1.7
* Ant

## Building

Edit the ``build.properties`` file
  josm=<Path to JOSM jar file>
  roborescue.dir=<Path to the rcrs-server root directory>

Run ``ant``to compile the plug-in and create the file ``dist/rcr-converter.jar`` file

## Installation

Copy ``jscience.jar`` and ``rcr-converter.jar`` into the JOSM-plug-in directory
(``~/.josm/plugins`` on Linux).

Start JOSM, go to Settings -> Plugins and enable the ``RCR-converter`` plugin, then restart JOSM.

Done! Everything should work.
