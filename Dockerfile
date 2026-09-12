# Image de l'API.
#
# Deux étapes : la première compile avec Maven, la seconde ne garde que le
# nécessaire pour exécuter. Le JDK, le dépôt Maven et les sources — plusieurs
# centaines de mégaoctets — ne partent pas en production.

# ---- Compilation ---------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /chantier

# Les dépendances sont résolues avant que les sources n'entrent dans l'image :
# tant que le pom ne change pas, Docker réutilise cette couche et la
# compilation ne retélécharge rien.
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
# Les tests tournent déjà à part, et deux d'entre eux réclament un démon
# Docker — que l'on n'a pas à l'intérieur d'une image en construction.
RUN mvn -q -B -DskipTests package

# ---- Exécution -----------------------------------------------------
# Jammy et non Alpine : Temurin ne publie pas d'image Alpine pour ARM, et la
# construction échouait sur un Mac récent tout en réussissant sur un serveur
# x86. Celle-ci couvre les deux architectures.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /application

# Un processus applicatif n'a aucune raison d'être root, même en conteneur.
RUN groupadd --system booking && useradd --system --gid booking booking

# Le répertoire des photos existe dans l'image, et appartient au compte qui
# fait tourner l'application.
#
# Ce n'est pas de la politesse : Docker recopie le propriétaire du répertoire
# de l'image dans un volume nommé la première fois qu'il le monte. Sans ce
# mkdir, le volume naît appartenant à root, l'application tourne sous
# « booking », et le premier envoi de photo échoue en AccessDeniedException —
# une erreur 500 que rien ne laissait prévoir en développement, où l'écriture
# se fait dans un répertoire local.
#
# Sur un volume déjà créé sans ce répertoire, la correction ne s'applique pas
# d'elle-même : il faut alors un « docker run --rm -v <volume>:/p alpine chown
# -R 999:999 /p », une seule fois.
RUN mkdir -p /var/lib/booking/photos && chown -R booking:booking /var/lib/booking
COPY --from=build /chantier/target/*.jar application.jar
USER booking

ENV TZ=Africa/Casablanca \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
