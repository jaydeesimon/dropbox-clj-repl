(ns dropbox-repl.util
  (:require [clojure.string :as str]))

(defn name-from-path [path]
  (last (str/split path #"/")))

(defn append
  "Append two paths together, removing duplicated slashes."
  [p1 p2]
  (.replaceAll (str p1 "/" p2) "/+" "/"))
