(ns minesweeper.core-test
  (:require [clojure.test :refer [deftest is]]
            [minesweeper.core :as mine]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(deftest test-board-generation
  (let [board (mine/generate-board)]
    (is (vector? board))
    (is (vector? (first board)))))

(deftest test-zero-propagation
  (let [board [[{:bomb? false :count 0 :coords [0 0]} {:bomb? false :count 0 :coords [0 1]}]
               [{:bomb? false :count 0 :coords [1 0]} {:bomb? false :count 0 :coords [1 1]}]]]
   (is (= #{[0 0] [0 1] [1 0] [1 1]}
        (mine/propogated-coordinates board [0 0])))))

(deftest test-zero-prop-large
  (let [board [[{:bomb? false :count 0 :coords [0 0]} {:bomb? false :count 0 :coords [0 1]} {:bomb? false :count 1 :coords [0 2]}]
               [{:bomb? false :count 0 :coords [1 0]} {:bomb? false :count 1 :coords [1 1]} {:bomb? false :count 1 :coords [1 2]}]
               [{:bomb? false :count 1 :coords [2 0]} {:bomb? false :count 2 :coords [2 1]} {:bomb? false :count 1 :coords [2 2]}]]]
   (is (= #{[0 0] [0 1] [0 2] [1 0] [1 1] [1 2] [2 0] [2 1]}
        (mine/propogated-coordinates board [0 0])))))

(defspec always-ten-bombs
  50
  (prop/for-all [w (gen/choose 4 10)
                 h (gen/choose 4 10)
                 b (gen/choose 8 16)]
                (let [board (mine/generate-board w h b)]
                  (= (mine/bomb-count board) b))))
