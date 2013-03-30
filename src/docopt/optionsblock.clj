(ns docopt.optionsblock
  (:require [clojure.string :as s])
  (:use      docopt.util))

(defn tokenize-names 
  "Generates a sequence of tokens for an option names specification string."
  [names]
  (tokenize (or names "")
            [[#"(<[^<>]*>)"       :arg]
             [#"(?:=|,|\s+)"      nil]
             [(re-tok re-arg-str) :arg]
             [(re-tok "--(\\S+)") :long]
             [(re-tok "-(\\S+)")  :short]]))

(defn parse-default 
  "Retrieves the contents of [default: ...] in an option description."
  [description]
  (let [[string]              (map second (re-seq #"\[(?i)default:\s*([^\]]*)\]" (or description "")))
        [value & more-values] (filter seq (s/split (or string "") #"\s+"))]
    (if (seq more-values)
      (into [value] more-values)
      value)))
    
(defn parse-option 
  "Parses option description line into associative map."
  [line]
  (let [[names description] (s/split line #"\s{2,}" 2)
        tokens (tokenize-names names)]
    (err (seq (filter string? tokens)) :syntax
         "Badly-formed option definition: '" names "'")            
    (let [{:keys [short long arg]} (reduce conj {} tokens)]     
      (into (if arg
              {:takes-arg true :default-value (parse-default description)}
              {:takes-arg false})            
            (filter val {:short short :long long :description description})))))

(defn block->lines 
  "'Real' lines in the options block begin with a dash."
  [block]
  (rest (reduce (fn [acc line] 
                  (if (= \- (first line)) 
                    (conj acc line) 
                    (conj (pop acc) (str (peek acc) line))))
                [] 
                (map s/trim (s/split-lines (str "-\n" block))))))

(defn find-repeated [name-type options] 
  (ffirst (filter #(< 1 (val %)) (dissoc (frequencies (map name-type options)) nil))))

(defn parse [block]
  "Parses options block, i.e. everything following the usage block." 
  (let [options (map parse-option (block->lines block))        
        redefined (or (find-repeated :short options) (find-repeated :long options))]
    (err redefined :syntax
         "In options descriptions, multiple definitions of option '" (option->string redefined) "'.")
    (into #{} options)))
