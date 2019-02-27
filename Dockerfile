#Download base image ubuntu 16.04
FROM ubuntu:16.04

# Update Software repository
RUN DEBIAN_FRONTEND=noninteractive apt-get update -y && apt-get upgrade -y && apt-get -y install openjdk-8-jdk wget

# Install Leiningen
RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN chmod u+x lein
RUN mv lein /usr/local/bin/
ENV LEIN_ROOT=1
RUN lein

# Copy project
ADD ./project.clj /app/

RUN cd /app && lein deps

# Copy project
ADD ./ /app

RUN chmod u+x /app/start.sh

CMD ["/app/start.sh"]

EXPOSE 8080