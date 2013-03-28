(ns docopt-clj.core
  (:require [clojure.string :as s])
  (:require [clojure.set :as set])
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
  
(defn find-repeated [seq] 
  (ffirst (filter #(< 1 (val %)) (frequencies seq))))


;;;; tokenization

(defn tokenize-string [line pairs]
  (reduce (fn [tokenseq [re tag]]
            (mapcat (fn [maybe-string]
                      (if (string? maybe-string)
                        (let [substrings (map s/trim (s/split (str " " (s/trim maybe-string) " ") re))
                              new-tokens (map #(into [tag] (if (vector? %) (filter seq (rest %))))
                                              (re-seq re maybe-string))]                           
                          (filter seq (interleave substrings (concat (if tag new-tokens) (repeat nil)))))
                        [maybe-string]))
                    tokenseq))
          [line]
          pairs))


;;;; parse options block

(defn re-tok [& patterns]
  (re-pattern (str "(?<=^| )" (apply str patterns) "(?=$| )")))

(def re-arg-str "(<[^<>]*>|[A-Z_0-9]*[A-Z_][A-Z_0-9]*)")

(defn parse-option-key [option-key ]
  (tokenize-string (or option-key "")
                   [[#"<[^<>]*>" :arg]
                    [#"(?:=|,|\s+)" nil]
                    [(re-tok re-arg-str) :arg]
                    [(re-tok "--(\\S+)") :long]
                    [(re-tok "-(\\S+)")  :short]]))

(defn parse-option-line [option-line]
  (let [[option-key option-description] (s/split option-line #"\s{2,}" 2)
        tokens (parse-option-key option-key)
        default-values (filter seq (s/split (or (second (re-matches #"\[(?i)default:\s*([^\]]*)\]" (or option-description ""))) "") #"\s+"))]
    (err (seq (filter string? tokens)) ::syntax
         "Badly-formed option definition in '" option-key "'")
    (let [{:keys [short long arg] :as option} (reduce conj {} tokens)]
      (conj {:takes-arg (boolean arg)} 
            (if short [:short short])
            (if long  [:long long])
            (if arg [:default-value (if (seq (rest default-values)) default-values (first default-values))])
            (if (seq option-description) [:description option-description])))))

(defn option-string [{:keys [short long]}]
  (if long (str "--" long) (str "-" short)))

(defn parse-options-block [options-block]
  (let [options (map (comp parse-option-line second) (re-seq #"(?:^|\n)\s*(-.*)" (or options-block "")))
        redefined (or (find-repeated (filter identity (map :short options)))
                      (find-repeated (filter identity (map :long options))))]
    (err redefined ::syntax
         "In options descriptions, multiple definitions of option '" (option-string redefined) "'.")
    (into #{} options)))


;;;; parse usage lines into token seq
;; all tokens are of the form [tag & more] before expansion and [tag line-number & more] after expansion, where tag derives from ::token

(specialize {::token     [::repeat ::options ::option ::word ::group ::choice]
             ::group     [::optional ::required]
             ::optional  [::end-optional]
             ::required  [::end-required]             
             ::word      [::command ::argument]
             ::command   [::separator ::stdin]
             ::option    [::long ::short]})

(defn arg-option-pairs [options]
  (let [options (filter :takes-arg options)]
    (concat (zipmap (map #(re-tok "--(" % ")(?:=| )(\\S+)")      (map :long  (filter :long options)))  (repeat ::long))
            (zipmap (map #(re-tok "-([^- " % "]*" % ") (\\S+)")  (map :short (filter :short options))) (repeat ::short)))))

(defn parse-usage-line [usage-line options]
  (tokenize-string 
    usage-line
    (concat [[#"\.{3}" ::repeat]
             [#"\|" ::choice]
             [#"\(" ::required]
             [#"\)" ::end-required]                   
             [#"\[-\]" ::stdin]
             [#"\[--\]" ::separator]
             [#"\[options\]" ::options]
             [#"\[" ::optional]
             [#"\]" ::end-optional]]
            (arg-option-pairs options)
            [[(re-tok "--([^= ]+)(?:=" re-arg-str ")?") ::long]                   
             [(re-tok "-(\\S+)") ::short]
             [(re-tok re-arg-str) ::argument]
             [(re-tok "(\\S+)") ::command]])))

(defn find-option [[tag name arg] line-number options]  
  (let [type (case tag ::short :short ::long :long)
        takes-arg (not (empty? arg))
        [option] (filter #(= name (% type)) options)]
    (err (and option (not= takes-arg (:takes-arg option))) ::parse
         "Usage line " line-number type " option '" name "' already defined " (if takes-arg "without" "with") " argument.")
    [::option line-number (or option {type name :takes-arg takes-arg})]))

(defmultimethods usage-token-expand 
  [[tag name arg :as token] line-number block-options] 
  tag
  ::token     [[tag line-number]]
  ::word      [[tag line-number name]]
  ::stdin     [[::optional line-number] [::command line-number "-"]  [::end-optional line-number]]
  ::separator [[::optional line-number] [::command line-number "--"] [::end-optional line-number]]
  ::long      [(find-option token line-number block-options)]
  ::options   (concat [[::optional line-number]] (map #(vector ::option line-number %) block-options) [[::end-optional line-number]])
  ::short     (letfn [(new-short [arg c] (find-option [::short (str c) arg] line-number block-options))]
                (conj (into [] (map (partial new-short nil) (butlast name)))
                      (new-short arg (last name)))))

(defn parse-usage-syntax [usage-lines block-options]
  (reduce #(concat %1 [[::choice]] %2) 
          (map-indexed (fn [line-number usage-line]
                         (mapcat #(usage-token-expand % (inc line-number) block-options)
                                 (parse-usage-line (s/replace usage-line #"\s+" " ") block-options)))
                       usage-lines)))


;;;; parse usage pattern 

(defn push-last [stack node]
  (conj (pop stack) (conj (pop (peek stack)) (conj (peek (peek stack)) node))))

(defn peek-last [stack]
  (peek (peek (peek stack))))

(defn pop-last [stack]
  (conj (pop stack) (conj (pop (peek stack)) (pop (peek (peek stack))))))

(specialize {::group-end [::end-optional ::end-required]})

(defmultimethods token-tree
  [stack [tag line-number data]]
  tag
  ::group     (conj stack [tag []])
  ::token     (push-last stack (conj [tag] data))     
  ::repeat    (push-last (pop-last stack) [tag (peek-last stack)])                
  ::choice    (conj (pop stack) (conj (peek stack) []))
  ::group-end (let [[group-type & children :as group] (peek stack)]
                (err (not (isa? tag group-type)) ::parse 
                     "Bad '" (if (= tag ::end-optional) \] \)) "' in line " line-number ".")
                (if (seq children)
                  (let [choices (map #(into [group-type] %) children)]
                    (push-last (pop stack) (if (seq (rest choices))
                                             (into [::choice] choices)
                                             (first choices))))
                  (pop stack))))

(prefer-method token-tree ::group-end ::group)
             
(defn parse-usage-pattern [tokens]
  (let [[tree & more] (reduce token-tree [nil] (concat [[:required]] tokens [[:end-required]]))]
    (err (seq more) ::parse "Missing ')' or ']'.")
    tree))


;;;; parse & accumulate usage variables

(defn parse-usage-variables [tokens]
  (let [token-groups (group-by first tokens)
        options (group-by last (token-groups ::option))]
    (doseq [o (keys options)]
      (let [alt-o (assoc o :takes-arg (not (:takes-arg o)))
            linefn #(s/join ", " (sort (into #{} (map second (options %)))))]
        (err (seq (options alt-o)) ::parse
             "Conflicting definitions of '" (option-string o) "': " 
             " takes " (if (:takes-arg alt-o) "no ") "argument on line(s) " (linefn o)
             " but takes " (if (:takes-arg o) "no ") "argument on line(s) " (linefn alt-o) ".")))
    {::option   (into #{} (keys options))
     ::command  (into #{} (map last (token-groups ::command)))
     ::argument (into #{} (map last (token-groups ::argument)))}))
 
(defmultimethods occurs
  [element [type & [data & _ :as children] :as node]]
  type
  ::token  (if (= data element) 1 0)
  ::repeat (* 2 (occurs element data))
  ::group  (reduce + 0 (map (partial occurs element) children))
  ::choice (reduce max 0 (map (partial occurs element) children)))

(defn accumulator [tree variables]
  (letfn [(acc-base [no-acc acc v] [v (if (< 1 (occurs v tree)) acc no-acc)])]
    (into {} (concat (map (partial acc-base nil [])  (filter :takes-arg (variables ::option)))
                     (map (partial acc-base false 0) (filter (comp not :takes-arg) (variables ::option)))
                     (map (partial acc-base false 0) (variables ::command))
                     (map (partial acc-base nil [])  (variables ::argument))))))

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
           "Inconsistent program name in usage patterns: " (s/join ", " (keys (group-by identity prog-names))) ".")
      (let [tokens    (parse-usage-syntax (map second usage-lines)  (parse-options-block options-block))
            tree      (parse-usage-pattern tokens)]
        {:name    (first prog-names)
         :pattern tree
         :acc     (accumulator tree (parse-usage-variables tokens))}))))

(parse-doc "usage: foo --arc\nfoo -aa\n\n--arc,-a")

;;;; parse command line

(defmultimethods command-token-expand 
  [[tag name arg :as token]] 
  tag
  ::token     [token]
  ::short     (conj (into [] (map #(vector ::short (str %)) (butlast name)))
                    [::short (str (last name)) arg]))

(defn tokenize-command-line [line options]
  (mapcat command-token-expand 
          (tokenize-string line 
                           (concat (arg-option-pairs options)
                                   [[(re-tok "(--?)") ::word]
                                    [(re-tok "--(\\S+)") ::long]
                                    [(re-tok "-(\\S+)") ::short]
                                    [(re-tok "(\\S+)") ::word]]))))

(defmultimethods command-token-parse
  [acc [tag option arg]] 
  tag
  ::token     acc
  ::long      (command-token-parse acc [::option (first (filter #(= option (:long %))  (keys acc))) arg])
  ::short     (command-token-parse acc [::option (first (filter #(= option (:short %)) (keys acc))) arg])
  ::option    (when (and option (= (not (nil? arg)) (:takes-arg option)))
                (push-acc acc option arg)))

;; match

(defn push-acc [acc k v]
  (if-let [to-val (acc k)]
    (if (nil? v)
      (cond 
        (number? to-val) (assoc acc k (inc to-val))
        (= false to-val) (assoc acc k true))
      (cond
        (vector? to-val) (assoc acc k (conj to-val v))
        (= nil to-val)   (assoc acc k v)))))

(defn peek-acc [acc k]
  (if (vector? (acc k))
    (first (acc k))
    (acc k)))

(defn pop-acc [acc k]
  (let [val (acc k)]
    (if (and (vector? val) (seq (rest val)))
      (assoc acc k (into [] (rest val)))
      (dissoc acc k))))

(defmultimethods consume [data [acc remaining [[tag name :as word] & more-words :as cmdseq] :as state]]
  tag
  ::argument [(push-acc acc name data) remaining more-words]
  ::command  (if (= data name) 
               [(push-acc acc name) remaining more-words])
  ::option   (if-let [value (peek-acc remaining data)]
               [(push-acc acc data value) (pop-acc remaining data) cmdseq])
    
(defmultimethods collect-states [states [type & [data & _ :as children] :as pattern]]
  type
  ::word     (into #{} (filter identity (map (partial consume data) states)))
  ::choice   (apply set/union (map (partial match states) children))
  ::optional (reduce #(into %1 (match %1 %2)) clojure.set/union states children)
  ::required (reduce match states children)
  ::repeat   (let [new-states (match states data)]
               (if (empty? new-states)
                 #{}
                 (into new-states (match new-states pattern)))))
  

;;
  
(defn parse-command-line [{:keys [acc pattern]} argv]
  (let [options-acc (into {} (filter (comp not string? key) acc))
        tokens      (tokenize-command-line (s/join " " argv) (keys options-acc))
        remaining   (reduce command-token-parse options-acc tokens)]
    (when remaining
      (match [acc remaining (map second (filter #(isa? (first %) ::word) tokens))] pattern))))


 (parse-command-line ["bar" "-a"] (:acc (parse-doc "usage: foo bar (--arc | -aa)\n\n--arc,-a")))




;;;;

(defn parsefn [doc & options]
  (let [{:keys [name options pattern] :as parsed-doc} (parse-doc doc)]  
    (fn [argv]
      parsed-doc)))
      


