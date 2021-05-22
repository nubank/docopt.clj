#!/usr/bin/env bb

(require '[babashka.classpath :refer [add-classpath]]
         '[clojure.java.shell :refer [sh]])

(def docopt-dep '{:deps {dev.nubank/docopt {:mvn/version "0.6.1-fix7"}}})
(def cp (:out (sh "clojure" "-Spath" "-Sdeps" (str docopt-dep))))
(add-classpath cp)

(require '[docopt.core :as docopt])

(load-file "common.clj")
