FROM payara/micro:6.2025.8-jdk21
COPY --chown=payara:payara target/payara-micro.jar $DEPLOY_DIR/../payara-micro.jar
COPY target/*.war $DEPLOY_DIR
CMD [ "--deploymentDir" , "/opt/payara/deployments", "--contextroot", "/", "--noHazelcast"]