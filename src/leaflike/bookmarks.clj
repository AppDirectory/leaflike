(ns leaflike.bookmarks
  (:require [leaflike.bookmarks.spec :refer [valid-bookmark?]
             :as bm-spec]
            [leaflike.bookmarks.db :as bm-db]
            [leaflike.bookmarks.uri :as uri]
            [leaflike.tags.db :as tags-db]
            [leaflike.user.db :as user-db]
            [leaflike.user.auth :refer [throw-unauthorized]]
            [leaflike.utils :as utils]
            [leaflike.handler-utils :as hutils]
            [leaflike.layout :as layout]
            [leaflike.bookmarks.views :as views]
            [net.cgrand.enlive-html :as html]
            [ring.middleware.anti-forgery :as anti-forgery]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [ring.util.response :as res]))

(defn parse-tags [tags]
  (let [[tag-type tag-value] (s/conform ::bm-spec/tags tags)]
    (case tag-type
      :nil             []
      :string          [tag-value]
      :coll-of-strings tag-value)))

(defn create
  [{:keys [params] :as request}]
  (if (s/valid? ::bm-spec/bookmark params)
    (let [user (hutils/get-user request)
          bookmark (-> (select-keys params [:title :url])
                       (assoc :user_id (:id user)
                              :created_at (utils/get-timestamp)))
          tags (parse-tags (:tags params))
          bookmark-id (bm-db/create bookmark)
          next-url (:next params "/bookmarks")]
      (when (not-empty tags)
        (tags-db/create tags)
        (bm-db/tag-bookmark bookmark-id tags))
      (-> (res/redirect next-url)
          (assoc-in [:headers "Content-Type"] "text/html")
          (assoc-in [:flash :success-msg] "Bookmark added.")))
    (assoc (res/redirect "/bookmarks/add")
           :flash {:error-msg (bm-spec/describe-errors params)})))

(defn edit
  [{:keys [params] :as request}]
  (if (s/valid? ::bm-spec/bookmark params)
    (let [user         (hutils/get-user request)
          bookmark-id  (Integer/parseInt (:id params))
          updated-keys (select-keys params [:title :url])
          tags         (parse-tags (:tags params))]
      (bm-db/update-bookmark bookmark-id (:id user) updated-keys)
      (bm-db/remove-all-tags bookmark-id)
      (when (not-empty tags)
        (tags-db/create tags)
        (bm-db/tag-bookmark bookmark-id tags))
      (-> (res/redirect "/bookmarks")
          (assoc-in [:headers "Content-Type"] "text/html")))
    (assoc (res/redirect (str "/bookmarks/edit/" (:id params)))
           :flash {:error-msg (bm-spec/describe-errors params)})))

(def items-per-page 10)

(defn fetch-bookmarks
  [username {:keys [tag search-terms] :as params}]
  (let [user (hutils/get-user {:session {:username username}})
        page (dec (:page params))
        query {:user-id (:id user)
               :tag tag
               :search-terms search-terms}
        bookmarks (bm-db/fetch-bookmarks (merge query
                                                {:limit items-per-page
                                                 :offset (* items-per-page page)}))
        num-bookmarks (bm-db/count-bookmarks query)]
    {:bookmarks bookmarks
     :num-pages (max 1
                     (int (Math/ceil (/ num-bookmarks items-per-page))))}))

(defn list-by-id
  [{:keys [route-params] :as request}]
  (let [id        (:id route-params)
        user      (hutils/get-user request)
        params    {:id id :user-id (:id user)}]
    (if (s/valid? :leaflike.bookmarks.spec/id id)
      (bm-db/list-by-id params)
      (assoc (res/redirect "/bookmarks")
             :flash {:error-msg "Invalid bookmark id"}))))

(defn delete
  [{:keys [params] :as request}]
  (let [user      (hutils/get-user request)
        unparsed-id (:id params)]
    (if (s/valid? :leaflike.bookmarks.spec/id unparsed-id)
      (let [id (Integer/parseInt unparsed-id)]
        (do (bm-db/remove-all-tags id)
            (bm-db/delete {:id id
                           :user-id (:id user)})
            (assoc (res/redirect "/bookmarks")
                   :flash {:success-msg "Successfully deleted bookmark"})))
      (assoc (res/redirect "/bookmarks")
             :flash {:error-msg "Invalid bookmark id"}))))

(defn- view-type-info
  [view-type {:keys [search-query tag] :as params}]
  (case view-type
    :all-bookmarks {:page-title "Bookmarks"
                    :path-format-fn (partial uri/page params)}
    :tag-bookmarks {:page-title (str "Bookmarks with tag: " tag)
                    :path-format-fn (partial uri/tag-page params)}
    :search-bookmarks {:page-title (str "Search results for: " search-query)
                       :path-format-fn (partial uri/search params)}))

(defn current-page [page]
  (if (string/blank? page)
    1
    (Integer/parseInt page)))

