(ns com.jkbff.traefik-forward-auth.middleware)

(defn trim-trailing-slash
	[handler]
	(fn [request]
		(let [uri (:uri request)]
			(if (and (clojure.string/ends-with? uri "/") (not (= uri "/")))
				(handler (assoc request :uri (subs uri 0 (dec (count uri)))))
				(handler request)))))

(defn log-request-and-response
	[handler]
	(fn [request]
		(prn "request:" request)
		(let [response (handler request)]
			(prn "response:" response)
			response)))
