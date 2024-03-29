(ns com.jkbff.traefik-forward-auth.helper
	(:require [ring.util.response :as response]
			  [clojure.data.json :as json]))

(defn generate-random
	[length]
	(let [chars (seq "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
		  random-chars (repeatedly #(nth chars (rand-int (count chars))))]
		(apply str (take length random-chars))))

(defn entities-fn
	[e]
	(.replace e \- \_))

(defn identifiers-fn
	[e]
	(.replace e \_ \-))

(defn write-json
	[msg]
	(json/write-str msg :key-fn #(entities-fn (name %))))

(defn read-json
	[msg]
	(json/read-str msg :key-fn #(keyword (identifiers-fn %))))

(defn serve-resource-file
	[filename content-type]
	(response/content-type (response/resource-response filename {:root "public"}) content-type))
