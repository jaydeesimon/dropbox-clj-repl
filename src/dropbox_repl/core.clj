(ns dropbox-repl.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [clj-http.client :refer [post]]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure.walk :refer [keywordize-keys]])
  (:gen-class))

;;; Follow the instructions in the Github page on how
;;; to generate an access token for your Dropbox REPL.
;;; Then put the value of your token in the profiles.clj.
(def access-token (env :access-token))

(defn- merge*
  "Deep-merge a list of maps."
  [& vals]
  (if (every? map? vals)
    (apply merge-with merge* vals)
    (last vals)))

(def ^:private default-req
  {:as :json
   :throw-entire-message? true
   :headers {:authorization (str "Bearer " access-token)}})

(defn- default-request
  ([url] (default-request url {}))
  ([url params]
   (let [req {:content-type :json
              :body (if (seq params) (generate-string params) "null")}]
     (:body (post url (merge* default-req req))))))

(defn- upload-request [file path]
  (let [url "https://content.dropboxapi.com/2/files/upload"
        req {:content-type "application/octet-stream"
             :body file
             :headers {:Dropbox-API-Arg (generate-string {:path path})}}]
    (:body (post url (merge* default-req req)))))

;; REVIEW: I'm pretty sure the input stream from the response (:body resp)
;; will be closed after it's done reading but I should probably double-check.
;; If I include the input stream in the with-open, it throws an exception,
;; probably because the stream is closed twice.
(defn- download-request [path dest-dir]
  (let [url "https://content.dropboxapi.com/2/files/download"
        req {:as :stream
             :headers {:Dropbox-API-Arg (generate-string {:path path})}}
        resp (post url (merge* default-req req))
        name (-> resp (get-in [:headers "dropbox-api-result"]) parse-string keywordize-keys :name)
        outfile (io/file dest-dir name)]
    (with-open [out (io/output-stream outfile)]
      (io/copy (:body resp) out))
    outfile))

;;;;;;;;;;;;;;;;;;;; USERS-RELATED ENDPOINTS ;;;;;;;;;;;;;;;;;;;;;;
;; https://www.dropbox.com/developers/documentation/http/documentation#users-get_account

(defn get-current-account []
  (default-request "https://api.dropboxapi.com/2/users/get_current_account"))

(defn get-account [account-id]
  (default-request "https://api.dropboxapi.com/2/users/get_account"
                   {:account_id account-id}))

(defn get-account-batch [account-ids]
  (default-request "https://api.dropboxapi.com/2/users/get_account_batch"
                   {:account_ids account-ids}))

(defn get-space-usage []
  (default-request "https://api.dropboxapi.com/2/users/get_space_usage"))

;;;;;;;;;;;;;;;;;;;; FILES-RELATED ENDPOINTS ;;;;;;;;;;;;;;;;;;;;;;
;; https://www.dropbox.com/developers/documentation/http/documentation#files-copy

(defn- sanitize-for-list [path]
  (cond (= "/" path) ""
        (= "" path) ""
        (not (str/starts-with? path "/")) (str "/" path)
        :else path))

(defn list-folder
  ([path] (list-folder path {}) )
  ([path optional]
   (let [params (merge {:path (sanitize-for-list path)} optional)]
     (default-request "https://api.dropboxapi.com/2/files/list_folder"
                      params))))

(defn list-folder-continue [cursor]
  (default-request "https://api.dropboxapi.com/2/files/list_folder/continue"
                   {:cursor cursor}))

(defn list-entries-lazy
  "Lazily returns the entries given a path. The sequence
  is terminated by nil so to get all of the entries you
  would do (take-while some? (list-entries-lazy path))."
  ([path] (list-entries-lazy path {:recursive true}))
  ([path optional]
   (let [init-results (list-folder path optional)
         nil-entries {:entries [nil]}]
     (mapcat :entries (iterate (fn [r]
                                 (if (:has_more r)
                                   (list-folder-continue (:cursor r))
                                   nil-entries))
                               init-results)))))

(defn list-entries
  ([path] (list-entries path {:recursive true}))
  ([path optional]
    (take-while some? (list-entries-lazy path optional))))

(defn copy [from-path to-path]
  (default-request "https://api.dropboxapi.com/2/files/copy"
                   {:from_path from-path
                    :to_path to-path}))

(defn create-folder [path]
  (default-request "https://api.dropboxapi.com/2/files/create_folder"
                   {:path path}))

(defn delete [path]
  (default-request "https://api.dropboxapi.com/2/files/delete"
                   {:path path}))

(defn move [from-path to-path]
  (default-request "https://api.dropboxapi.com/2/files/move"
                   {:from_path from-path
                    :to_path to-path}))

(defn search
  ([path query] (search path query {}))
  ([path query optional]
   (let [params (merge {:path path :query query} optional)]
     (default-request "https://api.dropboxapi.com/2/files/search"
                      params))))

(defn get-metadata
  ([path] (get-metadata path {}))
  ([path optional]
   (let [params (merge {:path path} optional)]
     (default-request "https://api.dropboxapi.com/2/files/get_metadata"
                      params))))

(defn upload [file path]
  (upload-request(io/as-file file) path))

(defn download [path dest-dir]
  (download-request path dest-dir))
