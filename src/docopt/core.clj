(ns docopt.core
  (:require [clojure.string      :as s])
  (:require [docopt.match        :as m])
  (:require [docopt.optionsblock :as o])
  (:require [docopt.usageblock   :as u])
  (:require [docopt.util         :as util])
  (:import java.util.HashMap)
  (:gen-class
    :name org.docopt.clj
    :methods [^{:static true} [docopt [String "[Ljava.lang.String;"] java.util.AbstractMap]]))

(defn parse
  "Parses doc string."
  [doc]
  {:pre [(string? doc)]}
  (letfn [(sec-re   [name]          (re-pattern (str "(?:^|\\n)(?!\\s).*(?i)" name ":\\s*(.*(?:\\n(?=[ \\t]).+)*)")))
          (section  [name splitfn]  (map s/trim (mapcat (comp splitfn second) (re-seq (sec-re name) doc))))
          (osplitfn [options-block] (re-seq #"(?<=^|\n)\s*-.*(?:\s+[^- \t\n].*)*" options-block))]
    (u/parse (section "usage" s/split-lines) (o/parse (section "options" osplitfn)))))

(defn docopt
  "Parses doc string at compile-time and matches command line arguments at run-time.
The doc string may be omitted, in which case the metadata of '-main' is used"
  ([args]
    (let [doc (:doc (meta (find-var (symbol (pr-str (ns-name *ns*)) "-main"))))]
      (if (string? doc)
        (m/match-argv (parse doc) args)
        (throw (Exception. "Docopt with one argument requires that #'-main have a doc string.\n")))))
  ([doc args]
    (m/match-argv (parse doc) args)))

(defn -docopt
  "Java-capable run-time equivalent to 'docopt';
argument 'doc' can be either a doc string or the result of a call to 'parse'.
Returns a java.util.HashMap of the matched values provided by the 'args' sequence."
  [doc args]
  (if-let [cljmap (m/match-argv (if (string? doc) (parse doc) doc) (into [] args))]
    (let [javamap (HashMap. (count cljmap))]
      (doseq [[k v] cljmap]
        (.put javamap k (if (vector? v) (into-array v) v)))
      javamap)))
