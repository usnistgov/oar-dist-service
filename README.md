# OAR Data Distribution Service (oar-dist-service)

This repository provides the implementation of the data distribution
service, which is responsible for delivering data products from the
NIST-OAR Public Data Repository (PDR).  Delivering data through a
service (as opposed to delivering directly from files on disk) allows
us to provide consistent and long-lived URLs to data products while
hiding the actual location of files in storage.  In particular, files
can be cached in a number locations for fast access.  The service
capabilities are provided via a REST Web interface.  The
implementation allows for the data products to be stored either on
local disk or in Amazon Web Services (AWS) S3.  

This service is implemented in Java and uses the
[Spring Boot Framework](https://spring.io/) to implement the web
interface. 

## Contents

```
docker/    --> Docker containers for building the software and running
                tests (without requiring prerequites to be installed).
src/       --> Java source code and resources, organized as a Maven
                project.
scripts/   --> Scripts for building, testing, and running the software.
oar-build/ --> Support libaries for building and testing (not for
                direct execution).
```

## Prerequisites

As a Maven project, this repository relies on Maven to fetch and manage its
library dependencies; thus, the only prerequisites that must be
installed by the user are:

* Java v1.8
* Maven 3.0.X or later
* npm 6.2.0 or later (required for unit testing)

This project has an optional dependency on Docker: the container
infrastructure.  If Docker is installed, one can build and test the
software without Java or Maven installed.

## Building and Running the Service

### Building the Executable Jar

The primary artifact provided by this repository is the executable JAR
file, `oar-dist-service.jar`, which can be used to launch the
service.  

#### Simple Building with `makedist`

As a standard OAR repository, the JAR file can be built
via the `makedist` script:

```
scripts/makedist
```

A successful build will save the JAR file as
`target/oar-dist-service.jar`.  This script will also copy the JAR
file to the `dist` subdirectory, adding a version number to the JAR
file's name.  It also creates in the `dist` directory a file
called `target/oar-dist-service-*_deps.json`, which inventories all of
the dependencies built into the JAR.

Building with `makedist` requires that the above-listed prerequisites
are installed and available to one's shell.

#### Building Natively with Maven

If Java and Maven are installed (see Prerequisites above), one can use
Maven directly to build the software.  To build the JAR file from
scratch, type:

```
mvn clean package
```

This will execute the unit tests as part of the process.  To skip
running the tests (and save considerable time), type instead:

```
mvn clean package -DskipTests
```

A successful build saves the JAR file as `target/oar-dist-service.jar`.  

#### Building with Docker

If you do not have Java and Maven installed, but you do have Docker,
you can use Docker to build JAR file.  Type:

```
scripts/makedist.docker
```

Just as with `makedist`, the JAR file will be saved as
`target/oar-dist-service.jar`.

### Building the Java API Documentation

Use Maven to generate the Java API documentation (JavaDoc):

```
mvn javadoc:javadoc
```

To view the documentation, use a browser to access
`target/site/aipdocs/index.html`.

## Running Unit Tests

### Simple Testing with `testall`

As a standard OAR repository, the unit tests can be executed
via the `testall` script:

```
scripts/testall
```

In addition to results being printed to the screen, detailed logs can
be found under `target/surefire-reports`.

Testing with `testall` requires that the above-listed prerequisites
are installed and available to one's shell.

### Testing with Maven

To test natively with Maven, type:

```
mvn test
```

To run just the tests for a particular class, use the `-Dtest` option
to specify the class:

```
mvn -Dtest=VersionControllerTest test
```

This generates the same reports as described above.  

### Testing with Docker

If you do not have Java, Maven, and npm installed, but do have Docker,
you can use Docker to run the unit tests.  Type:

```
scripts/testall.docker
```

## Launching the Service

The executable JAR includes its own Tomcat server, so no separate
server is needed to run.  To launch, use the `run_service.sh` script:

```
scripts/run_service.sh
```

The service can be configured via command-line arguments; provide the
`-h` option to see what options are available:

```
scripts/run_service.sh -h
```

## Logging
For each download request submitted to the service, information is captured about an individual file or group/bundle of files requested for download. 
There are three types of requests:

### Single file download
This request serves a file which is part of a collection associated with a particular data record/data publication. 
Single file download is served by using a long term URL.
For this request we updated the service logs to print/output the record identifies, filename/filepath, its size, and the status of the download (success, failure or canceled by user).

### BundlePlan 
Once a bundleplan request is submitted for bundle/collection of files, it checks the size of each file and creates bundle suggestions for download based on the size limitation per bundle. 
This request does not return actual data but returns the plan which the user should use to download data to avoid issues getting large data sizes. 
For this type of request, we log the details of each file requested per bundle with a common request identifier.

### Bundle Download
This request actually serves data and delivers files as per request. 
In this request, a collection of files called a bundle is requested with the unique request identifier. 
The logs now collection information about each request and print request identifier, bundle name, files and sizes etc. 
It also produces information about whether the bundle is successfully downloaded, partially downloaded, or the bundle download failed because all files failed.

Note for developer ::
Keep/maintain the formatting for these log messages as there are log parsing scripts written in python in a separate project to get the request related information from the log file and load in readable tables/collections in the database.

## Disclaimer

NIST-developed software is provided by NIST as a public service. You
may use, copy and distribute copies of the software in any medium,
provided that you keep intact this entire notice. You may improve,
modify and create derivative works of the software or any portion of
the software, and you may copy and distribute such modifications or
works. Modified works should carry a notice stating that you changed
the software and should note the date and nature of any such
change. Please explicitly acknowledge the National Institute of
Standards and Technology as the source of the software.

NIST-developed software is expressly provided "AS IS." NIST MAKES NO
WARRANTY OF ANY KIND, EXPRESS, IMPLIED, IN FACT OR ARISING BY
OPERATION OF LAW, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT
AND DATA ACCURACY. NIST NEITHER REPRESENTS NOR WARRANTS THAT THE
OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR-FREE, OR THAT
ANY DEFECTS WILL BE CORRECTED. NIST DOES NOT WARRANT OR MAKE ANY
REPRESENTATIONS REGARDING THE USE OF THE SOFTWARE OR THE RESULTS
THEREOF, INCLUDING BUT NOT LIMITED TO THE CORRECTNESS, ACCURACY,
RELIABILITY, OR USEFULNESS OF THE SOFTWARE.

You are solely responsible for determining the appropriateness of
using and distributing the software and you assume all risks
associated with its use, including but not limited to the risks and
costs of program errors, compliance with applicable laws, damage to or
loss of data, programs or equipment, and the unavailability or
interruption of operation. This software is not intended to be used in
any situation where a failure could cause risk of injury or damage to
property. The software developed by NIST employees is not subject to
copyright protection within the United States. 

