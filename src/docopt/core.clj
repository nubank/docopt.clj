(ns docopt.core  
  (:require [clojure.string      :as s])
  (:require [docopt.match        :as m])
  (:require [docopt.optionsblock :as o])
  (:require [docopt.usageblock   :as u])
  (:require [docopt.util         :as util]))

(defn parse
  "Parses doc string."
  [doc]
  {:pre [(string? doc)]}
  (let [usage-split (s/split doc #"(?i)usage:\s*")]
    (util/err (not= 2 (count usage-split)) :syntax
              (count usage-split) " occurences of the 'usage:' keyword, 1 expected.")
    (let [[usage-block options-block] (s/split (second usage-split) #"\n\s*\n" 2)]
      (u/parse usage-block (o/parse options-block)))))

(defn match 
  "Parses doc string and matches command line arguments."
  [doc args]
  {:pre [(string? doc) (or (nil? args) (sequential? args))]}
  (m/match-argv (parse doc) args))

(defmacro docopt
  "Parses doc string at compile-time and matches command line arguments at run-time.
The doc string may be omitted, in which case the metadata of '-main' is used"
  ([args]
    `(let [doc# (:doc (meta (var ~'-main)))]
       (if (string? doc#)
         (m/match-argv (parse doc#) ~args)
         (throw (Exception. "docopt requires a doc string: either provided as first argument, or as :doc metadata in '-main'.")))))
  ([doc args]
    `(m/match-argv ~(parse doc) ~args)))
