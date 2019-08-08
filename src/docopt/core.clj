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
  [doc args f]
  (let [arg-map (-> doc parse (m/match-argv args))]
    (if-not arg-map
      (println doc)
      (f))))
