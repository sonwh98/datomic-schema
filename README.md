# Spectacular

Spectacular is inspired by [datomic-schema](https://github.com/Yuppiechef/datomic-schema) refactored to be data-driven
without the use of macros. Added support for clojure.spec validation for validation datomic data from 
both Clojure and ClojureScript

## Example
```Clojure
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
```

## Usage

In leiningen, simply add this to your dependencies

```clojure
[stigmergy/spectacular "0.0.1-SNAPSHOT"]
```

## Possible keys to put on a field:

```clojure
;; Types
:keyword :string :boolean :long :bigint :float :double :bigdec :ref :instant
:uuid :uri :bytes :enum

;; Options
:unique-value :unique-identity :indexed :many :fulltext :component
```
## License
Distributed under The MIT License (MIT) 
