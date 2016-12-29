(ns unir.flexget
  #?(:clj (:gen-class))
  (:require [unir.config :refer [config]]
            [unir.yaml :as yaml]
            [clj-trakt.users :as users]
            [clj-trakt.shows :as shows]
            [clojure.set :refer [difference]]
            #?(:cljs [planck.core :refer [slurp spit]])))

(defn returning?
  [s]
  (let [show (shows/summary (:trakt @config)
                            (str (get-in s [:show :ids :slug])
                                 "?extended=full"))]
    (condp = (:status show)
      "returning series" true
      "in production" true
      false)))

(defn refresh-series!
  []
  (let [watchlist (->> (users/watchlist (:trakt @config) :me :shows)
                       (filter #(contains? % :show))
                       (filter returning?)
                       (map #(get-in % [:show :title])))
        watched (->> (users/watched (:trakt @config) :me :shows)
                     (filter returning?)
                     (map #(get-in % [:show :title])))
        dropped-shows (->> (get-in @config [:flexget :dropped_slug])
                           (users/list-items (:trakt @config) :me)
                           (map #(get-in % [:show :title])))]
    (->> (difference (set (apply merge watchlist watched))
                     (set dropped-shows))
         vec
         (assoc-in {} [:series :720p])
         yaml/dump
         (spit (-> @config :flexget :series-file)))))

(defn -main
  []
  (refresh-series!))
