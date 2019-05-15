# schema-dsl

Spectacular is a fork of [datomic-schema](https://github.com/Yuppiechef/datomic-schema) refactored to be data-driven
without the use of macros. Added support for clojure.spec validation for validation datomic data from 
both Clojure and ClojureScript

## 1.3.1 API Breaking change

Spectacular borrows ideas and syntax from [datomic-schema](https://github.com/Yuppiechef/datomic-schema) but
without the use of macros. The DSL syntax is similar but API is different.

## Example

Pending...

## Usage

In leiningen, simply add this to your dependencies

```clojure
[stigmergy/spectacular "0.0.1"]
```

A picture speaks a thousand words. I don't have a picture, but here's some code:

```clojure
(defonce db-url "datomic:mem://testdb")

(defdbfn dbinc [db e a qty] :db.part/user
  [[:db/add e a (+ qty (or (get (d/entity db e) a) 0))]])

(defn dbparts []
  [(part "app")])

(defn dbschema []
  [(schema user
    (fields
     [username :string :indexed]
     [pwd :string "Hashed password string"]
     [email :string :indexed]
     [status :enum [:pending :active :inactive :cancelled]]
     [group :ref :many]))
   
   (schema group
    (fields
     [name :string]
     [permission :string :many]))])

(defn setup-db [url]
  (d/create-database url)
  (d/transact
   (d/connect url)
   (concat
    (s/generate-parts (dbparts))
    (s/generate-schema (dbschema))
    (s/dbfns->datomic dbinc)))))

(defn -main [& args]
  (setup-db db-url)
  (let [gid (d/tempid :db.part/user)]
    (d/transact
     db-url
     [{:db/id gid
       :group/name "Staff"
       :group/permission "Admin"}
      {:db/id (d/tempid :db.part/user)
       :user/username "bob"
       :user/email "bob@example.com"
       :user/group gid
       :user/status :user.status/pending}])))
```

You can play around with the example project if you want to see this in action.

The crux of this is in the (s/generate-parts) and (s/generate-schema), which turns your parts and schemas into a nice long list of datomic schema transactions.

Also notice that :enum resolves to a :ref type, the vector can be a list of strings: ["Pending" "Active" "Inactive" "cancelled"] or a list of keywords as shown. String will be converted to keywords by lowercasing and converting spaces to dashes, so "Bad User" will convert to :user.status/bad-user.

Lastly, the result of (s/schema) and (s/part) are simply just datastructures - you can build them up yourself, add your own metadata or store them off. Your call.

## Possible keys to put on a field:

Just a list of keys you'd be interested to use on fields - look at http://docs.datomic.com/schema.html for more detailed info

```clojure
;; Types
:keyword :string :boolean :long :bigint :float :double :bigdec :ref :instant
:uuid :uri :bytes :enum

;; Options
:unique-value :unique-identity :indexed :many :fulltext :component
:nohistory "Some doc string" [:arbitrary "Enum" :values]
:alter!
```

### Altering schema

If you need to update an option of an existing field - add an `:alter!` option
key. This way a `:db.alter/_attribute` will be generated instead of a default
`:db.install/_attribute`.

## Datomic defaults:
Datomic has defaults for:

```
:db/index <false>
:db/fulltext <false>
:db/noHistory <false>
:db/component <false>
:db/doc <"">
```
The default behavior of `generate-schema` is to explicitly generate these defaults.

This behavior can be overridden by passing in `:gen-all?` as `false`:

```
(s/generate-schema schema {:gen-all? false})
```

Passing `:gen-all` as `false` will elide those Datomic default keys, unless of course your `schema`
defines non-default values.

Note, that Datomic requires that `:db/cardinality` be explicitly set for each attribute installed. `generate-schema` will default to `:db.cardinality/one` unless the `schema` passed in specifies otherwise.

## Indexing

By default, attributes have `:db/index false`. If you would like every attribute in your schema to have `:db/index true` then simply include `:index-all? true` in your `generate-schema` call:

```
(s/generate-schema schema {:index-all? true})
```

## License

Copyright © 2013 Yuppiechef Online (Pty) Ltd.

Distributed under The MIT License (MIT) - See LICENSE.txt
