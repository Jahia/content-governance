#!/bin/bash
# The CI copies the build job's jar into artifacts/ before this script runs; a local run takes the
# jar `mvn clean install` left in ../target. @jahia/cypress then builds the tests image.
mkdir -p ./artifacts
for file in ../target/*-SNAPSHOT.jar; do
  if [[ -e "$file" && "$file" != *-sources.jar ]]; then
    cp "$file" ./artifacts/
  fi
done

version=$(node -p "require('./package.json').devDependencies['@jahia/cypress']")
echo Using @jahia/cypress@$version...
npx --yes --package @jahia/cypress@$version ci.build
