(defproject stigmergy/spectacular "0.0.1-SNAPSHOT"
  :description "Schema generator for Datomic that won't set your boots alight"
  :url "http://www.github.com/Yuppiechef/datomic-schema"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [stigmergy/tily "0.1.7-SNAPSHOT"]]
  
  :source-paths ["src/cljc"]
  :profiles {:dev {:dependencies [[com.datomic/datomic-free "0.9.5697"]]}})
