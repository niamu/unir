(ns unir.config
  (:require [unir.yaml :as yaml]
            [planck.core :refer [slurp spit]]))

(def config
  (atom (-> (slurp "resources/config.yaml")
            yaml/load)))

(defn spit-config!
  [config]
  (->> config
       yaml/dump
       (spit "resources/config.yaml")))
