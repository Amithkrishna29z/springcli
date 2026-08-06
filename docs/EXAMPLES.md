# Terminal Output Examples

Captured from real runs against `https://start.spring.io`.

## `springcli --help`

```
 ___              _              ___ _    ___
/ __|_ __ _ _(_)_ _  __ _ / __| |  |_ _|
\__ \ '_ \ '_| | ' \/ _` | (__| |__ | |
|___/ .__/_| |_|_||_\__, |\___|____|___|
    |_|             |___/

Usage: springcli [-hV] [--no-banner] [COMMAND]
Scaffold Spring Boot projects using the Spring Initializr API.
  -h, --help        Show this help message and exit.
      --no-banner   Suppress the ASCII banner.
  -V, --version     Print version information and exit.
Commands:
  new      Create a new Spring Boot project (interactive by default).
  search   Search available Spring Boot dependencies.
  list     List all available dependencies grouped by category.
  version  Show the springcli version.
  doctor   Check your environment (Java, Maven, Git).
```

## `springcli search web`

```
Fetching Spring metadata...

Results for "web":

✔ Spring Web  [web]
    Build web, including RESTful, applications using Spring MVC. Uses Apache Tomcat as the default embedded container.
✔ Spring Reactive Web  [webflux]
    Build reactive web applications with Spring WebFlux and Netty.
✔ Spring Web Services  [web-services]
    Facilitates contract-first SOAP development using Spring WS.
...
```

## `springcli new my-app --yes --group com.acme --deps web,data-jpa --java-version 21`

```
Fetching Spring metadata...
Selecting dependencies...
Downloading project...
Extracting files...
✔ Project created successfully at ./my-app
```

Resulting layout:

```
my-app/
├── .gitignore
├── HELP.md
├── mvnw / mvnw.cmd
├── pom.xml                 (groupId com.acme, java.version 21, starters: web + data-jpa)
└── src/
    ├── main/java/com/acme/myapp/MyAppApplication.java
    ├── main/resources/application.properties
    └── test/java/com/acme/myapp/MyAppApplicationTests.java
```

## `springcli new` (interactive wizard)

```
Configure your Spring Boot project

Project name (demo): my-app
Group ID (com.example): com.acme
Artifact ID (my-app):
Package name (com.acme.myapp):
Description (Demo project for Spring Boot):

Build tool:
   1) Gradle - Groovy
   2) Gradle - Kotlin  (default)
   3) Maven
Select 1-3 (2): 3

Language:
   1) Java  (default)
   2) Kotlin
   3) Groovy
Select 1-3 (1):

... (packaging, Spring Boot version, Java version) ...

Dependencies
Type a search term to find dependencies, select by number, then press Enter on an empty line to finish.

Search dependency (empty to finish): web

   1) Spring Web - Build web, including RESTful, applications using Spring MVC...
   2) Spring Reactive Web - Build reactive web applications with Spring WebFlux...
Add which numbers? (comma-separated, empty to skip): 1
✔ Added Spring Web

Search dependency (empty to finish):

Project summary
  Name:        my-app
  Group:       com.acme
  Artifact:    my-app
  Package:     com.acme.myapp
  Build/Type:  maven-project
  Language:    java
  Packaging:   jar
  Boot:        3.3.2
  Java:        21
  Dependencies: web

Generate this project? [Y/n]: y
Downloading project...
Extracting files...
✔ Project created successfully at ./my-app
```

## `springcli doctor`

```
Environment check

✔ Java: java version "21.0.10" 2026-01-20 LTS
✔ Maven: Apache Maven 3.9.10
✔ Git: git version 2.45.0

✔ Core tooling looks good.
```
