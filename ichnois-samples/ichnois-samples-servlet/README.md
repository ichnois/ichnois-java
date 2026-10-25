# IchnoisCore Sample Servlet

Sample application showing how to use IchnoisCore with a non-Spring Java servlet application.

## How to build?

From the module directory execute a command:

```
../../gradlew build
```

## How to run?

To see events triggered in this sample application in your IchnoisCore dashboard, go to `src/main/java/io/ichnois/samples/servlet/IchnoisCoreInitializer.java` and replace the test DSN with your own DSN. 

Deploy `build/libs/ichnois-samples-servlet-0.0.1-SNAPSHOT.war` to the servlet container or application server of your choice.
