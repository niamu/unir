(ns unir.yaml
  (:require [cljsjs.js-yaml]))

(defn load
  [s]
  (-> (.safeLoad js/jsyaml s)
      (js->clj :keywordize-keys true)))

(defn dump
  [s]
  (->> (clj->js s)
       (.safeDump js/jsyaml)))
