(ns demo.ui.layout
  (:require
   [dev.onionpancakes.chassis.core :as c]
   [dev.onionpancakes.chassis.compiler :as cc]
   [starfederation.datastar.clojure.api :as d*]))

(defn document-layout
  [title main-content-fragment]
  (cc/compile
   [c/doctype-html5
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title title]
      [:link {:href "https://cdn.jsdelivr.net/npm/daisyui@5" :rel "stylesheet" :type "text/css"}]
      [:script {:src "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4"}]
      [:script {:type "module" :src d*/CDN-url}]]
     [:body
      main-content-fragment]]]))
