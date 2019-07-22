(ns spectacular.core
  (:require [clojure.string :as str]
            [stigmergy.tily :as util]))

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
