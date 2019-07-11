(ns traefik-auth0-oidc.config)

(defn get-env-string
	[name]
	(clojure.string/replace (System/getenv name) #"\"" ""))

(defn get-env-int
	[name]
	(Integer/parseInt (get-env-string name)))

; TODO use delay/memoize
(defn AUTHORIZATION_URL [] (get-env-string "AUTHORIZATION_URL"))
(defn TOKEN_URL [] (get-env-string "TOKEN_URL"))
(defn CLIENT_ID [] (get-env-string "CLIENT_ID"))
(defn CLIENT_SECRET [] (get-env-string "CLIENT_SECRET"))
(defn AUDIENCE [] (get-env-string "AUDIENCE"))
(defn ISSUER [] (get-env-string "ISSUER"))
(defn REDIRECT_URI [] (get-env-string "REDIRECT_URI"))
(defn JWKS_DOMAIN [] (get-env-string "JWKS_DOMAIN"))
(defn SCOPES [] (clojure.string/split (get-env-string "SCOPES") #" "))
