(set-env!
 :source-paths   #{"src/cljs" "test"}
 :dependencies '[[org.clojure/clojure             "1.9.0"   :scope "provided"]
                 [org.clojure/clojurescript       "1.10.64"]
                 [adzerk/boot-cljs                "2.1.4"   :scope "test"]
                 [adzerk/boot-cljs-repl           "0.3.3"   :scope "test"]
                 [adzerk/boot-reload              "0.5.2"   :scope "test"]
                 [com.cemerick/piggieback         "0.2.2"   :scope "test" :exclusions [org.clojure/clojurescript]]
                 [com.joshuadavey/boot-middleman  "0.0.7"   :scope "test"]
                 [org.clojure/test.check          "0.9.0"   :scope "test"]
                 [org.clojure/tools.nrepl         "0.2.13"  :scope "test"]
                 [pandeiro/boot-http              "0.8.3"   :scope "test"]
                 [weasel                          "0.7.0"   :scope "test"]
                 [org.clojure/core.async          "0.4.474" :exclusions [org.clojure/tools.reader]]
                 [cljsjs/react                    "15.6.2-4"]
                 [cljsjs/react-dom                "15.6.2-4"]
                 [cljsjs/create-react-class       "15.6.2-0"]
                 [org.clojure/tools.reader        "1.3.0-alpha3"]
                 [org.omcljs/om                   "1.0.0-beta2"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
 '[adzerk.boot-reload    :refer [reload]]
 '[com.joshuadavey.boot-middleman :refer [middleman]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask cider []
  (set-env! :dependencies #(into % '[[cider/cider-nrepl "0.16.0"]
                                     [refactor-nrepl "2.4.0-SNAPSHOT"]]))
  (require 'cider.tasks)
  ((resolve 'cider.tasks/add-middleware)
   :middleware '[cider.nrepl/cider-middleware
                 refactor-nrepl.middleware/wrap-refactor]))

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
    (cljs :optimizations :advanced)
    (target :dir #{"target"})))
