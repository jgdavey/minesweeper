(set-env!
 :source-paths   #{"src/cljs" "src/cljx" "test"}
 :dependencies '[[adzerk/boot-cljs       "0.0-2727-0" :scope "test"]
                 [adzerk/boot-cljs-repl  "0.1.8"      :scope "test"]
                 [deraen/boot-cljx       "0.2.2"      :scope "test"]
                 [adzerk/boot-reload     "0.2.4"      :scope "test"]
                 [pandeiro/boot-http     "0.6.1"      :scope "test"]
                 [org.clojure/test.check "0.6.1"      :scope "test"]
                 [org.clojure/clojure    "1.6.0"      :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :scope "provided"]
                 [org.omcljs/om          "0.8.7"]
                 [com.joshuadavey/boot-middleman "0.0.3-SNAPSHOT" :scope "test"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
 '[adzerk.boot-reload    :refer [reload]]
 '[deraen.boot-cljx      :refer [cljx]]
 '[com.joshuadavey.boot-middleman :refer [middleman]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask dev []
  (comp
    (serve :port 10555, :dir "target/")
    (watch)
    (cljx)
    (middleman)
    (reload :on-jsload 'minesweeper.app/main)
    (cljs-repl)
    (cljs :unified true
          :source-map true
          :optimizations :none)))

(deftask package []
  (comp
    (cljx)
    (middleman)
    (cljs :unified false
          :optimizations :advanced)))
