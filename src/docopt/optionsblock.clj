(ns docopt.optionsblock
  (:require [clojure.string :as s]
            [docopt.util :refer [err re-arg-str tokenize]]))

(defn tokenize-option
  "Generates a sequence of tokens for an option specification string."
  [string]
  (tokenize string [[#"\s{2,}(?s).*\[(?i)default(?-i):\s*([^\]]+).*" :default]
                    [#"\s{2,}(?s).*"]
                    [#"(?:^|\s+),?\s*-([^-,])"                       :short]
                    [#"(?:^|\s+),?\s*--([^ \t=,]+)"                  :long]
                    [(re-pattern re-arg-str)                         :arg]
                    [#"\s*[=,]?\s*"]]))

(defn parse-option 
  "Parses option description line into associative map."
  [option-line]
  (let [tokens (tokenize-option option-line)]
    (err (seq (filter string? tokens)) :syntax
         "Badly-formed option definition: '" (s/replace option-line #"\s\s.*" "") "'.")            
  (let [{:keys [short long arg default]} (reduce conj {} tokens)
        [value & more-values] (filter seq (s/split (or default "") #"\s+"))]
    (into (if arg
            {:takes-arg true :default-value (if (seq more-values) (into [value] more-values) value)}
            {:takes-arg false})
          (filter val {:short short :long long})))))

(defn parse
  "Parses options lines."
  [options-lines]
  (let [options (map parse-option options-lines)]
    (err (not (and (distinct? (filter identity (map :long options)))
                   (distinct? (filter identity (map :short options)))))
         :syntax "In options descriptions, at least one option defined more than once.")
    (into #{} options)))
