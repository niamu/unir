(ns unir.flexget
  (:require [unir.config :refer [config]]
            [unir.yaml :as yaml]
            [clj-trakt.users :as users]
            [clj-trakt.shows :as shows]
            [clojure.set :refer [difference]]
            [planck.core :refer [slurp spit]]))

(def series
  (-> (slurp "resources/series.yaml")
      yaml/load))

(def credentials
  (:trakt @config))

(defn returning?
  [s]
  (let [show (shows/summary credentials
                            (str (get-in s [:show :ids :slug])
                                 "?extended=full"))]
    (condp = (:status show)
      "returning series" true
      "in production" true
      false)))

(defn refresh-series!
  []
  (let [watchlist (->> (users/watchlist credentials :me :shows)
                       (filter returning?)
                       (map #(get-in % [:show :title])))
        watched (->> (users/watched credentials :me :shows)
                     (filter returning?)
                     (map #(get-in % [:show :title])))
        dropped-shows (->> (get-in @config [:flexget :dropped_slug])
                           (users/list-items credentials :me)
                           (map #(get-in % [:show :title])))]
    (->> (difference (set (apply merge watchlist watched))
                     (set dropped-shows))
         (assoc-in {} [:series :720p])
         yaml/dump
         (spit "resources/series.yaml"))))

(defn -main
  []
  (refresh-series!))
