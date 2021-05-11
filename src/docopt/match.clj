(ns docopt.match
  (:require [clojure.set :as set]
            [clojure.string :as s]
            [docopt.util :refer [defmultimethods re-tok tokenize]]))

(def ^:dynamic space-sep
  "Workaround issue with spaces by converting them to another character.

  See https://github.com/nubank/docopt.clj/pull/5 for details.

  If you have issues with this, use `binding` to select another separator
  string."
  "__DOCOPT_SPACE_SEP__")

(def ^:dynamic newline-sep
  "Workaround issue with newlines by converting them to another character.

  See https://github.com/nubank/docopt.clj/pull/5 for details.

  If you have issues with this, use `binding` to select another separator
  string."
  "__DOCOPT_NEWLINE_SEP__")

(def ^:dynamic tab-sep
  "Workaround issue with tabs by converting them to another character.

  See https://github.com/nubank/docopt.clj/pull/5 for details.

  If you have issues with this, use `binding` to select another separator
  string."
  "__DOCOPT_TAB_SEP__")

;; parse command line

(defmultimethods expand 
  "Expands command line tokens using provided sequence of options." 
  [[tag name arg :as token] options] 
  tag
  :word          [name]  
  :long-option   (let [exactfn   #(= name (:long %))
                       partialfn #(= name (subs (:long %) 0 (min (count (:long %)) (count name))))]
                   [{(first (concat (filter exactfn options) (filter partialfn options))) arg}])
  :short-options (let [options (map (fn [c] (first (filter #(= (str c) (:short %)) options))) name)]
                   (concat (map array-map (butlast options) (repeat nil)) [{(last options) arg}])))

(defn- strings->string-with-seps
  "Convert some strings to a string separator, so we don't lose args after
  parsing."
  [head]
  (map (fn [s]
         (if (string? s)
           (-> s
               (s/replace #" " space-sep)
               (s/replace #"\n" newline-sep)
               (s/replace #"\t" tab-sep))
           s))
       head))

(defn- string-with-seps->string
  [s]
  (-> s
      (s/replace (re-pattern space-sep) " ")
      (s/replace (re-pattern newline-sep) "\n")
      (s/replace (re-pattern tab-sep) "\t")))

(defn- strings-with-seps->strings
  "Convert separators back to string."
  [head]
  (map (fn [v]
         (cond
           (string? v) (string-with-seps->string v)
           (map? v)    (into {}
                             (map
                              (fn [[k v]]
                                [k (some-> v string-with-seps->string)])
                              v))
           :else       v))
       head))

(defn parse
  "Parses the command-line arguments into a matchable state [acc remaining-option-values remaining-words]."
  [{:keys [acc shorts-re longs-re]} argv]
  (let [[head & tail]   (partition-by (partial = "--") argv)
        options         (remove string? (keys acc))
        tokens          (mapcat #(expand % options) (tokenize (s/join " " (strings->string-with-seps head))
                                                              (concat (map vector longs-re  (repeat :long-option))
                                                                      (map vector shorts-re (repeat :short-options))
                                                                      [[(re-tok "-\\S+|(\\S+)")     :word]])))
        tokens'         (strings-with-seps->strings tokens)]
    (when (not-any? nil? tokens')
      [acc
       (apply merge-with conj (zipmap options (repeat [])) (filter map? tokens'))
       (apply concat (filter string? tokens') tail)])))

;; walk pattern tree

(defmultimethods consume 
  "If command line state matches tree node, update accumulator, else return nil."
  [[type key :as pattern] [acc options [word & more-words :as cmdseq] :as state]]
  type
  :docopt.usageblock/argument (when word
                                [(assoc acc key (if (acc key) (conj (acc key) word) word)) options more-words])
  :docopt.usageblock/command  (when (= key word)
                                [(assoc acc key (if (acc key) (inc (acc key))       true)) options more-words])
  :docopt.usageblock/option   (if-let [[head & tail] (seq (options key))]
                                (let [to (acc key)
                                      new-to (if head (if to (conj to head) head) (if to (inc to) true))]
                                  [(assoc acc key new-to) (assoc options key tail) cmdseq])
                                (when (:default-value key) state)))
    
(defmultimethods matches 
  "If command line state matches tree node, update accumulator, else return nil."
  [states [type & children :as pattern]]
  type
  nil                         states
  :docopt.usageblock/token    (into #{} (filter identity (map (partial consume pattern) states)))
  :docopt.usageblock/choice   (apply set/union (map (partial matches states) children))
  :docopt.usageblock/optional (reduce #(into %1 (matches %1 %2)) states children)
  :docopt.usageblock/required (reduce matches states children)
  :docopt.usageblock/repeat   (let [new-states (matches states (first children))]
                                (if (= states new-states)
                                  states                 
                                  (into new-states (matches new-states pattern)))))

;;

(defn option-value 
  "Helper function for 'match-argv' to present option values and deal with defaults."
  [[{:keys [long short default-value]} value]]
  [(if long (str "--" long) (str "-" short)) 
   (cond 
     (nil? value) default-value
     (= [] value) (if (vector? default-value) default-value [default-value])
     :else        value)])

(defn- best-match-by-argv
  [argv]
  (fn [matches]
    (first
     ;; Prioritize matches with `--` if the argv includes a `--`
     (or (and (some #{"--"} argv)
              (filter #(-> % first (get "--")) matches))
         matches))))

(defn- possible-matches
  [state docmap]
  (filter #(every? empty? (cons (% 2) (vals (% 1))))
          (matches #{state} (:tree docmap))))

(defn match-argv
  "Match command-line arguments with usage patterns."
  [docmap argv]
  (if-let [state (parse docmap argv)]
    (if-let [[match] ((best-match-by-argv argv) (possible-matches state docmap))]
      (into (sorted-map) (map #(if (string? (key %)) % (option-value %)) match)))))
