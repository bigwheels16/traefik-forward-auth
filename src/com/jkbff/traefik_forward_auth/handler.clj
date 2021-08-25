(ns com.jkbff.traefik-forward-auth.handler
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
              [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
              [ring.middleware.session :as session]
              [ring.middleware.session.memory :as memory]
              [ring.util.response :as response]
              [com.jkbff.traefik-forward-auth.middleware :as middleware]
              [com.jkbff.traefik-forward-auth.helper :as helper]
              [com.jkbff.traefik-forward-auth.config :as config]
              [clj-http.client :as client]
              [clojure.data.json :as json])
    (:import (java.net URLEncoder)
             (com.auth0.jwk UrlJwkProvider)
             (com.auth0.jwt.algorithms Algorithm)
             (java.security.interfaces RSAPublicKey)
             (com.auth0.jwt JWT)
             (com.auth0.jwt.exceptions JWTVerificationException)
             (java.nio.charset StandardCharsets)))

(def session-store (memory/memory-store))

(defn get-oauth2-authorization-url
    [scopes state config]
    (str (:authorization-url config) "?"
         "response_type=code"
         "&client_id=" (:client-id config)
         "&redirect_uri=" (URLEncoder/encode (:redirect-url config) StandardCharsets/UTF_8)
         "&scope=" (apply str (interpose "%20" scopes))
         "&state=" state
         "&audience=" (URLEncoder/encode (:audience config) StandardCharsets/UTF_8)
         ))

(defn get-key-by-id
    [key-id config]
    (let [provider (UrlJwkProvider. ^String (:jwks-domain config))]
        (.get provider key-id)))

(def get-jwt-verifier
    (memoize
        (fn [decoded-jwt config]
            (let [key-id                   (.getKeyId decoded-jwt)
                  key                      (get-key-by-id key-id config)
                  ^RSAPublicKey public-key (.getPublicKey key)
                  alg                      (Algorithm/RSA256 public-key nil)]

                (-> (JWT/require alg)
                    (.withIssuer (into-array [(:issuer config)]))
                    (.withAudience (into-array [(:audience config)]))
                    (.acceptLeeway 10)
                    (.build))))))

(defn verify-jwt
    [decoded-jwt config]
    (try
        (-> (get-jwt-verifier decoded-jwt config)
            (.verify decoded-jwt))
        nil
        (catch JWTVerificationException e (.getMessage e))))

(defn jwt-has-scopes?
    [decoded-jwt required-scopes]
    (let [scopes (clojure.string/split (or (.asString (.getClaim decoded-jwt "scope")) "") #" ")]
        (every? #(.contains scopes %) required-scopes)))

(defn process-jwt
    [decoded-jwt config]
    (or (verify-jwt decoded-jwt config)
        (if (not (jwt-has-scopes? decoded-jwt (:scopes config)))
            "missing required scopes")))

(defn get-target-url
    [headers]
    (let [proto (get headers "x-forwarded-proto")
          host  (get headers "x-forwarded-host")
          uri   (get headers "x-forwarded-uri")]

        (if (and proto host uri)
            (str proto "://" host uri)
            "/")))

(defn exchange-auth-code-for-token
    [code config]
    (let [token-params {"grant_type"    "authorization_code"
                        "code"          code
                        "redirect_uri"  (:redirect-url config)
                        "client_id"     (:client-id config)
                        "client_secret" (:client-secret config)}
          response     (client/post (:token-url config) {:form-params token-params :throw-exceptions false})]
        (json/read-str (:body response) :key-fn #(keyword (helper/identifiers-fn %)))))

(defn jwt-verification-failed-response
    [message]
    {:status 401
     :body   {:message (str "jwt verification failed: " message)}
     :session nil})

(defn auth-code-handler
    [session code state config]

    ; if state not equal, display "invalid state"
    ; if error_description param, display error

    (if (not= (:state session) state)

        {:body {:message (str "invalid state; expecting: " (:state session))} :status 400}

        (let [body       (exchange-auth-code-for-token code config)
              target-url (:target-url session)]

            ;(println body)

            (if-let [jwt-token (:access-token body)]
                (if-let [decoded-jwt (JWT/decode jwt-token)]
                    (if-let [message (process-jwt decoded-jwt config)]
                        (jwt-verification-failed-response message)

                        (-> (response/redirect target-url)
                            (assoc :session {:token decoded-jwt})))

                    (jwt-verification-failed-response "could not decode jwt"))

                (jwt-verification-failed-response (:error-description body))))))

(defn initiate-authorization-code-grant
    [request config]
    (let [scopes     (:scopes config)
          state      (or (get-in request [:session :state]) (helper/generate-random 20))  ; only generate if a state is not already set in the session; prevents CSRF attacks; also allows app state to be restored after flow
          ;nonce     (helper/generate-random 20) ; only needed for OpenID Connect
          url        (get-oauth2-authorization-url scopes state config)
          target-url (get-target-url (:headers request))]

        (-> (response/redirect url)
            (assoc :session {:state state :target-url target-url}))))

(defn replace-all
    [s replacements]
    (reduce (fn [acc [match replacement]] (if (string? acc)
                                              (clojure.string/replace acc match replacement)
                                              acc))
            s
            replacements))

(defn replace-variables-in-config
    [config hostname]
    (let [replacements [["$HOSTNAME" hostname]]]
        (reduce (fn [acc [k v]] (assoc acc k (replace-all v replacements))) {} config)))

(def get-config
    (memoize
        (fn [hostname]
            (let [config-map (config/load-config-map)
                  config (or (config/get-domain-config config-map hostname)
                             (config/get-domain-config config-map "default"))]
                (replace-variables-in-config config hostname)))))

(defn secure-handler
    [request]
    (let [token  (:token (:session request))
          config (get-config (get-in request [:headers "x-forwarded-host"]))]
        (if (or (nil? token) (process-jwt token config))
            (initiate-authorization-code-grant request config)
            {:status 204
             :headers {"X-Access-Token" (.getToken token)}})))

(defn get-access-token
    [session-id]
    (let [payload (some-> (ring.middleware.session.store/read-session session-store session-id)
                          :token
                          .getToken)]

        (if payload
            {:body payload}
            {:status 404})))

(defroutes open-routes
    ; handle auth code from oauth authorization code grant
    (GET "/callback" request (auth-code-handler (:session request) (get-in request [:params :code]) (get-in request [:params :state]) (get-config (get-in request [:headers "x-forwarded-host"]))))

    ; test auth for request
    (GET "/authorize" request (secure-handler request))

    (GET "/access-token/:session-id" [session-id] (get-access-token session-id))

    (GET "/health" request {:status 200 :body {:message "OK"}})
    )

(defroutes unknown-route
    (route/not-found {:body {:message "Not Found"}}))

(def app (-> (routes
                 open-routes
                 unknown-route)
             (wrap-json-response {:key-fn #(helper/entities-fn (name %))})
             middleware/trim-trailing-slash
             (wrap-json-body {:keywords? #(keyword (helper/identifiers-fn %))})
             (wrap-defaults api-defaults)
             middleware/log-request-and-response
             (session/wrap-session {:store session-store
                                    :cookie-name "auth-session"
                                    :cookie-attrs {:secure false :http-only true}})
             ))
