# Migrating from `ichnois-java` to `ichnois-android`

#### Docs

Migration page from [ichnois-android 1.x to ichnois-android 2.x](https://docs.sentry.io/platforms/android/migrate).

#### Installation

_Old_:

```
IchnoisCore.init("___PUBLIC_DSN___", new AndroidIchnoisCoreClientFactory(context));
```

_New_:

The SDK is able to initialize automatically. The only thing required is to make the DSN available.
*`ichnois.properties` has been discontinued and configurations on this SDK version is over `AndroidManifest.xml` or code.*

```xml
<meta-data android:name="io.ichnois.dsn" android:value="___PUBLIC_DSN___" />
```
_Or:_

*If you want to call `IchnoisCoreAndroid.init(...)` by yourself, first of all you need to disable the `auto-init` feature.*

```xml
<meta-data android:name="io.ichnois.auto-init" android:value="false" />
```

```
IchnoisCoreAndroid.init(context, options -> {
  options.setDsn("___PUBLIC_DSN___");    
});
```

#### Set tag

_Old_:

```
IchnoisCore.getContext().addTag("tagName", "tagValue");
```

_New_:

```
IchnoisCore.setTag("tagName", "tagValue");
```

#### Capture custom exception

_Old_:

```
try {
  int x = 1 / 0;
} catch (Exception e) {
  IchnoisCore.capture(e);
}
```

_New_:

```
try {
  int x = 1 / 0;
} catch (Exception e) {
  IchnoisCore.captureException(e);
}
```

#### Capture a custom event

_Old_:

```
Exception exception = new Exception("custom error");
EventBuilder eventBuilder = new EventBuilder()
  .withLevel(Event.Level.ERROR)
  .withIchnoisCoreInterface(new ExceptionInterface(exception));
IchnoisCore.capture(eventBuilder);
```

_New_:

```
Exception exception = new Exception("custom error");
IchnoisCoreEvent event = new IchnoisCoreEvent(exception);
event.setLevel(IchnoisCoreLevel.ERROR);
IchnoisCore.captureEvent(event);
```

#### Capture a message

_Old_:

```
IchnoisCore.capture("This is a test");
```

_New_:

```
IchnoisCore.captureMessage("This is a test"); // IchnoisCoreLevel.INFO by default
IchnoisCore.captureMessage("This is a test", IchnoisCoreLevel.WARNING); // or specific level
```

#### Breadcrumbs

_Old_:

```
IchnoisCore.getContext().recordBreadcrumb(
  new BreadcrumbBuilder().setMessage("User made an action").build()
);
```

_New_:

```
IchnoisCore.addBreadcrumb("User made an action");
```

#### User

_Old_:

```
IchnoisCore.getContext().setUser(
  new UserBuilder().setEmail("hello@ichnois.io").build()
);
```

_New_:

```
User user = new User();
user.setEmail("hello@ichnois.io");
IchnoisCore.setUser(user);
```

#### Set extra

_Old_:

```
IchnoisCore.getContext().addExtra("extra", "thing");
```

_New_:

```
IchnoisCore.setExtra("extra", "thing");
```
