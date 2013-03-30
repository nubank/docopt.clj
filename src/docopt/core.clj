(ns docopt.core  
  (:require [clojure.string      :as s])
  (:require [docopt.match        :as m])
  (:require [docopt.optionsblock :as o])
  (:require [docopt.usageblock   :as u])
  (:require [docopt.util         :as util]))

(defn parse
  "Parses doc string."
  [doc]
  (let [usage-split (s/split doc #"(?i)usage:\s*")]
    (util/err (not= 2 (count usage-split)) :syntax
              (count usage-split) " occurences of the 'usage:' keyword, 1 expected.")
    (let [[usage-block options-block] (s/split (second usage-split) #"\n\s*\n" 2)]
      (u/parse usage-block (o/parse options-block)))))

(defn match 
  "Parses doc string and matches command line arguments."
  [doc argv]
  (m/match-argv (parse doc) argv))

(defmacro docopt
  "Parses doc string at compile-time and matches command line arguments at run-time."
  [doc argv]
  `(m/match-argv ~(parse doc) ~argv))
