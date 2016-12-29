(ns unir.rules
  "Common rules for cleaning filenames and other operations"
  (:require [clojure.string :as string]))

(defn char-map
  [s c]
  (apply hash-map (interleave s (repeat (count s) c))))

(def space-chars
  (-> #{\. \_}
      (char-map " ")))

(def ignored-chars
  (-> #{\? \(\) \: \'}
      (char-map "")))

(def remap-rules
  {#"[\.| ]?percent" \%
   "s.h.i.e.l.d" "shield"})

(defn clean-name
  ([s]
   (clean-name s {:remap? true
                  :ignored? true
                  :spaces? true}))
  ([s {:keys [remap? ignored? spaces?]
       :or {remap? true
            spaces? true
            ignored? true}
       :as opts}]
   (-> (reduce (fn [accl c]
                 (apply (partial string/replace accl)
                        (map #(cond-> % (char? %) str) c)))
               (string/lower-case s)
               (merge {}
                      (when remap? remap-rules)
                      (when spaces? space-chars)
                      (when ignored? ignored-chars)))
       string/lower-case
       (string/replace #"[ ]+" " "))))
