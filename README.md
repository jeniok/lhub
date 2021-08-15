How to run from Intellij?
---------------------------

1)Define new run configuration:
  Main class: io.vertx.core.Launcher
  Program arguments: run com.interview.intuit.lhub.LearningHubVerticle -conf C:\Users\User\dev\lhub\src\main\conf\app-conf.json

2) Prior to running need to run KeyCloak identity and access management server:
docker run   -it   --name vertx-keycloak   --rm   -e KEYCLOAK_USER=admin   -e KEYCLOAK_PASSWORD=admin   -e KEYCLOAK_IMPORT=/tmp/vertx-realm.json   -v $PWD/vertx-realm.json:/tmp/vertx-realm.json   -v $PWD/data:/opt/jboss/keycloak/standalone/data   -p 8080:8080   quay.io/keycloak/keycloak:11.0.2

3) Click run

How to run from maven
-----------------------
