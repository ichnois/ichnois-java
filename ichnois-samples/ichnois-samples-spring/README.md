# IchnoisCore Sample Spring

Sample application showing how to use IchnoisCore with [Spring](http://spring.io/).

## How to run? 

To see events triggered in this sample application in your IchnoisCore dashboard, go to `src/main/java/io/ichnois/samples/spring/IchnoisCoreConfig.java` and replace the test DSN with your own DSN. 

Then, execute a command from the module directory:

```
../../gradlew appRun
```

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/ichnois-samples-spring/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```
