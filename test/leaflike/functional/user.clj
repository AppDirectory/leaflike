(ns leaflike.functional.user
  (:require [leaflike.user.spec :refer :all]
            [leaflike.user.db :as user-db]
            [leaflike.fixtures :refer [wrap-setup]]
            [clojure.test :refer :all]))

(use-fixtures :each wrap-setup) ; wrap-setup around the whole namespace of tests.
                                ; use :each to wrap around each individual test

(deftest registration-test
  (testing "user registration is invalid. missing email"
    (let [user {:email    ""
                :username "abc"
                :password "1"}
          result (valid-signup-details? user)]
      (is (= false result))))

  (testing "user registration is invalid. missing password"
    (let [user {:email    "b@b.com"
                :username "abc"
                :password ""}
          result (valid-signup-details? user)]
      (is (= false result))))

  (testing "user registration is valid"
    (let [user {:email    "a@a.com"
                :username "abc"
                :password "1"}
          result (valid-signup-details? user)]
      (is (= true result))
      (user-db/create-user user)))

  (testing "user registration is invalid. duplicate email"
    (let [user {:email    "a@a.com"
                :username "abk"
                :password "1"}
          result (valid-signup-details? user)]
      (is (= false result))))

  (testing "user registration is invalid. duplicate username"
    (let [user {:email    "a@ak.com"
                :username "abc"
                :password "1"}
          result (valid-signup-details? user)]
      (is (= false result)))))

(deftest login-test

  (let [user  {:email    "a@b.com"
               :username "a"
               :password "1"}]
    (user-db/create-user user))

  (testing "user login is valid"
    (let [body {:username "a"
                :password "1"}

          res  (valid-login-details? body)]

      (is (and (not-empty res)
               (contains? res :username)
               (contains? res :password)
               (contains? res :email)
               (contains? res :id)))))

  (testing "user login is invalid. Missing data"
    (let [body {:username "a"
                :password ""}
          res  (valid-login-details? body)]

      (not res))))
