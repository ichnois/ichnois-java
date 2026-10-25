# ichnois-android-navigation3

[Navigation 3](https://developer.android.com/guide/navigation/navigation-3) instrumentation for the IchnoisCore Android SDK.

Use `IchnoisCoreNavEffect` alongside your `NavDisplay` to record navigation transactions, breadcrumbs, screen names, and back stack context as
users move around your app. Configure the data you want captured via `IchnoisCoreNavOptions`.

Because Nav3 back stacks can have arbitrary key types, host apps need to provide `IchnoisCoreNavEffect` with a mapping between their back stack
keys and the data IchnoisCore should display for each (see `BackStackEntryMapper`).

See the [Navigation for Android docs](https://docs.sentry.io/platforms/android/integrations/navigation/) for additional details, including
configuration, supported setups, and limitations.