(defn parse-search-terms [search-query]
  (when-not (string/blank? search-query)
    (string/split search-query #" ")))


(defn list-view
  [username
   {:keys [page] :as params}
   {:keys [bookmarks num-pages] :as paginated-bookmarks}
   view-type
   & {:keys [error-msg success-msg]}]
  (let [{:keys [page-title path-format-fn]} (view-type-info view-type params)]
    (layout/user-view page-title
                      username
                      (views/list-all anti-forgery/*anti-forgery-token*
                                      bookmarks
                                      num-pages
                                      page
                                      path-format-fn)
                      :error-msg error-msg
                      :success-msg success-msg)))

(defn valid-page-number? [{:keys [current-page num-pages]}]
  (< 0 current-page (inc num-pages)))

(defn- bookmarks-list
  [view-type {:keys [session] :as request}]
  (let [username (:username session)
        search-terms (parse-search-terms (get-in request [:params :search-query]))
        params (-> (:params request)
                   (update :page current-page)
                   (assoc :search-terms search-terms))
        error-msg (get-in request [:flash :error-msg])
        success-msg (get-in request [:flash :success-msg])
        current-page (:page params)
        invalid-page-response (assoc (res/redirect "/bookmarks")
                                     :flash {:error-msg "Invalid page number"})]
    (if (pos? current-page)
      (let [{:keys [bookmarks
                    num-pages] :as paginated-bookmarks} (fetch-bookmarks username params)]
        (if (valid-page-number? {:current-page (:page params)
                                 :num-pages num-pages})
          (-> (res/response (list-view username
                                       params
                                       paginated-bookmarks
                                       view-type
                                       :error-msg error-msg
                                       :success-msg success-msg))
              (assoc :headers {"Content-Type" "text/html"}))
          invalid-page-response))
      invalid-page-response)))

(def all-bookmarks-view (partial bookmarks-list :all-bookmarks))
(def tag-bookmarks-view (partial bookmarks-list :tag-bookmarks))
(def search-bookmarks-view (partial bookmarks-list :search-bookmarks))

(defn create-view
  [{:keys [params] :as request}]
  (let [username (get-in request [:session :username])
        error-msg (get-in request [:flash :error-msg])
        user      (hutils/get-user request)
        next-url (:next params "/bookmarks")
        all-tags (map :name (tags-db/fetch-tags {:user-id (:id user)}))
        bookmark (assoc (select-keys params [:title :url])
                        :all-tags all-tags)]
    (-> (res/response (layout/user-view "Add Bookmark"
                                        username
                                        (views/bookmark-form anti-forgery/*anti-forgery-token*
                                                             (str "/bookmarks/add?next=" next-url)
                                                             bookmark)
                                        :error-msg error-msg))
        (assoc-in [:headers "Content-Type"] "text/html"))))

(defn edit-view
  [{:keys [params] :as request}]
  (let [username (get-in request [:session :username])
        user      (hutils/get-user request)
        bookmark-id (Integer/parseInt (:bookmark-id params))
        bookmark (bm-db/fetch-bookmark bookmark-id (:id user))
        error-msg (if bookmark
                    (get-in request [:flash :error-msg])
                    "The bookmark you're trying to edit does not exist.")
        all-tags (map :name (tags-db/fetch-tags {:user-id (:id user)}))]
    (-> (res/response (layout/user-view "Edit Bookmark"
                                        username
                                        (views/bookmark-form anti-forgery/*anti-forgery-token*
                                                             "/bookmarks/edit"
                                                             (assoc bookmark
                                                                    :all-tags all-tags))
                                        :error-msg error-msg))
        (assoc-in [:headers "Content-Type"] "text/html"))))

(defn pocket-import-form
  [{:keys [params] :as request}]
  (-> (res/response (layout/user-view "Import from pocket"
                                      (get-in request [:session :username])
                                      (views/pocket-import-form anti-forgery/*anti-forgery-token*)))
      (assoc-in [:headers "Content-Type"] "text/html")))

(defn import-bookmarks-from-pocket
  [html-str username]
  (let [bookmark-nodes (->
                        html-str
                        html/html-snippet
                        (html/select [:li :a]))
        get-url (fn [node]
                  (-> node :attrs :href))
        get-title (fn [node]
                    (html/text node))
        get-tags (fn [node] (let [tags-str (-> node :attrs :tags)]
                              (if (string/blank? tags-str)
                                nil
                                (string/split tags-str #","))))]
    (doseq [node bookmark-nodes]
      (create
       {:params {:title (get-title node)
                 :url (get-url node)
                 :tags (get-tags node)}
        :session {:username username}}))))

(defn pocket-import
  [{:keys [multipart-params] :as request}]
  (let [username (get-in request [:session :username])
        file (get-in multipart-params ["pocket_html" :tempfile])]
    (do (import-bookmarks-from-pocket (slurp file) username)
        (-> (res/redirect "/bookmarks")
            (assoc :flash {:success-msg "Successfully imported from Pocket"})))))
