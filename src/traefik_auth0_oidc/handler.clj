(ns traefik-auth0-oidc.handler
	(:require [compojure.core :refer :all]
			  [compojure.route :as route]
			  [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
			  [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
			  [traefik-auth0-oidc.middleware :as middleware]
			  [traefik-auth0-oidc.helper :as helper]))

(defroutes open-routes
		   (GET "/auth" [:as request] (fn [request] (println request) {:body {:message "hi"}}))
		   )

(defroutes unknown-route
		   (route/not-found {:body {:message "Not Found"}}))

(def app (-> (routes
				 open-routes
				 unknown-route)
			 (wrap-json-response {:key-fn #(helper/entities-fn (name %))})
			 middleware/trim-trailing-slash
			 ;(wrap-json-body {:keywords? #(keyword (helper/identifiers-fn %))})
			 (wrap-defaults api-defaults)
			 ;ring.middleware.params/wrap-params
			 ;(ring.middleware.multipart-params/wrap-multipart-params :store ba/byte-array-store)
			 ))