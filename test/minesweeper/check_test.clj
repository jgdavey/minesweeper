(ns minesweeper.check-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))



(defspec sort-is-idempotent
  (prop/for-all [v (gen/vector gen/int)]
                (= (sort v) (sort (sort v)))))

(defspec first-smaller-than-last
  (prop/for-all [v (gen/such-that not-empty (gen/vector gen/int))]
                (let [ve (sort v)]
                (<= (first ve) (last ve)))))

(defspec never-42
  (prop/for-all [v (gen/vector gen/int)]
                (not-any? #{42} v)))
