(ns traefik-auth0-oidc.handler
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
              [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
              [ring.middleware.session :as session]
              [ring.util.response :as response]
              [traefik-auth0-oidc.middleware :as middleware]
              [traefik-auth0-oidc.helper :as helper]
              [traefik-auth0-oidc.config :as config]
              [clj-http.client :as client]
              [clojure.data.json :as json])
    (:import (java.net URLEncoder)
             (com.auth0.jwk UrlJwkProvider)
             (com.auth0.jwt.algorithms Algorithm)
             (java.security.interfaces RSAPublicKey)
             (com.auth0.jwt JWT)
             (com.auth0.jwt.exceptions JWTVerificationException)))

(defn get-oauth2-authorization-url
    [scopes state]
    (str (config/AUTHORIZATION_URL) "?"
         "response_type=code"
         "&client_id=" (config/CLIENT_ID)
         "&redirect_uri=" (URLEncoder/encode (config/REDIRECT_URI))
         "&scope=" (apply str (interpose "%20" scopes))
         "&state=" state
         "&audience=" (URLEncoder/encode (config/AUDIENCE))
         ))

(defn get-key-by-id
    [key-id]
    (let [provider (UrlJwkProvider. (config/JWKS_DOMAIN))]
        (.get provider key-id)))

(def get-jwt-verifier
    (memoize
        (fn [decoded-jwt]
            (let [key-id                   (.getKeyId decoded-jwt)
                  key                      (get-key-by-id key-id)
                  ^RSAPublicKey public-key (.getPublicKey key)
                  alg                      (Algorithm/RSA256 public-key nil)]

                (-> (JWT/require alg)
                    (.withIssuer (into-array [(config/ISSUER)]))
                    (.withAudience (into-array [(config/AUDIENCE)]))
                    (.acceptLeeway 10)
                    (.build))))))

(defn verify-jwt
    [decoded-jwt]
    (try
            (-> (get-jwt-verifier decoded-jwt)
                (.verify decoded-jwt))
            nil
        (catch JWTVerificationException e (.getMessage e))))

(defn jwt-has-scopes?
    [decoded-jwt required-scopes]
    (let [scopes (clojure.string/split (.asString (.getClaim decoded-jwt "scope")) #" ")]
        (every? #(.contains scopes %) required-scopes)))

(defn process-jwt
    [decoded-jwt required-scopes]
    (or (verify-jwt decoded-jwt)
        (if (not (jwt-has-scopes? decoded-jwt required-scopes))
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
    [code]
    (let [token-params {"grant_type"    "authorization_code"
                        "code"          code
                        "redirect_uri"  (config/REDIRECT_URI)
                        "client_id"     (config/CLIENT_ID)
                        "client_secret" (config/CLIENT_SECRET)}
          response     (client/post (config/TOKEN_URL) {:form-params token-params :throw-exceptions false})]
        (json/read-str (:body response) :key-fn #(keyword (helper/identifiers-fn %)))))

(defn jwt-verification-failed-response
    [message]
    {:status 401
     :body   {:message (str "jwt verification failed: " message)}})

(defn auth-code-handler
    [session code state]

    ; if state not equal, display "invalid state"
    ; if error_description param, display error
    ;(println session)

    (if (not= (:state session) state)

        {:body {:message "invalid state"} :status 400}

        (let [body        (exchange-auth-code-for-token code)
              target-url  (:target-url session)]

            ;(println body)

            (if-let [jwt-token (:access-token body)]
                (if-let [decoded-jwt (JWT/decode jwt-token)]
                    (if-let [message (process-jwt decoded-jwt (config/SCOPES))]
                        (jwt-verification-failed-response message)

                        (-> (response/redirect target-url)
                            (assoc :session {:token decoded-jwt})))

                    (jwt-verification-failed-response "could not decode jwt"))

                (jwt-verification-failed-response (:error body))))))

(defn initiate-authorization-code-grant
    [request]
    (let [scopes     (config/SCOPES)
          state      (helper/generate-random 20) ; prevents CSRF attacks; also allows app state to be restored after flow
          ;nonce     (helper/generate-random 20) ; only needed for OpenID Connect
          url        (get-oauth2-authorization-url scopes state)
          target-url (get-target-url (:headers request))]

        (-> (response/redirect url)
            (assoc :session {:state state :target-url target-url}))))

(defn secure-handler
    [request]
    (let [token (:token (:session request))]
        (if (or (nil? token) (process-jwt token (config/SCOPES)))
            (initiate-authorization-code-grant request)
            (response/redirect (get-target-url (:headers request))))))

(defroutes open-routes
    ; handle auth code from oauth authorization code grant
    (GET "/auth" request (auth-code-handler (:session request) (get-in request [:params :code]) (get-in request [:params :state])))

    ; test auth for request
    (GET "/secure" request (secure-handler request))

    (GET "/" request {:body {:message "working!"}})
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
             (session/wrap-session {:cookie-attrs {:secure false :http-only true}})
             ))