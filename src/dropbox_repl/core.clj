(ns dropbox-repl.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [clj-time.format :as tf]
            [clj-http.client :refer [post]]
            [cheshire.core :refer [parse-string generate-string]]
            [clojure.walk :refer [keywordize-keys]]
            [dropbox-repl.util :as util])
  (:gen-class)
  (:import (java.io File RandomAccessFile)
           (java.nio.file.attribute FileAttribute)
           (java.nio.file Files)))

;;; Follow the instructions in the GitHub page on how
;;; to generate an access token for your Dropbox REPL.
;;; Then put the value of your token in the profiles.clj.
(def access-token (env :access-token))

(assert access-token
        "Access token is nil. Please follow the usage instructions on the
        GitHub page at https://github.com/jaydeesimon/dropbox-repl")

(defn- merge*
  "Deep-merge a list of maps."
  [& vals]
  (if (every? map? vals)
    (apply merge-with merge* vals)
    (last vals)))

(def ^:private default-req
  {:coerce :always ; exceptions will also be parsed as JSON
   :as :json
   :throw-entire-message? true
   :headers {:authorization (str "Bearer " access-token)}})

(defn- rpc-request
  ([url] (rpc-request url {}))
  ([url params]
   (let [req {:content-type :json
              :body (if (seq params) (generate-string params) "null")}]
     (post url (merge* default-req req)))))

(defn- content-upload-request [url file params]
  (let [req {:content-type "application/octet-stream"
             :body file
             :headers {:Dropbox-API-Arg (generate-string params)}}]
    (post url (merge* default-req req))))

;; REVIEW: I'm pretty sure the input stream from the response (:body resp)
;; will be closed after it's done reading but I should probably double-check.
;; If I include the input stream in the with-open, it throws an exception,
;; probably because the stream is closed twice.
(defn- content-download-request [path dest-dir]
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
  (:body (rpc-request "https://api.dropboxapi.com/2/users/get_current_account")))

(defn get-account [account-id]
  (:body (rpc-request "https://api.dropboxapi.com/2/users/get_account"
                       {:account_id account-id})))

(defn get-account-batch [account-ids]
  (:body (rpc-request "https://api.dropboxapi.com/2/users/get_account_batch"
                       {:account_ids account-ids})))

(defn get-space-usage []
  (:body (rpc-request "https://api.dropboxapi.com/2/users/get_space_usage")))

;;;;;;;;;;;;;;;;;;;; FILES-RELATED ENDPOINTS ;;;;;;;;;;;;;;;;;;;;;;
;; https://www.dropbox.com/developers/documentation/http/documentation#files-copy

(defn- sanitize [path]
  (cond (= "/" path) ""
        (= "" path) ""
        (not (str/starts-with? path "/")) (str "/" path)
        :else path))

(defn list-folder
  ([path] (list-folder path {}))
  ([path optional]
   (let [params (merge {:path (sanitize path)} optional)]
     (:body (rpc-request "https://api.dropboxapi.com/2/files/list_folder"
                          params)))))

(defn list-folder-continue [cursor]
  (:body (rpc-request "https://api.dropboxapi.com/2/files/list_folder/continue"
                       {:cursor cursor})))

(defn parse-timestamps [entry & time-keywords]
  (reduce (fn [entry time-key]
            (if-let [dt (tf/parse (time-key entry))]
              (assoc entry (keyword (str (name time-key) "_dt")) dt)
              entry))
          entry
          time-keywords))

(defn- enhance-entry [entry]
  (-> entry
      (parse-timestamps :client_modified :server_modified)))

(defn list-entries-lazy
  "Returns a lazy sequence of entries in a path. Probably
  don't want to use this directly. Use list-entries instead
  with the usual map, filter, take lazy functions."
  ([path optional] (list-entries-lazy path optional nil))
  ([path optional cursor]
   (let [f (if (nil? cursor)
             (fn [] (list-folder path optional))
             (fn [] (list-folder-continue cursor)))
         results (f)]
     (when (seq (:entries results))
       (lazy-cat (:entries results)
                 (when (:has_more results)
                   (list-entries-lazy path optional (:cursor results))))))))

(defn list-entries
  "A lazy sequence of all entries recursively given a path."
  ([path] (list-entries path {:recursive true}))
  ([path optional]
   (map enhance-entry (list-entries-lazy path optional))))

(defn copy [from-path to-path]
  (:body (rpc-request "https://api.dropboxapi.com/2/files/copy"
                       {:from_path from-path
                        :to_path   to-path})))

(defn create-folder [path]
  (:body (rpc-request "https://api.dropboxapi.com/2/files/create_folder"
                       {:path path})))

(defn delete [path]
  (:body (rpc-request "https://api.dropboxapi.com/2/files/delete"
                       {:path path})))

(defn move [from-path to-path]
  (:body (rpc-request "https://api.dropboxapi.com/2/files/move"
                       {:from_path from-path
                        :to_path   to-path})))

(defn op-file-to-folder [op file-path folder-path]
  (let [filename (util/name-from-path file-path)
        dest (util/append folder-path filename)]
    (op file-path dest)))

(defn move-file-to-folder
  "Given a path to a file, moves it to a folder,
  keeping the same name. If the destination folder
  does not exist, it will be created."
  [file-path folder-path]
  (op-file-to-folder move file-path folder-path))

(defn copy-file-to-folder
  "Given a path to a file, copies it to a folder,
  keeping the same name. If the destination folder
  does not exist, it will be created."
  [file-path folder-path]
  (op-file-to-folder copy file-path folder-path))

(defn search
  ([path query] (search path query {}))
  ([path query optional]
   (let [params (merge {:path (sanitize path) :query query} optional)]
     (:body (rpc-request "https://api.dropboxapi.com/2/files/search"
                          params)))))

(defn get-metadata
  ([path] (get-metadata path {}))
  ([path optional]
   (let [params (merge {:path path} optional)]
     (:body (rpc-request "https://api.dropboxapi.com/2/files/get_metadata"
                          params)))))

(defn upload-whole [file path]
  (:body (content-upload-request
           "https://content.dropboxapi.com/2/files/upload"
           (io/as-file file)
           {:path path})))

(defn download [path dest-dir]
  (content-download-request path dest-dir))

(defn- upload-start [file]
  (:body (content-upload-request
           "https://content.dropboxapi.com/2/files/upload_session/start"
           (io/as-file file)
           {})))

(defn- upload-append [file session-id offset]
  (content-upload-request
    "https://content.dropboxapi.com/2/files/upload_session/append_v2"
    (io/as-file file)
    {:cursor {:session_id session-id :offset offset}}))

(defn- upload-finish [file session-id offset path optional]
  (:body (content-upload-request
           "https://content.dropboxapi.com/2/files/upload_session/finish"
           (io/as-file file)
           (merge* {:cursor {:session_id session-id :offset offset}
                    :commit {:path path}}
                   optional))))

(defn- MB [n]
  (* n 1048576))

(defn- byte-ranges [start end step]
  (partition 2 1 (let [r (range start end step)]
                   (if (< (last r) end)
                     (concat r [end])
                     r))))

(defn- write-file-parts [^File file ^File dest-dir chunk-size-mb]
  (let [ranges (byte-ranges 0 (.length file) (MB chunk-size-mb))]
    (with-open [raf (RandomAccessFile. file "r")]
      (doall (map-indexed (fn [i [start end]]
                            (let [file-part (io/file dest-dir (str "part-" (format "%03d" i) "-" (str start)))
                                  buf (byte-array (- end start))]
                              (with-open [os (io/output-stream file-part)]
                                (.seek raf start)
                                (.read raf buf)
                                (.write os buf)
                                {:file-part file-part :offset start})))
                          ranges)))))

;; Assert that there's at least two parts. You can't use this if there isn't.
(defn- upload-file-parts
  ([file-parts path] (upload-file-parts file-parts path (fn [& args])))
  ([file-parts path progress-fn]
   (let [{:keys [session_id]} (upload-start (:file-part (first file-parts)))
         _ (progress-fn session_id 0 (count file-parts))
         num-parts (count (rest file-parts))]
     (last (map-indexed (fn [part-num {:keys [file-part offset]}]
                          (let [upload-result (if (not= (inc part-num) num-parts)
                                                (upload-append file-part session_id offset)
                                                (upload-finish file-part session_id offset path {}))]
                            (do
                              (progress-fn session_id (inc part-num) (count file-parts))
                              upload-result)))
                        (rest file-parts))))))

(defn- create-temp-dir [prefix]
  (.toFile (Files/createTempDirectory prefix (into-array FileAttribute []))))

(defn- delete-dir [dir]
  (reduce #(and %1 %2)
          (map #(.delete %) (reverse (file-seq dir)))))

(defn- upload-parts
  "Break a file into 10 MB parts and upload them.
  Why 10 MB? I'm going for something that is somewhat
  large but will not cause an OutOfMemory exception."
  [file path]
  (let [work-dir (create-temp-dir "dropbox-repl")]
    (try
      (-> (write-file-parts file work-dir 10)
          (upload-file-parts path))
      (finally
        (delete-dir work-dir)))))

(defn upload [file path]
  (if (< (.length file) (MB 150))
    (upload-whole file path)
    (upload-parts file path)))

;;;;;;;;;;;;;;;;;;;; SHARING-RELATED ENDPOINTS ;;;;;;;;;;;;;;;;;;;;;;
;; https://www.dropbox.com/developers/documentation/http/documentation#sharing-add_folder_member

(defn get-shared-links
  ([] (get-shared-links ""))
  ([path] (:body (rpc-request "https://api.dropboxapi.com/2/sharing/get_shared_links" {:path path}))))

(defn revoke-shared-link [url]
  (rpc-request "https://api.dropboxapi.com/2/sharing/revoke_shared_link"
                {:url url}))

(defn get-shared-link-metadata
  ([url] (get-shared-link-metadata url {}))
  ([url optional]
   (:body (rpc-request "https://api.dropboxapi.com/2/sharing/get_shared_link_metadata"
                        (merge {:url url} optional)))))

;;;;;;;;;;;;;;;;;;;; USEFUL FNS ;;;;;;;;;;;;;;;;;;;;;;
(defn tag= [tag entry]
  (= (:.tag entry) (name tag)))

(defn file-type? [entry exts]
  (and (tag= :file entry)
       (some #(str/ends-with? (:path_lower entry) %) exts)))

(defn image? [entry]
  (let [exts [".tif" ".tiff" ".gif" ".jpeg" ".jpg" ".png"]]
    (file-type? entry exts)))

(defn video? [entry]
  (let [exts [".mov" ".mp4" ".mpg" ".wmv" ".mkv" ".avi"]]
    (file-type? entry exts)))

(defn ebook? [entry]
  (let [exts [".epub" ".mobi" ".azw" ".lit" ".odf"]]
    (file-type? entry exts)))

(defn media? [entry]
  (or (image? entry) (video? entry)))


