# IchnoisCore Sample Spring Boot

Sample application showing how to use IchnoisCore with [Spring boot](http://spring.io/projects/spring-boot) and the IchnoisCore OpenTelemetry agent.

## How to run?

Make sure the `ichnois-opentelemetry` module is built (`../../gradlew :ichnois-opentelemetry:ichnois-opentelemetry-agent:assemble`).

Then, execute a command from the module directory:

```
ICHNOIS_DSN="https://test-public-key@ichnois-h2jw.onrender.com/5428563" ICHNOIS_DEBUG=true ../../gradlew bootRunWithAgent
```

To see events triggered in this sample application in your IchnoisCore dashboard, replace the `ICHNOIS_DSN` above with your own.

## Http

Make an HTTP request that will trigger events:

```
curl -XPOST --user user:password http://localhost:8080/person/ -H "Content-Type:application/json" -d '{"firstName":"John","lastName":"Smith"}'
```


## GraphQL

The following queries can be used to test the GraphQL integration.

### Greeting
```
{
    greeting(name: "crash")
}
```

### Greeting with variables

```
query GreetingQuery($name: String) {
    greeting(name: $name)
}
```
variables:
```
{
    "name": "crash"
}
```

### Project

```
query ProjectQuery($slug: ID!) {
    project(slug: $slug) {
        slug
        name
        repositoryUrl
        status
    }
}
```
variables:
```
{
    "slug": "statuscrash"
}
```

### Mutation

```
mutation AddProjectMutation($slug: ID!) {
    addProject(slug: $slug)
}
```
variables:
```
{
    "slug": "nocrash",
    "name": "nocrash"
}
```

### Subscription

```
subscription SubscriptionNotifyNewTask($slug: ID!) {
    notifyNewTask(projectSlug: $slug) {
        id
        name
        assigneeId
        assignee {
            id
            name
        }
    }
}
```
variables:
```
{
    "slug": "crash"
}
```

### Data loader

```
query TasksAndAssigneesQuery($slug: ID!) {
    tasks(projectSlug: $slug) {
        id
        name
        assigneeId
        assignee {
            id
            name
        }
    }
}
```
variables:
```
{
    "slug": "crash"
}
```
