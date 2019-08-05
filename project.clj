(defproject traefik-forward-auth "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.4.0"]
                 ;[org.clojure/java.jdbc "0.6.1"]
                 ;[mysql/mysql-connector-java "5.1.41"]
                 [org.clojure/data.json "0.2.6"]
                 ;[clj-time "0.14.3"]
                 [clj-http "3.7.0"]
                 ;[eu.maxschuster/dataurl "2.0.0"]
                 ;[digest "1.4.8"]
                 ;[crypto-password "0.2.0"]
                 [com.auth0/java-jwt "3.8.0"]
                 [com.auth0/jwks-rsa "0.8.1"] ; https://github.com/auth0/jwks-rsa-java
                 [com.fasterxml.jackson.core/jackson-core "2.9.8"]
                 ]

  :repl-options {:init-ns com.jkbff.traefik-forward-auth.handler}
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler com.jkbff.traefik-forward-auth.handler/app
         :port 80
         :host "0.0.0.0"}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}})
