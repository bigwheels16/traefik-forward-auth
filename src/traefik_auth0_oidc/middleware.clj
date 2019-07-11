(ns traefik-auth0-oidc.middleware)

(defn trim-trailing-slash
	[handler]
	(fn [request]
		(let [uri (:uri request)]
			(if (and (clojure.string/ends-with? uri "/") (not (= uri "/")))
				(handler (assoc request :uri (subs uri 0 (dec (count uri)))))
				(handler request)))))