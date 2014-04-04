Wrangler
========

Wrangle different dev tools together and look good doing it.

[![Build Status](https://magnum.travis-ci.com/CommBank/wrangler.svg?token=A3xq7fpHLyey1yCrNASy&branch=master)](https://magnum.travis-ci.com/CommBank/wrangler)
[![Gitter chat](https://badges.gitter.im/CommBank/wrangler.png)](https://gitter.im/CommBank/wrangler)

A collection of scripts around automating the development process.

* `create-project` sets up a new project. It creates a repository in STASH, forks it, creates a TeamCity build, uses omnia.g8 to deploy the template and makes an initial commit. 
* `clone-project` forks an existing project, sets up fork-syncing, does a git clone and sets up remote alias for upstream.
* `list-repos` lists the repos in stash.
* `latest-versions` list the latest version of standard jar artefacts in Artifactory
* `automator` given a script that makes some changes and commits them, applies the script to the given repos and creates pull requests for the changes.

Modify bin/config.sh to set the right environment variables.
