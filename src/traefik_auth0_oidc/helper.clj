(ns traefik-auth0-oidc.helper
	(:require [ring.util.response :as response]))

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

(defn serve-resource-file
	[filename content-type]
	(response/content-type (response/resource-response filename {:root "public"}) content-type))