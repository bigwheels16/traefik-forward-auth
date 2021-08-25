(ns com.jkbff.traefik-forward-auth.core-test
  (:require [clojure.test :refer :all]
            [com.jkbff.traefik-forward-auth.core :refer :all]
            [com.jkbff.traefik-forward-auth.handler :as handler]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 1 1))))

(deftest handler
    (testing "replace-all"
        (let [s "A B C"
              replacments [["A" "1"] ["C" "3"]]
              expected "1 B 3"
              result (handler/replace-all s replacments)]
            (is (= expected result))))

    (testing "replace-variables-in-config"
        (let [config {:url1 "123"
                      :url2 "abc"
                      :url3 "http://$HOSTNAME/abc/123"
                      :scopes ["test"]}
              hostname "example.com"
              expected {:url1 "123"
                        :url2 "abc"
                        :url3 "http://example.com/abc/123"
                        :scopes ["test"]}
              result (handler/replace-variables-in-config config hostname)]
            (println result)
            (is (= expected result)))))
