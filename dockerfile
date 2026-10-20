
FROM eclipse-temurin:8-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY habit.java .
COPY fonts/ ./fonts/

RUN javac habit.java

CMD ["xvfb-run", "-a", "java", "habit"]
