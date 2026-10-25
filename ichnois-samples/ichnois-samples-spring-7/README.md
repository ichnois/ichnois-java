# IchnoisCore Sample Spring 6.0+

Sample application showing how to use IchnoisCore with [Spring](http://spring.io/) from version `6.0` onwards.

## How to run? 

To see events triggered in this sample application in your IchnoisCore dashboard, go to `src/main/java/io/ichnois/samples/spring/jakarta/IchnoisCoreConfig.java` and replace the test DSN with your own DSN. 

Then, execute a command from the module directory:

```
../../gradlew appRun
```

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/ichnois-samples-spring-jakarta/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```
