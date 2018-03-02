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

(deftest test-neighbors
  (let [board (mine/generate-base 5 5)]
    (is (= (mine/neighbors board 0 0)
           (map #(get-in board %) [ ,,,  [0 1]
                                   [1 0] [1 1]])))
    (is (= (mine/neighbors board 1 1)
           (map #(get-in board %) [[0 0] [0 1] [0 2]
                                   [1 0]  ,,,  [1 2]
                                   [2 0] [2 1] [2 2]])))))

(deftest test-zero-propagation
  (let [board [[{:bomb? false :count 0 :path [0 0]} {:bomb? false :count 0 :path [0 1]}]
               [{:bomb? false :count 0 :path [1 0]} {:bomb? false :count 0 :path [1 1]}]]]
   (is (= #{[0 0] [0 1] [1 0] [1 1]}
        (mine/propogated-coordinates board [0 0])))))

(deftest test-zero-prop-large
  (let [summary '[[* 4 * 1 1 * 1]
                  [* * 3 1 1 1 1]
                  [3 * 2 1 1 1 0]
                  [1 2 2 2 * 1 0]
                  [1 2 * 2 1 2 1]
                  [* 2 1 1 0 1 *]
                  [1 1 0 0 0 1 1]]
        board (mine/summary->board summary)]
    (is (= (mine/propogated-coordinates board [0 0])
           #{[0 0]}))
    (is (= (mine/propogated-coordinates board [4 1])
           #{[4 1]}))
    ;; zeroes marked with commas
    (is (= (mine/propogated-coordinates board [2 6])
           #{[1 5] [1 6]
             [2 5] [2,6]
             [3 5] [3,6]
             [4 5] [4 6]}))
    (let [zero-spots [[5,4] [6,4] [6,3] [6,2]]])
    (is (= (mine/propogated-coordinates board [6 3])
           #{            [4 3] [4,4] [4 5]
             [5 1] [5 2] [5 3] [5,4] [5 5]
             [6 1] [6,2] [6,3] [6,4] [6 5]}))))

(defspec always-ten-bombs
  50
  (prop/for-all [w (gen/choose 4 10)
                 h (gen/choose 4 10)
                 b (gen/choose 8 16)]
                (let [board (mine/generate-board w h b)]
                  (= (mine/bomb-count board) b))))
