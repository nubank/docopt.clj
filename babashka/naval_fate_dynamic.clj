#!/usr/bin/env bb

(require '[babashka.classpath :refer [add-classpath]]
         '[clojure.java.shell :refer [sh]])

(def docopt-dep '{:deps {nubank/docopt {:git/url "https://github.com/nubank/docopt.clj"
                                        :sha     "0c5b1c7645901affcda115fc280744f5f8dc802a"}}})
(def cp (:out (sh "clojure" "-Spath" "-Sdeps" (str docopt-dep))))
(add-classpath cp)

(require '[docopt.core :as docopt])

(load-file "common.clj")
