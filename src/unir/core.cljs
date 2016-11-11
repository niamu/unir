(ns unir.core
  (:require [unir.config :refer [config spit-config!]]
            [unir.yaml :as yaml]
            [unir.transmission :as transmission]
            [unir.flexget :refer [refresh-series!]]
            [clj-trakt.core :as trakt]
            [clj-trakt.users :as users]
            [clj-trakt.episodes :as episodes]
            [clojure.string :as string]
            [cljs.reader :refer [read-string]]
            [cljs.pprint :refer [cl-format]]
            [planck.shell :refer [sh]]))

(def env
  (as-> (:out (sh "env")) e
    (string/split e #"\n")
    (map #(string/split % #"=" 2) e)
    (into {} e)))

(def credentials
  (:trakt @config))

(defn refresh-token!
  []
  (->> (trakt/refresh-token (:trakt @config))
       (merge (:trakt @config))
       (assoc @config :trakt)
       (reset! config)
       spit-config!))

(defn symlink?
  [filename]
  (= 0 (:exit (sh "test" "-h" filename))))

(defn link
  [source destination]
  (sh "mv" source destination)
  (sh "ln" "-s" destination source))

(defn process-movie
  [torrent]
  (let [watchlist (users/watchlist credentials :me)
        movies (->> (filter #(= "movie" (:type %)) watchlist)
                    (map :movie))
        moviefile (sort-by :length (:files torrent))
        movie (->> movies
                   (filter #(string/includes? (string/replace moviefile
                                                              #"[._]" " ")
                                              (string/replace (:title %)
                                                              #"[:()?']" "")))
                   first)
        source (str (get-in @config [:transmission :downloads]) moviefile)
        destination (str (get-in @config [:transmission :destination :movie])
                         (:title movie) " (" (:year movie) ")"
                         "." (last (string/split (:name moviefile) #"\.")))]
    (link source destination)))

(defn production-code
  [torrent-name]
  (->> (re-find #"[sS]([0-9]+)[eE]([0-9]+)" torrent-name)
       (drop 1)
       (map read-string)))

(defn process-episode
  [show file]
  (when (and (transmission/file-completed? file)
             (re-matches #"[.](mkv|mov|mp4|avi)$" (:name file))
             (not (symlink? (:name file))))
    (let [code (production-code (:name file))
          episode (episodes/summary credentials (get-in show [:ids :slug])
                                    (first code) (second code))
          source (str (get-in @config [:transmission :downloads]) (:name file))
          destination (str (get-in @config [:transmission :destination :show])
                           (:title show)
                           "/" "Season " (first code) "/"
                           (:title show)
                           " - "
                           "S" (cl-format nil "~2,'0d" (first code))
                           "E" (cl-format nil "~2,'0d" (second code))
                           " - "
                           (string/replace (:title episode)
                                           #"[:()?']" "")
                           "." (last (string/split (:name file) #"\.")))]
      (link source destination))))

(defn process-show
  [torrent]
  (let [watchlist (users/watchlist credentials :me)
        shows-watched (users/watched credentials :me :shows true)
        shows (->> (merge shows-watched
                          (filter #(= "show" (:type %)) watchlist))
                   (map :show))
        show (->> shows
                  (filter #(string/includes? (:clean-name torrent)
                                             (string/replace (:title %)
                                                             #"[:()?']" "")))
                  first)]
    (map #(process-episode show %) (:files torrent))))

(defn process
  [torrent-name]
  (let [fields [:name :trackers :downloadDir :files]
        torrent (-> (if-let [torrent-id (get env "TR_TORRENT_ID")]
                      (transmission/torrent-by-id fields torrent-id)
                      (transmission/torrent-by-name fields torrent-name))
                    transmission/normalize-torrent)]
    (case (:media-type torrent)
      :show (process-show torrent)
      :movie (process-movie torrent))))

(defn -main
  [torrent-name]
  (refresh-token!)
  (process torrent-name))
