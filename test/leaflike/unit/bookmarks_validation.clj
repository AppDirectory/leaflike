(ns leaflike.unit.bookmarks-validation
  (:require  [clojure.test :refer :all]
             [leaflike.bookmarks.spec :refer :all]))

(deftest id-test
  (testing "valid id"
    (is (id? "41")))

  (testing "invalid id"
    (is (not (id? "abc"))))

  (testing "nil is not a valid id"
    (is (not (id? nil)))))

(deftest title-test
  (testing "valid title"
    (is (title? "something important")))

  (testing "invalid title"
    (is (not (title? "")))
    (is (not (title? nil)))))

(deftest url-test
  (testing "valid url"
    (is (url? "http://google.com")))

  (testing "invalid url"
    (is (not (url? "")))
    (is (not (url? "foobar")))
    (is (not (url? nil)))))

(deftest bookmark-test
  (let [good-bookmark {:title "google"
                       :url "http://google.com"}]
    (testing "valid bookmark"
      (is (valid-bookmark? good-bookmark))
      (is (valid-bookmark? (assoc good-bookmark
                                  :tags "search,google")))
      (is (valid-bookmark? (assoc good-bookmark
                                  :id "12"))))


    (testing "invalid bookmarks"
      (is (not (valid-bookmark? (dissoc good-bookmark :url))))
      (is (not (valid-bookmark? (dissoc good-bookmark :title)))))))
