# Important Note: Git Repository Must Not Be Empty

## Spring Cloud Config Server and an Empty Git Repository

When using Spring Cloud Config Server with a Git backend, the remote Git repository **must contain at least one committed file**. A completely empty repository (a repository with no commits) is **not supported**.

Example configuration:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/<username>/<config-repo>.git
          default-label: main
```

## What Happens Internally?

When the Config Server needs to access configuration (either during startup when `clone-on-start=true`, or when a client requests configuration, or when the `/actuator/health` endpoint performs a health check), it performs the following steps:

```
Config Server Starts
        │
        ▼
Creates a Temporary Directory
        │
        ▼
/tmp/config-repo-xxxxxxxx
        │
        ▼
Attempts to Clone the Git Repository
        │
        ▼
Checks Out the Configured Branch (main)
        │
        ▼
Reads Configuration Files
        │
        ▼
Returns Configuration to Client Applications
```

## What Happens with an Empty Repository?

If the Git repository has **no commits**, the process fails.

```
Remote Repository
        │
        ▼
No Commits
        │
        ▼
No Files
        │
        ▼
No Valid Branch Reference
        │
        ▼
Clone Cannot Complete Successfully
        │
        ▼
Temporary Directory is Created
        │
        ▼
Directory Remains Empty
```

For example:

```
/tmp/config-repo-16773442275255887484
```

Contents:

```
.
..
```

Notice that:

* There is **no `.git` directory**.
* There are **no configuration files**.
* The repository was **not cloned successfully**.

## Why Does the Error Mention `master`?

A common exception is:

```
org.eclipse.jgit.api.errors.RefNotFoundException:
Ref master cannot be resolved
```

This does **not necessarily mean** that your GitHub repository contains a `master` branch.

The actual problem is that **the repository has no commits**, so JGit cannot resolve any branch reference. During its internal checkout process, it reports that the requested reference cannot be resolved because there is no valid commit to check out.

## Why Does `/actuator/health` Trigger This?

The Health endpoint does more than simply check whether the application is running.

```
GET /actuator/health
        │
        ▼
Config Server Health Indicator
        │
        ▼
Attempts to Access Git Repository
        │
        ▼
Clone / Fetch / Checkout
        │
        ▼
Repository Has No Commits
        │
        ▼
Health Check Fails
```

This is why the application may start successfully, but accessing `/actuator/health` can produce a Git-related exception.

## How to Fix the Problem

Create at least one configuration file and commit it to the repository.

Example:

```
application.yml
```

Contents:

```yaml
message: Hello Config Server
```

Commit and push the file:

```bash
git add .
git commit -m "Initial configuration"
git push origin main
```

After pushing the initial commit:

1. Stop the Config Server.
2. Delete the cached repository (if present):

```bash
rm -rf /tmp/config-repo-*
```

3. Restart the Config Server.

The repository will now clone successfully, configuration files will be available, and endpoints such as `/actuator/health` will work correctly.

## Best Practice

A Config Server Git repository should **never be completely empty**.

Even if configuration is not yet finalized, create an initial commit containing at least one configuration file.

Example:

```
config-repo/
├── application.yml
├── user-service.yml
├── order-service.yml
└── payment-service.yml
```

Having at least one committed file ensures that:

* The repository can be cloned successfully.
* The configured branch can be checked out.
* Spring Cloud Config Server can read configuration.
* Health checks complete successfully.
* Client microservices can retrieve configuration from the Config Server.
