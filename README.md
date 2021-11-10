# Installation instructions

(Linux) Instructions to build and run the **RCR-Converter**

## 1. Required Software

* Git
* OpenJDK Java 17
* Gradle

## 2. Compile the project

```bash

$ ./gradlew clean

$ ./gradlew completeBuild
```

## 3. Configure the JSOM with RCR-Converter

Copy ``lib/jscience-4.3.1.jar`` and ``dist/rcr-converter.jar`` into the _JOSM-plug-in_ directory (``~/.josm/plugins``).

Start JOSM

```bash

$ java -jar lib/josm-8800.jar
```

Go to **Edit** -> **Preferences** -> **Plugins** and enable the ``rcr-converter`` plugin, then restart JOSM.

Done! Everything should work.
