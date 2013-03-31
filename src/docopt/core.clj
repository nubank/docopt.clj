(ns docopt.core  
  (:require [clojure.string      :as s])
  (:require [docopt.match        :as m])
  (:require [docopt.optionsblock :as o])
  (:require [docopt.usageblock   :as u])
  (:require [docopt.util         :as util])
  (:import java.util.HashMap)
  (:gen-class
    :name org.docopt.clj
    :methods [^{:static true} [docopt [String "[Ljava.lang.String;"] java.util.HashMap]]))

(defn parse
  "Parses doc string."
  [doc]
  {:pre [(string? doc)]}
  (let [usage-split (s/split doc #"(?i)usage:\s*")]
    (util/err (not= 2 (count usage-split)) :syntax
              (count usage-split) " occurences of the 'usage:' keyword, 1 expected.")
    (let [[usage-block options-block] (s/split (second usage-split) #"\n\s*\n" 2)]
      (u/parse usage-block (o/parse options-block)))))

(defmacro docopt
  "Parses doc string at compile-time and matches command line arguments at run-time.
The doc string may be omitted, in which case the metadata of '-main' is used"
  ([args]
    `(let [doc# (:doc (meta (var ~'-main)))]
       (if (string? doc#)
         (m/match-argv (parse doc#) ~args)
         (throw (Exception. "Docopt with one argument requires that #'-main have a doc string.")))))
  ([doc args]
    `(m/match-argv ~(parse doc) ~args)))

(defn -docopt 
  "Java-capable run-time equivalent to 'docopt'."
  [doc args]
  (if-let [cljmap (m/match-argv (parse doc) (into [] args))]
    (let [javamap (HashMap. (count cljmap))]
      (doseq [[k v] cljmap]
        (.put javamap k (if (vector? v) (into-array v) v)))
      javamap)))

