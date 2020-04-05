#!/usr/bin/env bb

(require '[babashka.classpath :refer [add-classpath]]
         '[clojure.java.shell :refer [sh]])

(def docopt-dep '{:deps {docopt {:git/url "https://github.com/nubank/docopt.clj"
                                 :sha     "12b997548381b607ddb246e4f4c54c01906e70aa"}}})
(def cp (:out (sh "clojure" "-Spath" "-Sdeps" (str docopt-dep))))
(add-classpath cp)

(require '[docopt.core :as docopt])

(def usage "Naval Fate.

Usage:
  naval_fate ship new <name>
  naval_fate ship <name> move <lat> <long> [--speed=<kn>]
  naval_fate mine (set|remove) <lat> <long> [--moored|--drifting]
  naval_fate -h | --help
  naval_fate --version

Options:
  -h --help     Show this screen.
  --version     Show version.
  --speed=<kn>  Speed in knots [default: 10].
  --moored      Moored (anchored) mine.
  --drifting    Drifting mine.")

(docopt/docopt usage
               *command-line-args*
               (fn [arg-map] (clojure.pprint/pprint arg-map)))
