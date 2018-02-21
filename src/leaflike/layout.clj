(ns leaflike.layout
  (:require [hiccup.page :refer [html5 include-css]]
            [hiccup.element :refer [link-to]]
            [hiccup.form :as f]))

(defn search-form
  []
  (f/form-to {:class "form-inline" :role "form"}
             [:get "/bookmarks/search/page/1"]
             [:span
              (f/text-field {:class "form-control" :placeholder "Search"
                             :required ""} "search_query")]))

(defn application
  [title content & {:keys [username error-msg]}]
  (html5 [:head
          [:title title]
          (include-css "//maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css")
          (include-css "/css/styles.css")
          [:body
           [:nav.navbar.navbar-expand-lg.navbar-light.bg-light
            [:a.navbar-brand {:href "/"} "Leaflike"]
            [:div.collapse.navbar-collapse
             [:ul.navbar-nav.mr-auto.mt-2.mt-lg-0
              [:li.nav-item
               (search-form)]]
             (str "Logged in as " username)
             [:a {:class "nav-link" :href "/logout"} "Logout"]]]

           [:div.container
            [:div#content
             [:h3 {:class "text-success"} title]
             (when error-msg
               [:div {:class "alert alert-danger"} error-msg])
             content]]]]))

(defn user-view
  [title username content & {:keys [error-msg]}]
  (application title content
               :username username
               :error-msg error-msg))

(defn index
  []
  [:div {:class "container"}
   [:div {:id "content"}
    [:h1 {:class "text-success"} "Welcome to Leaflike"]
    [:a {:href "/signup"} [:button {:class "btn btn-primary btn-space"} "Signup"]]
    [:a {:href "/login"}  [:button {:class "btn btn-primary btn-space"} "Login"]]]])
