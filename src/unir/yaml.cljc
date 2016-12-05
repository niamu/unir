(ns unir.yaml
  (:refer-clojure :exclude [load])
  (:require #?(:clj [clj-yaml.core :as yaml]
               :cljs [cljsjs.js-yaml])))

(defn load
  [s]
  #?(:clj (-> s yaml/parse-string)
     :cljs (-> (.safeLoad js/jsyaml s)
               (js->clj :keywordize-keys true))))

(defn dump
  [s]
  #?(:clj (yaml/generate-string s :dumper-options {:flow-style :block})
     :cljs (->> (clj->js s)
                (.safeDump js/jsyaml))))
