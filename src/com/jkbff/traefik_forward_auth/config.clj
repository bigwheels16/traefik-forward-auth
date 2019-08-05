(ns com.jkbff.traefik-forward-auth.config
    (:require [clojure.java.io :as io]
              [com.jkbff.traefik-forward-auth.helper :as helper]))

(defn get-domain-config
    [m domain]
    (let [config  (get m (keyword domain))
          extends (get config :$extends)]
        (if extends
            (merge (get-domain-config m (keyword extends)) config)
            config)))

(defn load-config-map
    []
    (let [config-map (helper/read-json (slurp (io/resource "config.json")))]
        (reduce #(assoc-in %1 [%2 :domain] (name %2)) config-map (keys config-map))))
