(ns unir.core
  (:refer-clojure :exclude [read-string test])
  (:require [unir.config :refer [config spit-config!]]
            [unir.yaml :as yaml]
            [unir.transmission :as transmission]
            [unir.flexget :refer [refresh-series!]]
            [clj-trakt.core :as trakt]
            [clj-trakt.auth :as auth]
            [clj-trakt.users :as users]
            [clj-trakt.episodes :as episodes]
            [clojure.string :as string]
            [#?(:clj clojure.edn :cljs cljs.reader) :refer [read-string]]
            [#?(:clj clojure.pprint :cljs cljs.pprint) :refer [cl-format]]
            #?(:clj [me.raynes.conch.low-level :as sh]
               :cljs [planck.shell :refer [sh]])))

(defn shell
  [& args]
  #?(:clj (sh/stream-to-string (apply sh/proc args) :out)
     :cljs (:out (apply sh args))))

(def env
  (as-> (shell "env") e
    (string/split e #"\n")
    (map #(string/split % #"=" 2) e)
    (into {} e)))

(defn refresh-token!
  []
  (->> (auth/refresh-token (:trakt @config))
       (merge (:trakt @config))
       (assoc @config :trakt)
       (reset! config)
       spit-config!))

(defn symlink?
  [filename]
  (= 0 (-> (apply #?(:clj sh/proc :cljs sh)
                  ["test" "-h" (str (-> @config :transmission :downloads)
                                    filename)])
           #?(:clj sh/exit-code
              :cljs :exit))))

(defn link
  [source destination]
  (shell "mv" source destination)
  (shell "ln" "-s" destination source))

(defn process-movie
  [torrent]
  (let [watchlist (users/watchlist (:trakt @config) :me)
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

(defn trim-leading-zeroes
  [s]
  (string/replace s #"^0+" ""))

(defn production-code
  [torrent-name]
  (->> (re-find #"[sS]([0-9]+)[eE]([0-9]+)" torrent-name)
       (drop 1)
       (map (comp read-string trim-leading-zeroes))))

(defn process-episode
  [show file]
  (when (and (transmission/file-completed? file)
             (some #(string/ends-with? (:name file) %)
                   ["mkv" "mov" "mp4" "avi"])
             (not (symlink? (:name file))))
    (let [code (production-code (:name file))
          episode (episodes/summary (:trakt @config) (get-in show [:ids :slug])
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
  (let [watchlist (users/watchlist (:trakt @config) :me)
        shows-watched (users/watched (:trakt @config) :me :shows true)
        shows (->> (apply merge
                          shows-watched
                          (filter #(contains? % :show) watchlist))
                   (map :show))
        show (->> shows
                  (filter #(apply string/includes?
                                  [(string/lower-case (:clean-name torrent))
                                   (string/replace (-> (:title %)
                                                       string/lower-case)
                                                   #"[:()?']" "")]))
                  first)]
    (doall (map #(process-episode show %) (:files torrent)))))

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
  [& [torrent-name]]
  (refresh-token!)
  (process torrent-name))
