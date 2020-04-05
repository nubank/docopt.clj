#!/usr/bin/env bb

(require '[babashka.classpath :refer [add-classpath]]
         '[clojure.java.shell :refer [sh]])

(def docopt-dep '{:deps {docopt {:git/url "https://github.com/nubank/docopt.clj"
                                 :sha     "12b997548381b607ddb246e4f4c54c01906e70aa"}}})
(def cp (:out (sh "clojure" "-Spath" "-Sdeps" (str docopt-dep))))
(add-classpath cp)

(require '[docopt.core :as docopt])

(load-file "common.clj")
