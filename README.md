Wrangler
========

Wrangle different dev tools together and look good doing it.

[![Build Status](https://travis-ci.org/CommBank/wrangler.svg?branch=master)](https://travis-ci.org/CommBank/wrangler)
[![Gitter chat](https://badges.gitter.im/CommBank/wrangler.png)](https://gitter.im/CommBank/wrangler)

A collection of scripts around automating the development process.

* `create-gh-project` or `create-stash-project` sets up a new project. It creates a repository in
   STASH, forks it, creates a TeamCity build, uses omnia.g8 to deploy the template and makes an
   initial commit. It does the same for Github but use commbank.g8 as template and sets up Travis.
   Note that for stash repos the repo name is expected to be of the format `group.name`.
* `clone-project` For stash forks an existing project, sets up fork-syncing, does a git clone and
   sets up remote alias for upstream.
* `clone-existing` Forks an existing stash project, sets up fork syncing and clones it.
* `list-gh-repos` or `list-stash-repos` lists the repos in github or stash.
* `latest-versions` list the latest version of standard jar artefacts in Artifactory
* `automator` given a script that makes some changes and commits them, applies the script to the
   given repos and creates pull requests for the changes.
* `updater` given a configuration file (see wrangler-ops for examples) updates the dependencies in
  the specified repos to the specified versions. It is also possible to specify `latests` instead
  of a version number for dependencies with semantic versioning or omnnia style versioning and
  wrangler will get the latest version number to use from artifactory.
* `gh-merge` For the specified github repo merges the specified branch into master doing a fast
  forward merge and then deletes the branch.
      
Modify bin/config.sh to set the path to the jar and wrangler.conf. Wrangler.conf has all the
configuration. Alternatively that configuration can be passed on the command line.

Configuration
-------------

Wrangler is configured either on the command line, filling in wrangler.conf (see example.conf) or a
combination of the two.

Wrangler uses [summac](https://github.com/quantifind/Sumac) for command line parsing and
[config](https://github.com/typesafehub/config) for parsing config files.

###Artifactory

Wrangler can query up to three different artifactory repos. They need to be specified in the
configuration file as artifactor, artifactory2, and artifactory3.

###Example

```
wrangler {
travisGitPassword = "password"
travisArtifactoryPassword = "encrypted artifactory password"

github {
  user     = user
  org      = org
  teamid   = 1
  apiUrl   = "https://api.github.com"
  gitUrl   = "https://github.com"
  password = password
}

stash {
  user      = hoermast
  project   = omnia
  apiUrl    = "https://stash.dev/rest"
  gitUrl    = "ssh://git@stash.dev:7999"
  reviewers = [ hoermast ]
  //password = // Leave blank to be promoted for password instead
}

artifactory {
  url      = "https://org.artifactoryonline.com/org"
  repos    = "repo1, repo2"
  user     = user
  password = password
}

artifactory2 {
  url      = "http://artifactory/artifactory"
  repos    = "repo1, repo2"
  user     = user
  password = password
}

teamcity {
  url     = "http://teamcity.dev/httpAuth/app/rest"
  project = project
  user    = "domain\\user"
}
```


Proxy support
-------------

If the environment variable `http_proxy` or `HTTP_PROXY` is set Wrangler will use that proxy for
REST requests.

Git operations currently don't seem to work behind the proxy.


Git authentication
-------------------

When executing git commands Wrangler will authenticate:

* using ssh keys for ssh urls
* credentials in ~/.netrc for https urls.

Examples
--------

###Updater

##### Config

```
targets = [wrangler, maestro]
artifacts = [
  au.com.cba.omnia % edge   % 2.1.0-20140604032756-0c0abb1,
  au.com.cba.omnia % tardis % latest,
  org.apache.sqoop % sqoop  % 1.4.3-cdh4.6.0
]
```

The command  to update Github repos then is `updater --useGithub true --updaterConfig updater.config`

###Automator

Unfortunately running automator using the bash command has some issues when it comes to strings with
spaces. Instead is has to be run directly, e.g. `java  -cp wrangler-assembly-0.9.0.jar:bin wrangler.commands.Automator --repos uniform,piped --branch travis_fix --title "Travis fix" --description "Travis fix" --useGithub true --script /path/travis_fix.sh`.



