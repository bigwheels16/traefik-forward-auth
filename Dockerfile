FROM clojure:lein-2.9.1-alpine

WORKDIR /app

COPY project.clj project.clj
RUN lein deps

EXPOSE 80

CMD ["lein", "ring", "server-headless"]
