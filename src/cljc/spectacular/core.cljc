(ns spectacular.core
  (:require [clojure.string :as str]
            [stigmergy.tily :as util]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log :include-macros true]
            
            [spectacular.coerce :as co]))

(defn generate-schema [schema-dsl]
  (flatten (for [[entity-ns attr] schema-dsl]
             (for [[attr-name attr-prop] attr]
               (let [enum-keywords (atom nil)
                     schema-map (let [[attr-type & other] attr-prop
                                      attr-name (-> attr-name str (str/replace #":" ""))
                                      attr-type (-> attr-type str (str/replace #":" ""))
                                      spec-name (keyword (name entity-ns) attr-name)
                                      datomic-type (if (= attr-type "enum")
                                                     :db.type/ref
                                                     (keyword "db.type" attr-type))
                                      required-schema {:db/ident spec-name
                                                       :db/valueType datomic-type
                                                       :db/cardinality (if (util/some-in? :many other)
                                                                         :db.cardinality/many
                                                                         :db.cardinality/one)}
                                      final-schema (atom required-schema)]
                                  (when (util/some-in? :index other)
                                    (swap! final-schema #(into % {:db/index true})))

                                  (if (util/some-in? :unique-value other)
                                    (swap! final-schema #(into % {:db/unique :db.unique/value}))
                                    (if (util/some-in? :unique-identity other)
                                      (swap! final-schema #(into % {:db/unique :db.unique/identity}))))

                                  (when (util/some-in? :fulltext other)
                                    (swap! final-schema #(into % {:db/fulltext true})))

                                  (when (util/some-in? :component other)
                                    (swap! final-schema #(into % {:db/isComponent true})))

                                  (when (= attr-type "enum")
                                    (reset! enum-keywords (first other)))
                                  @final-schema)]
                 [schema-map (map (fn [kw]
                                    {:db/ident kw}) @enum-keywords)])))))

(defn conform [collection-of-entity]
  (clojure.walk/prewalk (fn [e]
                          (if (and (vector? e)
                                   (let [e1 (first e)]
                                     (and (keyword? e1)
                                          (not= (namespace e1)
                                                "db")
                                          (not= e1 :part)
                                          (not= e1 :idx))))
                            (let [ [k v & other] e
                                  conformer-kw (-> (str k "-conformer")
                                                   (str/replace #":" "")
                                                   keyword)
                                  conformer-spec (s/get-spec conformer-kw)]
                              (if conformer-spec
                                (try
                                  (into [k (s/conform conformer-kw v)] other)
                                  #?(:cljs (catch js/Error e
                                             (let [msg (.. e -message)]
                                               (throw {:attribute k
                                                       :value v
                                                       :collection collection-of-entity
                                                       :msg msg}))))
                                  #?(:clj (catch Exception e
                                            (log/error "exception " k v)
                                            (throw e))))
                                e))
                            e))
                        collection-of-entity))

(defn generate-spec [schema-dsl]
  (let [type->predicate-conform {:keyword [keyword? keyword]
                                 :string [string? str]
                                 :boolean [boolean? boolean]
                                 :long [int? co/str->int]
                                 :bigint (do
                                           #?(:clj [#(= (type %) clojure.lang.BigInt) co/str->bigint])
                                           #?(:cljs [number? co/str->bigint]))
                                 :float [float? co/str->float]
                                 :double [double? co/str->float]
                                 :int [int? co/str->int]
                                 :bigdec (do
                                           #?(:clj [decimal? co/str->bigdec])
                                           #?(:cljs [double? co/str->bigdec]))
                                 :instant [inst? identity]
                                 :uuid [uuid? identity]
                                 :uri [string? identity]
                                 :bytes [false? identity]}]
    (doseq [[entity-ns attr] schema-dsl]
      (let [attr-req-seq (doall (for [[attr-name attr-prop] attr]
                                  (when (vector? attr-prop)
                                    (let [[attr-type & other] attr-prop
                                          [attr-predicate default-conform-fn] (if (= attr-type :enum)
                                                                                (let [vector-of-enums (second attr-prop)]
                                                                                  [(fn [e]
                                                                                     (util/some-in? e vector-of-enums))
                                                                                   (fn [e]
                                                                                     (co/->enum e vector-of-enums))])
                                                                                (type->predicate-conform attr-type))
                                          attr-name (-> attr-name str (str/replace #":" ""))
                                          spec-name (keyword (name entity-ns) attr-name)
                                          conform-fn (or (first (filter #(fn? %) other))
                                                         default-conform-fn)]

                                      (when attr-predicate
                                        (s/def-impl spec-name (str attr-predicate) attr-predicate))
                                      
                                      (when conform-fn
                                        (let [conformer-name (keyword (str (name entity-ns)
                                                                           "/" attr-name
                                                                           "-conformer"))
                                              conformer (s/conformer conform-fn)]
                                          (s/def-impl conformer-name conformer-name conformer)))
                                      [spec-name (util/some-in? :req other)]))))
            attr-req-seq (filter (fn [attr-req]
                                   (second attr-req))
                                 attr-req-seq)
            required-attributes (map #(first %) attr-req-seq)]
        #?(:clj
           (when-not (empty? required-attributes)
             (let [current-ns (str *ns*)
                   spec-name (-> entity-ns str (str/replace #":" ""))
                   spec-name-kw (keyword current-ns spec-name)
                   conformer-kw (keyword current-ns (str spec-name "-conformer"))
                   conformer (s/conformer conform)
                   sdef `(s/def ~spec-name-kw (s/keys :req ~(vec required-attributes)))
                   sdef2 `(s/def ~conformer-kw ~conformer)]
               ;;(prn sdef)
               ;;(prn sdef2)
               (eval sdef)
               (eval sdef2))))))))


(comment
  (def dsl {:person {:first-name [:string]
                     :last-name [:string]
                     :phones [:string :many]
                     :emails [:string :many]}
            :address {:line1 [:string]
                      :line2 [:string]
                      :city [:string]
                      :state [:string]
                      :zip [:string]
                      :country [:string]
                      }})
  (def schema (generate-schema dsl))
  
  (require '[datomic.api :as d])
  
  (def uri "datomic:free://localhost:4334/example")
  (prn "created?=" (d/create-database uri))
  (def conn (d/connect uri)) 
  
  (d/transact conn schema)
  (def data [{:person/first-name "Sun"
              :person/last-name "Tzu"
              :person/phones ["215-123-4567" "267-123-4567"]
              :person/emails ["sto@foobar.com"]}])
  (d/transact conn data)
  )
