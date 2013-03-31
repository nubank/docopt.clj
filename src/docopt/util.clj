(ns docopt.util
  (:require [clojure.string :as s]))

;; exceptions raised when parsing docstring

(defmacro err [err-clause type & err-strs]
  `(when ~err-clause
     (throw (Exception. (str "DOCOPT ERROR " ~(case type :syntax "(syntax) " :parse "(parse) ") \| ~@err-strs)))))

;; multimethod macros

(defmacro defmultimethods
  "Syntactic sugar for defmulti + multiple defmethods."
  [method-name docstring args dispatch-fn-body & body]
  {:pre [(string? docstring) (even? (count body)) (vector? args)]}
  `(do (defmulti ~method-name ~docstring (fn ~args (do ~dispatch-fn-body)))
       ~@(map (fn [[dispatched-key dispatched-body]]
                `(defmethod ~method-name ~dispatched-key ~args (do ~dispatched-body)))
              (apply array-map body))))
  
(defmacro specialize [m]
  "Syntactic sugar for derive." 
  `(do ~@(mapcat (fn [[parent children]]
                   (map (fn [child] `(derive ~child ~parent)) children))
                 m)))

;; options

(defn option->string [{:keys [short long]}]  
  (if long (str "--" long) (str "-" short)))

;; tokenization

(def re-arg-str "(<[^<>]*>|[A-Z_0-9]*[A-Z_][A-Z_0-9]*)") ; argument pattern

(defn re-tok 
  "Generates tokenization regexp, bounded by whitespace or string beginning / end."
  [& patterns]
  (re-pattern (str "(?<=^| )" (apply str patterns) "(?=$| )")))

(defn arg-option-pairs 
  "Generates a sequence of [re tag] pairs for 'tokenize' to extract all 
provided options which take arguments and which might be ambiguous otherwise."
  [options long-tag short-tag]
  (let [options (filter :takes-arg options)]
    (concat (zipmap (map #(re-tok "--(" % ")(?:=| )(\\S+)") (map #(or (:long-re %) (:long %)) (filter :long  options)))
                    (repeat long-tag))
            (zipmap (map #(re-tok "-([^- " % "]*" % ") ?(\\S+)") (map :short (filter :short options))) 
                    (repeat short-tag)))))

(defn tokenize
  "Repeatedly extracts tokens from string according to sequence of [re tag]; 
tokens are of the form [tag & groups] as captured by the corresponding regex."
  [string pairs]
  (reduce (fn [tokenseq [re tag]]
            (mapcat (fn [maybe-string]
                      (if (string? maybe-string)
                        (let [substrings (map s/trim (s/split (str " " (s/trim maybe-string) " ") re))
                              new-tokens (map #(into [tag] (if (vector? %) (filter seq (rest %))))
                                              (re-seq re maybe-string))]                           
                          (filter seq (interleave substrings (concat (if tag new-tokens) (repeat nil)))))
                        [maybe-string]))
                    tokenseq))
          [string]
          pairs))
