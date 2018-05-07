# OAR-DIST-SERVICE

This is the distribution service project responsible of managing the distribution files of the oar project.

## Installation
The project is a maven based java project. To build: 
`mvn clean install`

## Layout
```
oar-config/                    							--> main folder
	oar-config-server/		   							--> folder of the configuration server module
  		src/											--> application sources folder
  			main/java/             			    		--> java classes
  				gov/nist/oar/ds/             			--> main java classes folder
  					config/             				--> config classes 
  				 	controller/             			--> rest api classes 
  				 	exception/             				--> config classes 
  				 	s3/             					--> s3 utlities and services classes
  				 	service/             				--> interfaces of the services
  				 		impl/  							--> implementation of the services interface classes
  				 	util/             					--> utilities classes
    		main/resources/								--> resources folder
      			application.yml							--> configuration file of the project
      			logback.xml								--> logging configuration file of the project 
  			/test/java/             			    	--> unit and integration test classes
  				gov/nist/oar/ds/             			--> main test classes folder
  					unit/             					--> junit classes
  				 		controller/             		--> junit classes of the controllers
  				 		service/             			--> junit classes of the services 
   			/test/resources/							--> unit and integration test resources 
    	pom.xml 										--> maven pom file
```

## Developers Notes
PreservationBag Service:
This service connects to long term data storage and returns either given file, package(bag) or the information  about the bag.
Long term storage can be on Amazon S3 or local or ec2 filesystem. To help enable Long term storage in given instance,
Developer can set property "aws" or "FileStorage" in addition to "prod","dev","test" properties in spring properties.
E.g. spring.profiles.active: dev, FileStorage or
     spring.profiles.active: prod,aws


## Contributing

1. Fork it!
2. Create your feature branch: `git checkout -b my-new-feature`
3. Commit your changes: `git commit -am 'Add some feature'`
4. Push to the branch: `git push origin my-new-feature`
5. Submit a pull request :D

## History

TODO: Write history

## Credits

TODO: Write credits

## License

TODO: Write license

## Disclaimer
```
NIST-developed software is provided by NIST as a public service. You may use, copy and distribute copies of the software in any medium, provided that you keep intact this entire notice. You may improve, modify and create derivative works of the software or any portion of the software, and you may copy and distribute such modifications or works. Modified works should carry a notice stating that you changed the software and should note the date and nature of any such change. Please explicitly acknowledge the National Institute of Standards and Technology as the source of the software.
NIST-developed software is expressly provided "AS IS." NIST MAKES NO WARRANTY OF ANY KIND, EXPRESS, IMPLIED, IN FACT OR ARISING BY OPERATION OF LAW, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT AND DATA ACCURACY. NIST NEITHER REPRESENTS NOR WARRANTS THAT THE OPERATION OF THE SOFTWARE WILL BE UNINTERRUPTED OR ERROR-FREE, OR THAT ANY DEFECTS WILL BE CORRECTED. NIST DOES NOT WARRANT OR MAKE ANY REPRESENTATIONS REGARDING THE USE OF THE SOFTWARE OR THE RESULTS THEREOF, INCLUDING BUT NOT LIMITED TO THE CORRECTNESS, ACCURACY, RELIABILITY, OR USEFULNESS OF THE SOFTWARE.
You are solely responsible for determining the appropriateness of using and distributing the software and you assume all risks associated with its use, including but not limited to the risks and costs of program errors, compliance with applicable laws, damage to or loss of data, programs or equipment, and the unavailability or interruption of operation. This software is not intended to be used in any situation where a failure could cause risk of injury or damage to property. The software developed by NIST employees is not subject to copyright protection within the United States.
```

