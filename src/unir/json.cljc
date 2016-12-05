(ns unir.json
  (:require #?(:clj [clojure.data.json :as json])))

(defn write-str
  [s]
  #?(:clj (json/write-str s)
     :cljs (.stringify js/JSON (clj->js s))))

(defn read-str
  [s]
  #?(:clj (json/read-str s :key-fn keyword)
     :cljs (js->clj (.parse js/JSON s) :keywordize-keys true)))
