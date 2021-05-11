(defproject dev.nubank/docopt "0.6.1-fix5"
  :description "docopt creates beautiful command-line interfaces - clojure port"
  :url "http://docopt.org"
  :license {:name "MIT" :url "https://github.com/docopt/docopt.clj/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.10.3"]]
  :profiles {:dev {:dependencies [[cheshire "5.10.0"]]}}
  :aot :all)
