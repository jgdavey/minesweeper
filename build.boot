(set-env!
 :source-paths   #{"src/cljs" "test"}
 :dependencies '[[org.clojure/clojure       "1.9.0-alpha14" :scope "provided"]
                 [org.clojure/clojurescript "1.9.293"]
                 [adzerk/boot-cljs          "1.7.228-2"     :scope "test"]
                 [adzerk/boot-cljs-repl     "0.3.3"]
                 [com.cemerick/piggieback   "0.2.1"         :scope "test"]
                 [weasel                    "0.7.0"         :scope "test"]
                 [org.clojure/tools.nrepl   "0.2.12"        :scope "test"]
                 [adzerk/boot-reload        "0.4.13"        :scope "test"]
                 [pandeiro/boot-http        "0.7.3"         :scope "test"]
                 [org.clojure/test.check    "0.9.0"         :scope "test"]
                 [org.clojure/core.async    "0.2.395"]
                 [org.omcljs/om             "0.8.7"]
                 [com.joshuadavey/boot-middleman "0.0.7" :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
 '[adzerk.boot-reload    :refer [reload]]
 '[com.joshuadavey.boot-middleman :refer [middleman]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask dev []
  (comp
    (serve :port 10555, :dir "target/")
    (watch)
    (middleman)
    (reload :on-jsload 'minesweeper.app/main)
    (cljs-repl)
    (cljs :source-map true
          :optimizations :none)
    (target :dir #{"target"})))

(deftask package []
  (comp
    (middleman)
    (cljs :unified false
          :optimizations :advanced)
    (target :dir #{"target"})))
