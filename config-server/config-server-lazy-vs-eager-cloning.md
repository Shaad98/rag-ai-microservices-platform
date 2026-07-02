# Why Does the Config Repository Clone Only After Accessing `/actuator/health`?

## Configuration

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/<username>/<config-repo>.git
          clone-on-start: false
```

## Understanding `clone-on-start`

The property:

```yaml
clone-on-start: false
```

means:

> **Do not clone the Git repository when the Config Server starts. Clone it only when it is actually needed.**

This behavior is called **lazy cloning**.

---

# What Happens During Application Startup?

When the Config Server starts:

```text
Config Server Starts
        │
        ▼
Embedded Tomcat Starts
        │
        ▼
Spring Context Loads
        │
        ▼
Config Server Initializes
        │
        ▼
Git Repository Information is Stored
        │
        ▼
Repository is NOT cloned
```

At this point:

```
Remote Git Repository
        │
        ▼
(Not Accessed Yet)
```

Therefore, if you check:

```bash
cd /tmp
ls
```

you will **not** see any `config-repo-*` directory.

This is expected behavior.

---

# What Happens When `/actuator/health` Is Requested?

When someone accesses:

```text
GET /actuator/health
```

Spring Boot performs more than a simple health check.

The sequence is:

```text
Browser
    │
    ▼
/actuator/health
    │
    ▼
Health Endpoint
    │
    ▼
Config Server Health Indicator
    │
    ▼
Checks Git Repository Status
    │
    ▼
Repository Needed
    │
    ▼
Creates /tmp/config-repo-xxxxxxxx
    │
    ▼
Clones Git Repository
    │
    ▼
Checks Out Branch
    │
    ▼
Reads Configuration Files
    │
    ▼
Returns Health Response
```

This is why, immediately after calling `/actuator/health`, you suddenly see:

```text
/tmp/config-repo-2419024494000721926
```

---

# Why Was the Directory Empty Initially?

Initially your Git repository contained **no commits**.

Spring created the temporary directory:

```text
/tmp/config-repo-16773442275255887484
```

However, because the repository was empty:

```
No commits
        │
        ▼
Nothing to checkout
        │
        ▼
Clone cannot complete
        │
        ▼
Directory remains empty
```

Contents:

```text
.
..
```

No `.git` directory existed because the clone operation never completed successfully.

---

# After Adding `application.yaml`

After creating a file in the repository and pushing the first commit:

```text
application.yaml
```

the process became:

```text
/actuator/health
        │
        ▼
Config Server accesses Git
        │
        ▼
Clone succeeds
        │
        ▼
Checkout main branch
        │
        ▼
Repository copied into /tmp
```

Now:

```bash
ls -a
```

shows:

```text
.
..
.git
application.yaml
```

This proves that the repository has been cloned successfully.

---

# Why Didn't It Clone During Startup?

Because:

```yaml
clone-on-start: false
```

With this configuration:

```
Application Starts
        │
        ▼
Do NOT clone repository
        │
        ▼
Wait until first request
```

Only when an endpoint requires the repository does cloning occur.

Examples include:

* `/actuator/health`
* `/{application}/{profile}`
* `/{application}/{profile}/{label}`
* `/actuator/refresh` (when applicable)

---

# What If `clone-on-start=true`?

If you configure:

```yaml
clone-on-start: true
```

then the startup sequence changes:

```text
Config Server Starts
        │
        ▼
Immediately Creates /tmp/config-repo-xxxxxxxx
        │
        ▼
Immediately Clones Git Repository
        │
        ▼
Checks Out Branch
        │
        ▼
Startup Completes
```

Before sending any request, you can already see:

```bash
cd /tmp
ls
```

Output:

```text
config-repo-2419024494000721926
```

and:

```bash
cd config-repo-2419024494000721926
ls -a
```

Output:

```text
.
..
.git
application.yaml
```

---

# Summary

| `clone-on-start` Value | When Does Git Clone Occur?                                     |
| ---------------------- | -------------------------------------------------------------- |
| `false`                | On the first request that needs the repository (lazy cloning). |
| `true`                 | During Config Server startup (eager cloning).                  |

Your experiment demonstrated this perfectly:

1. Started Config Server with `clone-on-start=false`.
2. Checked `/tmp` → No cloned repository.
3. Accessed `/actuator/health`.
4. Config Server needed the Git repository.
5. Spring created `/tmp/config-repo-*`.
6. Initially, the repository was empty, so cloning failed and the directory remained empty.
7. After adding and committing `application.yaml`, the next request cloned the repository successfully.
8. The temporary directory now contained both `.git` and `application.yaml`.

This is exactly how Spring Cloud Config Server's lazy cloning mechanism is designed to work.
