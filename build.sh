#!/bin/sh
sudo docker run -it --rm -v $(pwd):/usr/src/mvn -v $(dirname $(pwd))/build-cache:/root/.m2 maven-project-build
