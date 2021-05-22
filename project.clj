(defproject dev.nubank/docopt "0.6.1-fix7"
  :description "docopt creates beautiful command-line interfaces - clojure port"
  :url "http://docopt.org"
  :license {:name "MIT"
            :url "https://github.com/docopt/docopt.clj/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  :profiles {:dev {:dependencies [[cheshire "5.10.0"]]}}
  :aot :all)
