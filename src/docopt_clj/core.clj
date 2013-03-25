(ns docopt-clj.core
  (:require [clojure.string :as s])
  (:use     [slingshot.slingshot :only [throw+ try+]]))
            
(defmacro err [err-clause type & err-strs]
  `(when ~err-clause
     (throw+ {:type ~type :msg (str ~@err-strs)})))

(defmacro defmultimethods [method-name args dispatch-fn-body & body]
  {:pre [(even? (count body)) (vector? args)]}
  `(do (defmulti ~method-name (fn ~args (do ~dispatch-fn-body)))
     ~@(map (fn [[dispatched-key dispatched-body]]
              `(defmethod ~method-name ~dispatched-key ~args (do ~dispatched-body)))
            (apply array-map body))))
 
(defmacro specialize [m]
  `(do ~@(mapcat (fn [[parent children]]
                   (map (fn [child] `(derive ~child ~parent)) children))
                 m)))
  

;;;; parse options block

(defn re-tok [& patterns]
  (re-pattern (str "(?<=^| )" (apply str patterns) "(?=$| )")))

(def re-arg-str "(<[^<>]*>|[A-Z_0-9]*[A-Z_][A-Z_0-9]*)")

(defn parse-default-value [option-key option-description]
  (let [[default & more] (map second (re-seq #"\[default:\s*([^\]]*)\]" (or option-description "")))]
    (err (seq more) ::parse
         (inc (count more)) " default values for option definition '" option-key "' , at most 1 expected.")
    default))

(defn parse-option-key [option-key]
  (let [clean-key (s/replace (or  option-key "") #"[=,]" " ")                         
        [_ bad] (re-matches #"(?:^|\s)(-{1,2})(?:$|\s)" clean-key)]
    (err bad ::parse
         "'" bad "' is not a valid short option, in '" option-key "'")
    (let [{args 0, 
           [[_ short & more-shorts] & even-more-shorts] 1, 
           [[_1 _2 & long] & more-longs] 2} 
          (group-by #(if (= \- (first %)) (if (= \- (second %)) 2 1) 0) 
                    (s/split clean-key #"\s+"))]
      (err (or (seq more-shorts) (seq even-more-shorts)) ::parse
           "too many short option groups specified in '" option-key "', at most 1 expected.")
      (err (seq more-longs) ::parse
           (inc (count more-longs)) "long options specified in '" option-key "', at most 1 expected.")      
      [(if short (str short)) (if (seq long) (apply str long)) (boolean (seq args))])))

(defn parse-option-line [option-line]
  (let [[option-key option-description] (s/split option-line #"\s{2,}" 2)
        default-value (parse-default-value option-key option-description)
        [short long takes-arg] (parse-option-key option-key)
        more (filter val {:long long :description option-description :default-value default-value :takes-arg takes-arg})]
    (concat (if short [(into {:type ::short :name short} more)])
            (if long  [(into {:type ::long :name long} more)]))))

;;

(defmultimethods option-string [o] (:type o)
  ::short (str "-"  (:name o))
  ::long  (str "--" (:name o)))

(defn parse-options-block [options-block]
  (if options-block
    (let [options (into [] (mapcat parse-option-line (filter #(= \- (first %)) (map s/triml (s/split-lines options-block)))))
          redefined (keys (filter #(< 1 (val %)) (frequencies (map option-string options))))]
      (err redefined ::syntax
           "In options descriptions, multiple definitions of the following options: '" (s/join "', '" redefined) "'.")
      options)
    []))

;;;; parse usage lines into token seq
;; all tokens are of the form [tag & more] before expansion and [tag line-number data] after expansion, where tag derives from ::token

(specialize {::token     [::repeat ::options ::option ::word ::group ::choice]
             ::group     [::or ::xor]
             ::or        [::end-or]
             ::xor       [::end-xor]             
             ::word      [::command ::separator ::argument]
             ::argument  [::stdin]
             ::option    [::long ::short]})

;; tokenization


(defn tokenize-string [string re tag]
  (filter seq (interleave (map s/trim (s/split (str " " (s/trim string) " ") re))
                          (concat (map #(into [tag ] (if (vector? %) (filter seq (rest %))))
                                       (re-seq re string))
                                  (repeat nil)))))

(defmultimethods option-re [o] (:type o)
  ::short [(re-tok (str "-([^- " (:name o) "]*" (:name o) ") ?(\\S+)"))      ::short]
  ::long  [(re-tok (str "--(" (:name o) ")(?:=| )(\\S+)")) ::long])

(defn parse-usage-line [usage-line options]
  (reduce (fn [tokenseq [re tag]]
            (mapcat #(if (string? %) (tokenize-string % re tag) [%]) tokenseq))
          [usage-line] 
          (concat [[#"\.{3}" ::repeat]
                   [#"\|" ::choice]
                   [#"\(" ::xor]
                   [#"\)" ::end-xor]                   
                   [#"\[-\]" ::stdin]
                   [#"\[--\]" ::separator]
                   [#"\[options\]" ::options]
                   [#"\[" ::or]
                   [#"\]" ::end-or]]
                  (map option-re (filter :takes-arg options))
                  [[(re-tok "--([^= ]+)(?:=" re-arg-str ")?") ::long]                   
                   [(re-tok "-(\\S+)") ::short]
                   [(re-tok re-arg-str) ::argument]
                   [(re-tok "(\\S+)") ::command]])))

;; token expansion

(defn find-option [[tag name arg] line-number options]  
  (let [options (filter #(and (= tag (:type %)) (= name (:name %))) options)
        [clash] (filter #(not= (boolean arg) (boolean (:takes-arg %))) options)]
    (err clash ::parse
         "Usage line " line-number ": " 
         (case tag ::short "short" ::long "long") " option '" name "' defined " (if arg "with" "without") " argument, "
         "while already defined " (if arg "without" "with") " argument.")     
    (or (first options)
        (conj {:type tag :name name :takes-arg (boolean arg)} 
              (if (= tag ::long) 
                [:long name])))))

(defmultimethods token-expand 
  [[tag name arg :as token] line-number block-options] 
  tag
  ::token     [[tag line-number]]
  ::word      [[tag line-number name]]
  ::separator [[tag line-number "--"]]
  ::stdin     [[tag line-number "-"]]
  ::long      [[::long line-number (find-option token line-number block-options)]]
  ::options   (concat [[::or line-number]] (map #(vector (:type %) line-number %) block-options) [[::end-or line-number]])  
  ::short     (letfn [(new-short [arg c] [::short line-number (find-option [::short (str c) arg] line-number block-options)])]
                (conj (into [] (map (partial new-short nil) (butlast name)))
                      (new-short arg (last name)))))

;; 

(defn parse-usage-syntax [usage-lines block-options]
  (reduce #(concat %1 [[::choice]] %2) 
          (map-indexed (fn [line-number usage-line]
                         (mapcat #(token-expand % (inc line-number) block-options)
                                 (parse-usage-line (s/replace usage-line #"\s+" " ") block-options)))
                       usage-lines)))
  
  
;;;; parse usage options

(defn parse-usage-options [tokens]
  (let [option-defs (group-by (fn [[tag _ option]] 
                                [tag (:name option)])
                              (filter #(isa? (first %) ::option) tokens))
        options (into {} (map (fn [[option-key option-tokens]]
                                [option-key (into #{} (map last option-tokens))])
                              option-defs))                       
        [[clash-tag clash-name :as clash-key] _] (first (filter #(seq (rest (val %))) options))
        [clash-line & more-clash-lines] (reverse (sort (into #{} (map second (option-defs clash-key)))))]
    (err clash-tag ::parse
         "Conflicting definitions of " (case clash-tag ::short "short" :long "long") " option '" clash-name "' in "
         (if (seq more-clash-lines)
           (str "lines " (s/join ", " (reverse more-clash-lines)) " and ") 
           "line ")
         clash-line ".")
    (let [{short ::short long ::long} (group-by :type (into #{} (map first (vals options))))]
      (concat (sort-by :name short) (sort-by :name long)))))


;;;; parse usage pattern 

(defn push-last [stack node]
  (conj (pop stack) (conj (pop (peek stack)) (conj (peek (peek stack)) node))))

(defn peek-last [stack]
  (peek (peek (peek stack))))

(defn pop-last [stack]
  (conj (pop stack) (conj (pop (peek stack)) (pop (peek (peek stack))))))

(specialize {::group-end [::end-or ::end-xor]})

(defmultimethods token-tree
  [stack [tag line-number token-data :as token]]
  tag
  ::group     (conj stack [tag line-number []])
  ::token     (push-last stack token)     
  ::repeat    (push-last (pop-last stack) [tag line-number (peek-last stack)])                
  ::choice    (conj (pop stack) (conj (peek stack) []))
  ::group-end (let [[group-type & _ :as group] (peek stack)]
                (err (not (isa? tag group-type)) ::parse 
                     "Bad '" (if (= tag ::end-or) \] \)) "' in line " line-number ".")
                (push-last (pop stack) group)))

(prefer-method token-tree ::group-end ::group)

(defn merge-or [node-seq]
  (reduce (fn [merged-seq [type line-number & children :as node]]
            (let [[merged-type _ & merged-children] (peek merged-seq)]
              (if (= type merged-type ::or)
                (conj (pop merged-seq) (apply vector ::or line-number (into #{} (filter seq (concat merged-children children)))))
                (conj merged-seq node))))
          []
          node-seq))

(defn optimize-children [children]
  (let [optimized (into #{} (map #(merge-or (filter identity %)) children))]
    (when-not (= #{[]} optimized)
      (into [] optimized))))


(defmultimethods optimize
  [[type line-number & children :as node]]
  type
  nil      nil
  ::token  [node]
  ::repeat (if-let [child (optimize (first children))]
             [[type line-number child]]
             (err true ::parse "Bad '...' in line " line-number ": nothing to repeat."))
  ::or     (when-let [optimized (optimize-children (map #(mapcat optimize %) children))]
             [(apply vector ::or line-number (filter seq optimized))])
  ::xor    (when-let [optimized (optimize-children (map #(mapcat optimize %) children))]
             (if (seq (rest optimized)) 
               [(apply vector ::xor line-number optimized)]
               (first optimized))))
             
             
(defn parse-usage-pattern [tokens]
  (let [[tree & more] (reduce token-tree [[::xor nil []]] tokens)]
    (err (seq more) ::parse "Missing ')' or ']'.")
    tree))

;;;; parse doc

(defn split-doc-blocks [doc]
  (let [usage-split (s/split doc #"(?i)usage:\s*")]
    (err (not= 2 (count usage-split)) ::syntax
         (count usage-split) " occurences of the 'usage:' keyword, 1 expected.")
    (s/split (second usage-split) #"\n\s*\n" 2)))

(defn parse-doc [doc]
  (let [[usage-block options-block] (split-doc-blocks doc)]
    (err (= "" usage-block) ::syntax
         "No usage patterns, at least 1 expected.")      
    (let [usage-lines    (map #(rest (re-matches #"\s*(\S+)\s*(.*)" %)) (s/split-lines usage-block))
          prog-names     (map first usage-lines)]
      (err (apply not= prog-names) ::parse
           "Inconsistent program name in usage patterns: " (keys (group-by identity prog-names)))
      (let [tokens (parse-usage-syntax (map second usage-lines)  (parse-options-block options-block))]
        {:name (first prog-names)
         :options (into [] (parse-usage-options tokens))
         :pattern (parse-usage-pattern tokens)}))))
      
(defn parsefn [doc & options]
  (let [{:keys [name options pattern] :as parsed-doc} (parse-doc doc)]  
    (fn [argv]
      parsed-doc)))
      


