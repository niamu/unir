(ns unir.core
  #?(:clj (:gen-class))
  (:refer-clojure :exclude [read-string test])
  (:require [unir.config :refer [config spit-config!]]
            [unir.yaml :as yaml]
            [unir.rules :as rules]
            [unir.transmission :as transmission]
            [unir.flexget :refer [refresh-series!]]
            [clj-trakt.core :as trakt]
            [clj-trakt.auth :as auth]
            [clj-trakt.users :as users]
            [clj-trakt.episodes :as episodes]
            [clojure.string :as string]
            #?(:clj [clojure.java.io :as io])
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

(defn extension
  [filename]
  (last (string/split filename #"\.")))

(defn process-movie
  [torrent]
  (let [watchlist (users/watchlist (:trakt @config) :me)
        movies (->> (filter #(= "movie" (:type %)) watchlist)
                    (map :movie))
        moviefile (first (sort-by :length > (:files torrent)))
        movie (->> movies
                   (filter #(string/includes? (rules/clean-name moviefile)
                                              (rules/clean-name (:title %))))
                   first)
        source (str (get-in @config [:transmission :downloads]) moviefile)
        filename (str (:title movie) " (" (:year movie) ")")
        destination (str (get-in @config [:transmission :destination :movie])
                         filename
                         "." (extension (:name moviefile)))]
    (if movie
      (do
        (println "Matched movie:" filename)
        #?(:clj (io/make-parents destination))
        (link source destination))
      (println "No matching movie for" (:name torrent)))))

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
          filename (str (:title show)
                        " - "
                        "S" (cl-format nil "~2,'0d" (first code))
                        "E" (cl-format nil "~2,'0d" (second code))
                        " - "
                        (:title episode))
          destination (str (get-in @config [:transmission :destination :show])
                           (:title show)
                           "/" "Season " (first code) "/"
                           filename
                           "." (extension (:name file)))]
      (println "Matched episode:" filename)
      #?(:clj (io/make-parents destination))
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
                                  [(rules/clean-name (:name torrent))
                                   (rules/clean-name (:title %))]))
                  (sort-by #(count (:title %)) >)
                  first)]
    (if show
      (do
        (println "Matched show" (:title show) "for file:" (:name torrent))
        (doall (map #(process-episode show %) (:files torrent))))
      (println "No matching show for" (:name torrent)))))

(defn process
  [torrent-name]
  (let [fields [:name :trackers :downloadDir :files]
        torrent (if-let [torrent-id (get env "TR_TORRENT_ID")]
                  (transmission/torrent-by-id fields torrent-id)
                  (transmission/torrent-by-name fields torrent-name))]
    (when torrent
      (case (transmission/media-type torrent)
        :show (process-show torrent)
        :movie (process-movie torrent)))))

(defn -main
  [& [torrent-name]]
  (refresh-token!)
  (process torrent-name))
