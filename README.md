# friend-oauth2

friend-oauth2 is an [OAuth2](https://en.wikipedia.org/wiki/OAuth)
workflow for [Friend][1].

OAuth2 allows you to send users to a third party site for
authentication. They are then redirected to a URL you choose on your
site with an access token. This token can be used by the server to
obtain user-authorized metadata about the user from the third party
provider.

[Working examples][2] have been implemented for:

* [Google's OAuth2](https://developers.google.com/accounts/docs/OAuth2)
* [Github's OAuth2](http://developer.github.com/v3/oauth/).
* [app.net's OAuth2](https://github.com/appdotnet/api-spec/blob/master/auth.md)
* [Facebook's server-side authentication](https://developers.facebook.com/docs/authentication/server-side/)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
##Table of Contents

- [Installation and Usage](#installation-and-usage)
  - [1) Add the library to your project](#1-add-the-library-to-your-project)
  - [2) Obtain third party credentials](#2-obtain-third-party-credentials)
  - [3) Set up Friend and your main Ring handler stack.](#3-set-up-friend-and-your-main-ring-handler-stack)
  - [4) Provide your OAuth2 client-id and client-secret to friend-oauth2](#4-provide-your-oauth2-client-id-and-client-secret-to-friend-oauth2)
  - [5) Set up your credential-fn to find user metadata](#5-set-up-your-credential-fn-to-find-user-metadata)
  - [6) Configure the third party's OAuth2 URLs](#6-configure-the-third-partys-oauth2-urls)
  - [7) Add the workflow to your Friend handler](#7-add-the-workflow-to-your-friend-handler)
  - [8) Add restrictions to your routes and handlers](#8-add-restrictions-to-your-routes-and-handlers)
- [Configuration Reference](#configuration-reference)
- [Testing](#testing)
- [To-do:](#to-do)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Installation and Usage



### 1) Add the library to your project

In project.clj:

```clojure
[com.cemerick/friend "0.2.0"]
[friend-oauth2 "0.1.1"]
```
As a security best practice, it is
strongly recommended that you use
[Environ](https://github.com/weavejester/environ) or similar to store
your credentials in environment variables, rather than hardcode any credentials
into the source code itself. In this way, if an attacker steals your code,
they still won't be able to use your credentials.

To use Environ, also add:

```clojure
[environ "0.5.0"]
```
to your project.clj.


### 2) Obtain third party credentials

To be able to use friend-oauth2, you must obtain site credentials from
the third party providers you wish to authenticte against, then
provide those credentials to friend-oauth2. The exact procedure for
obtaining the credentials varies by provider; consult their docs for
details. You will be given a `:client-id` and a
`:client-secret`. (These are for your server code, not for your
clients - you are a client of the third party provdier.)

### 3) Set up Friend and your main Ring handler stack.

Besides friend-oauth2, you must also set up Friend itself. This is
typically done near where your main Ring app stack is being
constructed. friend-oauth2 is then given to Friend as a possible
workflow. It's easy to use other workflows besides friend-oauth2 in
the same app, too. See the [Friend docs][1] for more information on
setting up Friend itself.

Near where your Ring stack is being constructed, add the following requires:

```clojure
(ns your.ns.here
  (:require [cemerick.friend        :as friend]
            [friend-oauth2.workflow :as oauth2]
            [environ.core           :refer [env]])
```

### 4) Provide your OAuth2 client-id and client-secret to friend-oauth2


Using Environ and [Google APIs OAuth2](https://developers.google.com/accounts/docs/OAuth2) as an example:

```clojure
(def client-config
  {:client-id     (env :friend-oauth2-client-id)
   :client-secret (env :friend-oauth2-client-secret)
   :callback      {:domain "http://localhost:8090" ;; replace this for production with the appropriate site URL
                   :path "/oauth2callback"}})
```

In `~/.lein/profiles.clj`, add your actual credentials (N.B. this file
is *not* checked into source control! It should only be present on
your local machine, and should be protected from other users as it
contains sensitive passwords):

```clojure
{:user {:env {
    :friend-oauth2-client-id "12345.apps.googleusercontent.com"
    :friend-oauth2-client-secret "xyz"
}}
```

In production, these can be set at the ops level as ordinary
environment variables (with underscores instead of dashes):

```
export FRIEND_OAUTH2_CLIENT_ID=12345.apps.googleusercontent.com
export FRIEND_OAUTH2_CLIENT_secret=xyz
./bin/run-my-app.sh
```

### 5) Set up your credential-fn to find user metadata

If the user login succeeds on the third party site, you will get a
token back in your `credential-fn` uniquely identifying the user. You
can use this token to look up user metadata from the third party
service. What metadata you can look up is provider-specific. You can
also use it to create accounts in your own system, assign roles to the
user, and so on, as your particular app requires..

```clojure
(defn credential-fn
  "Upon successful authentication with the third party, Friend calls
  this function with the user's token. This function is responsible for
  translating that into a Friend identity map with at least the :identity
  and :roles keys. How you decide what roles to grant users is up to you;
  you could e.g. look them up in a database.

  You can also return nil here if you decide that the token provided
  is invalid. This could be used to implement e.g. banning users.

  This example code just automatically assigns anyone who has
  authenticated with the third party the nominal role of ::user."
  [token]
    {:identity token
     :roles #{::user}})
```

`credential-fn` behaves slightly differently differently in
friend-oauth2 than in other workflows: it allows you to intercept the
access token at the end of the 3rd-party authentication process and
inject your own functionality. This is an artifact of OAuth2's
original purpose as a protocol for obtaining 3rd-party
**authorization** of third party resources, not **authentication** of
user identity as such. It therefore does not return the user metadata
you probably find interesting; just a token. However, nowadays almost
everyone uses OAuth primarily for authentication rather than
authorization, so it is typical to find e.g. local database 
interaction in your own system, or third party metadata lookup here.


### 6) Configure the third party's OAuth2 URLs

Create the following data structure, given with Google as the
example. Consult the examples and the third party's documentation for
the URLs to use with other providers.

```clojure
(def uri-config
  {:authentication-uri {:url "https://accounts.google.com/o/oauth2/auth"
                        :query {:client_id (:client-id client-config)
                               :response_type "code"
                               :redirect_uri (format-config-uri client-config)
                               :scope "email"}}

   :access-token-uri {:url "https://accounts.google.com/o/oauth2/token"
                      :query {:client_id (:client-id client-config)
                              :client_secret (:client-secret client-config)
                              :grant_type "authorization_code"
                              :redirect_uri (format-config-uri client-config)}}})
```

### 7) Add the workflow to your Friend handler

Finally, create an OAuth2 workflow and add it to your Friend handler,
then add the Friend handler to your Ring stack:

```clojure
(def friend-config
  {:allow-anon? true
   :workflows   [(oauth2/workflow
                  {:client-config client-config
                   :uri-config uri-config
                   :credential-fn credential-fn})
                   ;; Optionally add other workflows here...
                   ]})

(def app
  (-> my-ring-app
      (friend/authenticate friend-config)
      handler/site))
```

### 8) Add restrictions to your routes and handlers

Access control is performed by Friend as usual. See [Friend's docs][1] for the full details.

For example, to allow access to a certain Compojure route only to users who are logged in and have the `::oauth2-user` role:

```clojure
(GET "/authlink" request
  (friend/authorize #{::oauth2-user} "Authorized page."))
```

Or, you can protect an entire Ring substack with Friend's `wrap-authorize` function:

```clojure
  (friend/wrap-authorize admin-routes #{::administrator})
```


Check out the [friend-oauth2 examples][2] and refer to the [Friend README][1] for more information.



## Configuration Reference

The map passed to `oauth2/workflow` accepts various optional arguments, described below. See also the [example handlers][2] for working examples.


* `client-config` holds the basic information which changes from app to app, regardless of the provider: `:client-id`, `:client-secret`, and the application's callback url.
* The `authentication-uri` map holds the provider-specific configuration for the initial redirect to the OAuth2 provider (the user-facing GET request).
* The `access-token-uri` map holds the provider-specific configuration for the access_token request, after the code is returned from the previous redirect (a server-to-server POST request).
* `access-token-parsefn` is a provider-specific function which parses the access_token response and returns just the access_token (see below.)

If your OAuth2 provider does not follow the RFC
(http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-5.1, ("in
the entity body of the HTTP response using the "application/json"
media type as defined by [RFC4627]") then you can pass in a custom
function to parse the access_token response.  Note that there is an
alternate function (`get-access-token-from-params`) supplied to handle
the common case where an access_token is provided as parameters in the
callback request. Simply set the `:access-token-parsefn
get-access-token-from-params` See the
[Facebook and Github examples][2] for reference.


## Testing

friend-oauth2 uses Midje (https://github.com/marick/Midje) for testing.  You can run all the tests by starting up a repl, running `use 'midje.repl` and running `autotest`, or run `lein midje :autotest` on the command line.

## To-do:

* Move client_id/client_secret to Authorization header (necessary? Good for security or immaterial? Does FB support this?) (http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-2.3)

## License

Distributed under the MIT License (http://dd.mit-license.org/)

[1]: https://github.com/cemerick/friend
[2]: https://github.com/ddellacosta/friend-oauth2-examples
