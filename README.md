# Learning Hub
Learning Hub is a learning portal for company employees, where they can create learning paths, add topics, rate topics.

### Run KeyCloak
KeyCloak is single sign-on identity and access management service. Keycloak contains the following predefined user `tester:test`.
```bash
docker run   -it   --name vertx-keycloak   --rm   -e KEYCLOAK_USER=admin   -e KEYCLOAK_PASSWORD=admin   -e KEYCLOAK_IMPORT=/tmp/vertx-realm.json   -v $PWD/src/main/conf/vertx-realm.json:/tmp/vertx-realm.json   -v $PWD/data:/opt/jboss/keycloak/standalone/data   -p 8080:8080   quay.io/keycloak/keycloak:11.0.2
```

### Run MongoDB
- This project uses MongoDb. Run locally or on docker
- Modify connection string in /src/main/conf/app-conf.json
```
  "mongo.config": {
    "connection_string": "mongodb://localhost:27017/?readPreference=primary&directConnection=true&ssl=false",
    "db_name": "learning_hub"
  }
  ```

### Run from Intellij
- Add new Application configuration
  - Main class: io.vertx.core.Launcher
  - Program arguments: run com.interview.intuit.lhub.LearningHubVerticle -conf C:\Users\User\dev\lhub\src\main\conf\app-conf.json
- Click run


### Run from Maven
- ```mvn clean package```
- ```java -jar target/*.jar```
