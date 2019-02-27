(ns traefik-auth0-oidc.middleware)

(defn trim-trailing-slash
	[handler]
	(fn [request]
		(let [uri (:uri request)
			  last-letter (last uri)]
			(if (= \/ last-letter)
				(handler (assoc request :uri (subs uri 0 (dec (count uri)))))
				(handler request)))))