(defproject unir "0.1.0"
  :description "Unir sorts TV and Movie files automatically"
  :url "http://github.com/niamu/unir"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/data.json "0.2.6"]
                 [me.raynes/conch "0.8.0"]
                 [clj-http "2.3.0"]
                 [cljsjs/js-yaml "3.3.1-0"]
                 [clj-yaml "0.4.0"]
                 [clj-trakt "1.0.2"]]
  :main unir.core)
